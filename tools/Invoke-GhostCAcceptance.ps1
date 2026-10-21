#requires -Version 5.1
<#
.SYNOPSIS
  GhostC versioned Windows acceptance: checksum-gated Prepare, Collect, Rollback.
.DESCRIPTION
  Modifies only ghostc-forge189*.jar files inside the supplied Minecraft profile.
  Never launches Minecraft, changes Java, reads private logs or touches unrelated mods.
  Preparation backs up every recognized GhostC JAR before installing a verified one.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Prepare','Collect','Rollback')]
    [string]$Mode,

    [Parameter(Mandatory=$true)]
    [string]$GameDir,

    [string]$JarPath,
    [string]$ExpectedSha256,

    # Internal, CI-fixture-only handled-failure test hook. Not an owner option.
    [ValidateRange(-1,10)]
    [int]$CiFailRollbackAfterRestoreCount = -1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-OriginalJarName([string]$Name) {
    if ($Name -notmatch '^ghostc-forge189(?:-[0-9]+\.[0-9]+\.[0-9]+)?\.jar$') {
        throw "Unsafe GhostC jar name: $Name"
    }
}
function Assert-NoLink([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Refusing symlink or junction: $Path"
    }
}
function Get-Sha([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing file: $Path"
    }
    Assert-NoLink $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}
function Write-AtomicState([string]$Path, $Data) {
    $temp = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
    try {
        $Data | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $temp -Encoding UTF8
        Move-Item -LiteralPath $temp -Destination $Path -ErrorAction Stop
    }
    finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force }
    }
}

$game = [IO.Path]::GetFullPath($GameDir)
if (!(Test-Path -LiteralPath $game -PathType Container)) {
    throw "The Minecraft profile directory does not exist."
}
Assert-NoLink $game
if ($CiFailRollbackAfterRestoreCount -ge 0) {
    if ($Mode -ne 'Rollback' -or $env:GITHUB_ACTIONS -ne 'true' -or
        [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        throw 'Rollback fault injection is restricted to GitHub Actions fixtures.'
    }
    $runnerRoot = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd('\','/')
    $runnerPrefix = $runnerRoot + [IO.Path]::DirectorySeparatorChar
    if (!$game.StartsWith($runnerPrefix,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Rollback fault injection requires a temporary CI fixture profile.'
    }
}
$mods = Join-Path $game 'mods'
$versionFolder = Join-Path $game 'versions'
if (!(Test-Path -LiteralPath $mods -PathType Container) -and
    !(Test-Path -LiteralPath $versionFolder -PathType Container) -and
    !(Test-Path -LiteralPath (Join-Path $game 'launcher_profiles.json') -PathType Leaf)) {
    throw "Not a recognizable Minecraft profile. Supply its actual game directory."
}
if (Test-Path -LiteralPath $mods) { Assert-NoLink $mods }
$accept = Join-Path $game 'ghostc-acceptance'
if (Test-Path -LiteralPath $accept) { Assert-NoLink $accept }
$backups = Join-Path $accept 'backups'
# Validate the ENTIRE owned backup path, not only a transaction child.
# A junction at backups/ can redirect Move-Item outside the game profile
# while every individual ghostc-forge189 JAR passes its file hash check.
if (Test-Path -LiteralPath $backups) {
    Assert-NoLink $backups
    if (!(Test-Path -LiteralPath $backups -PathType Container)) {
        throw 'Acceptance backup path is not a directory.'
    }
}
$stateFile = Join-Path $accept 'install-state.json'

if ($Mode -eq 'Prepare') {
    if ([string]::IsNullOrWhiteSpace($JarPath) -or
        $ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$') {
        throw "Prepare requires -JarPath and its exact 64-digit -ExpectedSha256."
    }
    $source = [IO.Path]::GetFullPath($JarPath)
    $name = [IO.Path]::GetFileName($source)
    if ($name -notmatch '^ghostc-forge189-[0-9]+\.[0-9]+\.[0-9]+\.jar$') {
        throw 'The source must be a versioned GhostC Forge JAR.'
    }
    $sha = Get-Sha $source
    if ($sha -ne $ExpectedSha256.ToUpperInvariant()) {
        throw 'SHA256 mismatch. Nothing was installed.'
    }
    if (Test-Path -LiteralPath $stateFile) {
        throw 'A prior GhostC acceptance transaction is active. Collect or Rollback first.'
    }
    if (!(Test-Path -LiteralPath $mods)) {
        New-Item -ItemType Directory -Path $mods | Out-Null
    }
    $sourceDir = [IO.Path]::GetDirectoryName($source)
    if ([string]::Equals($sourceDir.TrimEnd('\'),$mods.TrimEnd('\'),
                         [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Source JAR must be outside the live mods directory.'
    }
    $unrecognized = @(Get-ChildItem -LiteralPath $mods -File |
        Where-Object {
            $_.Name -match '(?i)^ghostc.*\.jar$' -and
            $_.Name -notmatch '^ghostc-forge189(?:-[0-9]+\.[0-9]+\.[0-9]+)?\.jar$'
        })
    if ($unrecognized.Count -gt 0) {
        throw 'Another GhostC-named JAR is present; remove duplicates manually before preparing.'
    }
    $old = @(Get-ChildItem -LiteralPath $mods -File |
        Where-Object { $_.Name -match '^ghostc-forge189(?:-[0-9]+\.[0-9]+\.[0-9]+)?\.jar$' })
    $previous = @()
    foreach ($item in $old) {
        Assert-OriginalJarName $item.Name
        $previous += [pscustomobject]@{ name=$item.Name; sha256=(Get-Sha $item.FullName) }
    }
    # Record the baseline modification time without deleting earlier diagnostics.
    $diagPath = Join-Path $game 'config/ghostc/diagnostics-last.properties'
    $diagBaselineModifiedUtcTicks = [long]0
    if (Test-Path -LiteralPath $diagPath) {
        if (!(Test-Path -LiteralPath $diagPath -PathType Leaf)) {
            throw 'Diagnostics path is not a regular file.'
        }
        Assert-NoLink $diagPath
        $diagBaselineModifiedUtcTicks = (Get-Item -LiteralPath $diagPath).LastWriteTimeUtc.Ticks
    }
    $preparedUtcTicks = [DateTime]::UtcNow.Ticks
    # Do not create any backup directories until source, duplicate-name and
    # all existing-JAR hash preflight checks have succeeded.
    New-Item -ItemType Directory -Path $backups -Force | Out-Null
    Assert-NoLink $backups
    $transaction = [guid]::NewGuid().ToString('N')
    $backupDir = Join-Path $backups $transaction
    if (Test-Path -LiteralPath $backupDir) {
        throw 'Acceptance transaction backup directory unexpectedly exists.'
    }
    New-Item -ItemType Directory -Path $backupDir -ErrorAction Stop | Out-Null
    Assert-NoLink $backupDir
    $destination = Join-Path $mods $name
    $installed = $false
    try {
        foreach ($entry in $previous) {
            Move-Item -LiteralPath (Join-Path $mods $entry.name) -Destination $backupDir
        }
        $installed = $true
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-Sha $destination) -ne $sha) { throw 'Installed JAR verification failed.' }
        Write-AtomicState $stateFile ([pscustomobject]@{
            schema=2
            transaction=$transaction
            preparedUtcTicks=$preparedUtcTicks
            diagnosticsBaselineModifiedUtcTicks=$diagBaselineModifiedUtcTicks
            installed=[pscustomobject]@{ name=$name; sha256=$sha }
            previous=@($previous)
        })
    }
    catch {
        if ($installed -and (Test-Path -LiteralPath $destination)) {
            Remove-Item -LiteralPath $destination -Force
        }
        foreach ($entry in $previous) {
            $saved = Join-Path $backupDir $entry.name
            if (Test-Path -LiteralPath $saved) {
                Move-Item -LiteralPath $saved -Destination (Join-Path $mods $entry.name)
            }
        }
        throw
    }
    Write-Output "GHOSTC_PREPARE_PASS file=$name sha256=$sha previous=$($previous.Count)"
    Write-Output 'A real Minecraft launch is NOT verified. Start your licensed Forge 1.8.9 profile normally.'
    exit 0
}

if (!(Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    throw 'No active GhostC acceptance transaction. Nothing has been changed.'
}
Assert-NoLink $stateFile
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if (($state.schema -ne 1 -and $state.schema -ne 2) -or
    $state.transaction -notmatch '^[0-9a-f]{32}$' -or
    $state.installed.sha256 -notmatch '^[0-9A-F]{64}$') {
    throw 'Invalid acceptance manifest; refusing to change files.'
}
# Legacy schema-1 installs remain recoverable, but their diagnostics
# cannot be attributed to a Prepare time, so Collect withholds those counters.
if ($state.schema -eq 2) {
    if ("$($state.preparedUtcTicks)" -notmatch '^[0-9]{15,19}$' -or
        "$($state.diagnosticsBaselineModifiedUtcTicks)" -notmatch '^[0-9]{1,19}$') {
        throw 'Invalid diagnostics timing in acceptance state.'
    }
}
Assert-OriginalJarName $state.installed.name
$installedJar = Join-Path $mods $state.installed.name
$backupDir = Join-Path $backups $state.transaction
# Checking only this child would miss a redirect at backups/ itself.
if (!(Test-Path -LiteralPath $backups -PathType Container)) {
    throw 'Acceptance backup root missing; refusing Collect or Rollback.'
}
Assert-NoLink $backups
if (!(Test-Path -LiteralPath $backupDir -PathType Container)) {
    throw 'Backup directory missing; refusing any rollback.'
}
Assert-NoLink $backupDir
$prior = @($state.previous | Where-Object { $null -ne $_ })
foreach ($entry in $prior) {
    Assert-OriginalJarName $entry.name
    if ($entry.sha256 -notmatch '^[0-9A-F]{64}$' -or
        (Get-Sha (Join-Path $backupDir $entry.name)) -ne $entry.sha256) {
        throw "Backup verification failed: $($entry.name)"
    }
}
$installedHash = Get-Sha $installedJar
if ($installedHash -ne $state.installed.sha256) {
    throw 'Installed GhostC JAR changed since Prepare; refusing to overwrite or remove it.'
}

if ($Mode -eq 'Rollback') {
    foreach ($entry in $prior) {
        $original = Join-Path $mods $entry.name
        if ((Test-Path -LiteralPath $original) -and
            $original -ne $installedJar) {
            throw "Rollback would overwrite a changed file: $($entry.name)"
        }
    }

    # Stage and verify all restore copies before touching the current client.
    # Original backups stay intact, allowing retry after a handled failure.
    $stage = Join-Path $backupDir 'rollback-stage'
    if (Test-Path -LiteralPath $stage) {
        throw 'A prior rollback stage exists. Refusing to overwrite recovery files.'
    }
    $stageCopies = Join-Path $stage 'copies'
    $heldPath = Join-Path $stage 'active-client.jar'
    $heldInstalled = $false
    $restored = @()
    try {
        New-Item -ItemType Directory -Path $stageCopies -Force -ErrorAction Stop | Out-Null
        Assert-NoLink $stage
        Assert-NoLink $stageCopies
        foreach ($entry in $prior) {
            $copy = Join-Path $stageCopies $entry.name
            Copy-Item -LiteralPath (Join-Path $backupDir $entry.name) -Destination $copy -ErrorAction Stop
            if ((Get-Sha $copy) -ne $entry.sha256) {
                throw "Rollback staged-copy hash mismatch: $($entry.name)"
            }
        }
        Move-Item -LiteralPath $installedJar -Destination $heldPath -ErrorAction Stop
        $heldInstalled = $true
        if ((Get-Sha $heldPath) -ne $state.installed.sha256) {
            throw 'Quarantined active-client SHA256 mismatch.'
        }
        foreach ($entry in $prior) {
            $copy = Join-Path $stageCopies $entry.name
            $destination = Join-Path $mods $entry.name
            Move-Item -LiteralPath $copy -Destination $destination -ErrorAction Stop
            $restored += $entry
            if ((Get-Sha $destination) -ne $entry.sha256) {
                throw "Restoration checksum mismatch: $($entry.name)"
            }
            if ($CiFailRollbackAfterRestoreCount -eq $restored.Count) {
                throw 'CI_INJECTED_ROLLBACK_RESTORE_FAILURE'
            }
        }
        # Commit only after every original is restored and hash-verified.
        Remove-Item -LiteralPath $stateFile -Force -ErrorAction Stop
    }
    catch {
        $cause = $_.Exception.Message
        $recoveryErrors = @()
        for ($i = $restored.Count - 1; $i -ge 0; $i--) {
            $entry = $restored[$i]
            $destination = Join-Path $mods $entry.name
            try {
                if ((Get-Sha $destination) -ne $entry.sha256) {
                    throw 'A restored JAR changed while rollback was running.'
                }
                Remove-Item -LiteralPath $destination -Force -ErrorAction Stop
            }
            catch {
                $recoveryErrors += "Cannot reclaim restored $($entry.name): $($_.Exception.Message)"
            }
        }
        if ($heldInstalled) {
            try {
                if (Test-Path -LiteralPath $installedJar) {
                    throw 'Active client destination was unexpectedly occupied.'
                }
                if ((Get-Sha $heldPath) -ne $state.installed.sha256) {
                    throw 'Held active client changed during recovery.'
                }
                Move-Item -LiteralPath $heldPath -Destination $installedJar -ErrorAction Stop
                if ((Get-Sha $installedJar) -ne $state.installed.sha256) {
                    throw 'Recovered active client SHA256 mismatch.'
                }
            }
            catch {
                $recoveryErrors += "Cannot restore active client: $($_.Exception.Message)"
            }
        }
        if ($recoveryErrors.Count -gt 0) {
            throw "ROLLBACK_RECOVERY_INCOMPLETE: $cause. Recovery files preserved at $stage. $($recoveryErrors -join '; ')"
        }
        try { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction Stop }
        catch { Write-Warning 'Rollback stage cleanup failed; review before retrying.' }
        throw "GHOSTC_ROLLBACK_REVERTED: $cause. Active client and transaction retained."
    }
    # After commit, cleanup is best-effort; original backup copies are retained.
    try { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction Stop }
    catch { Write-Warning 'Rollback succeeded but a backup stage requires manual review.' }
    Write-Output "GHOSTC_ROLLBACK_PASS restored=$($prior.Count)"
    exit 0
}

# Collect keeps owner-confirmed checkboxes/notes across repeated runs.
# Only safe numeric telemetry is refreshed; no raw game logs are copied.
$report = Join-Path $accept 'acceptance-report.md'
$telemetryFile = Join-Path $accept 'telemetry-latest.txt'
if (Test-Path -LiteralPath $report) { Assert-NoLink $report }
if (Test-Path -LiteralPath $telemetryFile) { Assert-NoLink $telemetryFile }
$lines = @(
    '# GhostC Windows acceptance report',
    '',
    '**Status: UNVERIFIED IN-GAME until the checklist is completed by a human.**',
    '',
    "Installed artifact: $($state.installed.name)",
    "Verified SHA256: $installedHash",
    "Session transaction: $($state.transaction)",
    "Existing GhostC JARs safely backed up: $($prior.Count)",
    '',
    '## Live Minecraft 1.8.9 + Forge acceptance (complete manually)',
    '',
    '- [ ] Launcher starts without a Forge loading error',
    '- [ ] GhostC appears in the Forge Mods list, with the correct version',
    '- [ ] F7 ClickGUI opens/closes, and module search/settings work',
    '- [ ] F8 status HUD toggles and remapped Keystrokes labels are correct',
    '- [ ] Long/remapped Unicode keystroke labels stay inside cells with a visible ellipsis',
    '- [ ] Small scaled-screen Keystrokes/Target HUD cards are hidden when too large and reappear on resize',
    '- [ ] CPS panel counts stay inside their card at high counts and on small scaled windows',
    '- [ ] Rendering and Target HUD behave correctly in an authorized local world',
    '- [ ] Runtime Diagnostics fits smaller scaled windows, clips long counters and retains FPS/snapshot summary',
    '- [ ] Target HUD long names truncate without overlapping right-aligned HP text',
    '- [ ] Target HUD formatting codes and supplementary Unicode render safely without stalls',
    '- [ ] Target HUD health_text values/percent options display and persist',
    '- [ ] Hidden HP restores full available name width with same widget footprint',
    '- [ ] Very high/custom HP does not overflow text bounds or corrupt HP fill',
    '- [ ] All five ESP modes (box, tracer, both, skeleton, box+skeleton) checked visually',
    '- [ ] Optional ESP show_health toggles/persists and fill reflects low/full health',
    '- [ ] ESP health bar camera orientation, distance, depth and wall policy verified',
    '- [ ] ESP health_side Left/Right moves the health bar across the player as camera rotates',
    '- [ ] ESP health_gap 0.05-0.50 changes spacing and persists through restart',
    '- [ ] Default Right/0.18 side remains visually identical to R2.25 health bars',
    '- [ ] ESP RGB/opacity sliders visibly update box/tracer/skeleton and survive profile reload',
    '- [ ] ESP corner_box renders distinct short 3D corner lines, not a full 12-edge box',
    '- [ ] ESP corner_skeleton retains distinct schematic skeleton lines inside the 3D corners',
    '- [ ] ESP corner_length 10%-50% slider visibly scales corners and persists after restart',
    '- [ ] ESP filled_box shows translucent six-face volume plus RGB wire outline',
    '- [ ] ESP filled_skeleton includes full wire outline, volume and schematic skeleton',
    '- [ ] ESP fill_opacity 4%-50% changes volume only and persists; old box colors unaffected',
    '- [ ] Depth-tested and through-walls filled volumes do not break GL or other rendering',
    '- [ ] Filled Box overlapping faces look consistent when moving around or inside the box',
    '- [ ] Overlapping translucent players blend in distant-to-nearest order without hiding near targets',
    '- [ ] Non-filled Box, Tracer, Corner and Skeleton styles remain visually unchanged',
    '- [ ] With distance_fade disabled, all nine ESP styles retain their old RGB/alpha',
    '- [ ] With fading enabled, near players stay opaque and far players fade smoothly',
    '- [ ] fade_start and fade_min_opacity sliders change only wire/face opacity and persist',
    '- [ ] Health bars keep their independent HP gradient when distance fade is enabled',
    '- [ ] Default ESP purple and independent red-green health gauge look unchanged',
    '- [ ] Crowded 80+ player world prioritizes nearby ESP players regardless of entity-list order',
    '- [ ] With 80 nearest valid players already selected, farther in-range players avoid redundant visible-only LOS checks',
    '- [ ] Equal-distance entity IDs and closer duplicate IDs preserve the original ESP selection',
    '- [ ] ESP max_players, fading, filled painter order and health side/gap behave unchanged with admission pruning',
    '- [ ] ESP max_players slider from 1 through 80 reduces rendered overlays to nearest N',
    '- [ ] Filled ESP at max_players below 80 still draws only nearest N, far-to-near',
    '- [ ] Visible-only ESP skips LOS rays for out-of-range players; in-range appearance remains unchanged',
    '- [ ] ESP max_players persists through restart, with 80 as legacy default',
    '- [ ] Crowded 256+ player world retains nearby Target HUD and Aim Assist candidates',
    '- [ ] Crowded occluded players cannot crowd out visible-only HUD/aim targets',
    '- [ ] With visible_only disabled, nearest occluded targets remain selectable',
    '- [ ] Disabled Target HUD and Aim Assist do not scan world players or issue LOS checks',
    '- [ ] Unfiltered-only target mode avoids LOS rays while preserving nearest targets',
    '- [ ] Outside target range, loaded entities do not incur LOS checks; mixed mode stays correct',
    '- [ ] Mixed HUD24/aim6 visibility preferences ray-check only visible-only feature radius',
    '- [ ] Nearest Target HUD and Aim Assist selections match expected sorted target choices',
    '- [ ] ESP and health bars remain correct after many world/dimension switches',
    '- [ ] ESP occlusion/through-walls, distance filters and scene switching checked',
    '- [ ] Diagnostics enabled: ESP frame/player/skeleton counters rise as expected',
    '- [ ] Invalid geometry counter stays low, with no auto-disabled ESP module',
    '- [ ] Focus/GUI/reconnect do not leave synthetic attack/use held',
    '- [ ] Profile changes survive restart, and Rollback can restore the old JARs',
    '- [ ] FPS min/p50/p95/p99 and JVM heap peak observed during focused gameplay',
    '- [ ] GUI/focus/world switching does not distort frame-time samples',
    '- [ ] A representative 60-minute soak checked for crashes, memory/FPS and faults',
    '- [ ] Gameplay responsiveness and a representative soak session checked',
    '- [ ] Runtime Diagnostics: snapshot p95/p99/max us and LOS count collected during crowded gameplay',
    '- [ ] Profile with HUD/aim off, visible-only and mixed preferences and compare snapshot timing',
    '- [ ] Compare per-mode snap.mode_off_*, snap.mode_visible_only_*, snap.mode_unfiltered_only_* and snap.mode_mixed_* samples',
    '- [ ] Verify diagnostics profiler itself has no noticeable render stutter with HUD enabled and switching modes',
    '- [ ] Confirm menu/chat/alt-tab periods do not enter focused snapshot sample statistics',
    '',
    '## Local diagnostics (counts only; no raw latest.log is copied)',
    '',
    'Initial snapshot only. The freshest safe counters are in telemetry-latest.txt.',
    'Collect preserves this checklist and all owner notes on subsequent runs.',
    ''
)
$diagPath = Join-Path $game 'config/ghostc/diagnostics-last.properties'
$safe = @()
$diagStatus = '(No fresh numeric diagnostics for this installation; no gameplay inference.)'
if (Test-Path -LiteralPath $diagPath -PathType Leaf) {
    Assert-NoLink $diagPath
    $modifiedTicks = (Get-Item -LiteralPath $diagPath).LastWriteTimeUtc.Ticks
    if ($state.schema -eq 2 -and
        $modifiedTicks -gt [long]$state.preparedUtcTicks -and
        $modifiedTicks -gt [long]$state.diagnosticsBaselineModifiedUtcTicks) {
        $safe = @(Get-Content -LiteralPath $diagPath |
            Where-Object { $_ -match '^(?:count\.[a-z0-9_]+|perf\.[a-z0-9_]+|snap\.[a-z0-9_]+)=[0-9]+\z' } |
            Sort-Object)
        if ($safe.Count -gt 0) {
            $diagStatus = '(Diagnostics file modified after Prepare; this does not verify a game launch.)'
        } else {
            $diagStatus = '(No allowlisted numeric counters in freshly modified diagnostics.)'
        }
    } else {
        $diagStatus = '(Withheld: diagnostics predates Prepare or no supported freshness baseline.)'
    }
} elseif (Test-Path -LiteralPath $diagPath) {
    throw 'Diagnostics path is not a regular file.'
}
$lines += $diagStatus
if ($safe.Count -gt 0) { $lines += $safe }
$lines += @(
    '',
    'ESP counters count render submissions, NOT confirmed visible pixels or FPS.',
    'Rounded FPS is a frame-interval estimate, NOT actual GPU time.',
    'JVM heap measurements exclude system/GPU/process memory.',
    'Raw latest.log, screenshots and crash reports are intentionally NOT included.',
    'Review/redact account names, addresses, chat and private details before sharing.',
    'This report and the build hash are evidence of installation only, not successful in-game play.'
)
New-Item -ItemType Directory -Path $accept -Force | Out-Null
# The visible report belongs to ONE Prepare transaction, not merely a JAR
# hash. After Rollback another Prepare may use even the identical JAR; never
# reuse an earlier human-completed checklist as evidence of that new session.
$reportExists = Test-Path -LiteralPath $report -PathType Leaf
$telemetryExists = Test-Path -LiteralPath $telemetryFile -PathType Leaf
$sameSession = $false
if ($reportExists) {
    $priorReport = Get-Content -LiteralPath $report -Raw
    $sameSession = (
        $priorReport -match "(?m)^Session transaction: $($state.transaction)\r?$" -and
        $priorReport -match "(?m)^Installed artifact: $([regex]::Escape($state.installed.name))\r?$" -and
        $priorReport -match "(?m)^Verified SHA256: $installedHash\r?$"
    )
}
if (!$sameSession -and ($reportExists -or $telemetryExists)) {
    $history = Join-Path $accept 'history'
    if (Test-Path -LiteralPath $history) { Assert-NoLink $history }
    $archive = Join-Path $history ([guid]::NewGuid().ToString('N'))
    if (Test-Path -LiteralPath $archive) {
        throw 'Acceptance archive collision. Refusing to overwrite historical evidence.'
    }
    New-Item -ItemType Directory -Path $archive -Force | Out-Null
    if ($reportExists) {
        Move-Item -LiteralPath $report -Destination (Join-Path $archive 'acceptance-report.md') -ErrorAction Stop
    }
    if ($telemetryExists) {
        Move-Item -LiteralPath $telemetryFile -Destination (Join-Path $archive 'telemetry-latest.txt') -ErrorAction Stop
    }
    Write-Output "GHOSTC_COLLECT_PREVIOUS_SESSION_ARCHIVED path=$archive"
}
# A deliberately separate sidecar avoids erasing checked boxes/owner notes
# from this transaction. Only allowlisted numeric fields are refreshed.
$telemetryLines = @(
    '# GhostC numeric diagnostics snapshot (not proof of in-game success)',
    "Installed SHA256: $installedHash",
    "Session transaction: $($state.transaction)",
    'Only allowlisted count.*, perf.*, and snap.* nonnegative integers follow.'
)
$telemetryLines += $diagStatus
if ($safe.Count -gt 0) {
    $telemetryLines += $safe
}
$telemetryLines | Set-Content -LiteralPath $telemetryFile -Encoding UTF8
if (!(Test-Path -LiteralPath $report)) {
    $lines | Set-Content -LiteralPath $report -Encoding UTF8
} else {
    Write-Output 'GHOSTC_COLLECT_REPORT_PRESERVED owner checklist/notes not overwritten'
}
Write-Output "GHOSTC_COLLECT_PASS sha256=$installedHash"
Write-Output "Local report: $report"
Write-Output "Latest numeric telemetry: $telemetryFile"
