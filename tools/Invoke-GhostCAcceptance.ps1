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
    [string]$ExpectedSha256
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
    New-Item -ItemType Directory -Path $backups -Force | Out-Null
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
    $transaction = [guid]::NewGuid().ToString('N')
    $backupDir = Join-Path $backups $transaction
    New-Item -ItemType Directory -Path $backupDir | Out-Null
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
            schema=1
            transaction=$transaction
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
if ($state.schema -ne 1 -or $state.transaction -notmatch '^[0-9a-f]{32}$' -or
    $state.installed.sha256 -notmatch '^[0-9A-F]{64}$') {
    throw 'Invalid acceptance manifest; refusing to change files.'
}
Assert-OriginalJarName $state.installed.name
$installedJar = Join-Path $mods $state.installed.name
$backupDir = Join-Path $backups $state.transaction
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
    Remove-Item -LiteralPath $installedJar -Force
    foreach ($entry in $prior) {
        Move-Item -LiteralPath (Join-Path $backupDir $entry.name) -Destination $mods
        if ((Get-Sha (Join-Path $mods $entry.name)) -ne $entry.sha256) {
            throw "Restoration checksum mismatch: $($entry.name)"
        }
    }
    Remove-Item -LiteralPath $stateFile -Force
    Write-Output "GHOSTC_ROLLBACK_PASS restored=$($prior.Count)"
    exit 0
}

# Collect only an evidence checklist and safe counters. No raw game logs are copied.
$report = Join-Path $accept 'acceptance-report.md'
$lines = @(
    '# GhostC Windows acceptance report',
    '',
    '**Status: UNVERIFIED IN-GAME until the checklist is completed by a human.**',
    '',
    "Installed artifact: $($state.installed.name)",
    "Verified SHA256: $installedHash",
    "Existing GhostC JARs safely backed up: $($prior.Count)",
    '',
    '## Live Minecraft 1.8.9 + Forge acceptance (complete manually)',
    '',
    '- [ ] Launcher starts without a Forge loading error',
    '- [ ] GhostC appears in the Forge Mods list, with the correct version',
    '- [ ] F7 ClickGUI opens/closes, and module search/settings work',
    '- [ ] F8 status HUD toggles and remapped Keystrokes labels are correct',
    '- [ ] Rendering and Target HUD behave correctly in an authorized local world',
    '- [ ] All five ESP modes (box, tracer, both, skeleton, box+skeleton) checked visually',
    '- [ ] Optional ESP show_health toggles/persists and fill reflects low/full health',
    '- [ ] ESP health bar camera orientation, distance, depth and wall policy verified',
    '- [ ] Crowded 80+ player world prioritizes nearby ESP players regardless of entity-list order',
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
    '',
    '## Local diagnostics (counts only; no raw latest.log is copied)',
    ''
)
$diagPath = Join-Path $game 'config/ghostc/diagnostics-last.properties'
if (Test-Path -LiteralPath $diagPath -PathType Leaf) {
    $safe = @(Get-Content -LiteralPath $diagPath |
        Where-Object { $_ -match '^(?:count\.[a-z_]+|perf\.[a-z_]+)=[0-9]+$' } |
        Sort-Object)
    if ($safe.Count -gt 0) { $lines += $safe } else { $lines += '(No safe counter fields found)' }
} else {
    $lines += '(Enable the optional diagnostics module and disconnect to export counters.)'
}
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
$lines | Set-Content -LiteralPath $report -Encoding UTF8
Write-Output "GHOSTC_COLLECT_PASS sha256=$installedHash"
Write-Output "Local report: $report"
