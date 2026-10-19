# GhostC R1.0 — Windows release acceptance transaction

**R1.0 is a build/installation acceptance milestone, NOT a claim of a verified Minecraft launch.** Source, build, CI scripts, development artifact and issue evidence live entirely in [Trexzo/ghostc](https://github.com/Trexzo/ghostc).

## What GitHub verifies

- Java 17 and 21 headless regression suites target Java 8 bytecode.
- Forge 1.8.9 JDK 8 reobfuscated JAR builds on **Linux and Windows**.
- `tools/verify_forge_jar.py` verifies ZIP CRC, duplicate/path traversal entries, required classes, GhostC mod ID, version alignment and **every classfile** at Java 8 major-version 52. Its own fixture tests reject malformed versions, duplicate entries, path traversal and new-version Java bytecode.
- The Windows CI job invokes the actual **Windows PowerShell 5.1** acceptance script on a disposable fake profile: **Prepare → Collect → Rollback**. The old sample GhostC jar must be restored byte-for-byte and the active transaction state must be removed.
- GitHub Actions publishes a versioned `ghostc-forge189-1.0.0.jar`, `SHA256SUMS.txt`, the PowerShell script and this guide. The artifact remains explicitly labelled **unverified-ingame**.

None of the above installs a full Minecraft instance, authenticates with a game account, loads a world or verifies PvP functionality.

## One-time consolidated Windows acceptance, only at the runtime milestone

You must already have a **legitimately installed Minecraft 1.8.9 profile with Forge 11.15.1.2318** and a working game directory. Do not run the installer while Minecraft is open.

1. Open the successful **Forge 1.8.9 CI** run under [GitHub Actions](https://github.com/Trexzo/ghostc/actions), and retrieve the **Windows build, unverified in-game** artifact. Its JAR, manifest, installer and instructions are stored together. GitHub may download the artifact as a ZIP; unpack only to your desired staging folder outside the live `mods` directory.
2. Identify your actual Minecraft **game directory**, e.g. `$env:APPDATA\.minecraft` for the default launcher (custom launchers may have a separate game directory).
3. Read the SHA-256 in the artifact's `SHA256SUMS.txt`; verify it is the line for `ghostc-forge189-1.0.0.jar`. The `-ExpectedSha256` argument is mandatory and the script verifies it again before any changes.
4. In a single PowerShell session, set these explicit paths to your own staging folder and actual game directory; the commands below are complete:

```powershell
& {
    $ErrorActionPreference = "Stop"
    $Staging = "C:\path\to\downloaded-GhostC-artifact"
    $GameDir = "$env:APPDATA\.minecraft"
    $Jar = Join-Path $Staging "forge189\build\libs\ghostc-forge189-1.0.0.jar"
    $Installer = Join-Path $Staging "tools\Invoke-GhostCAcceptance.ps1"
    $Hashes = Join-Path $Staging "forge189\build\libs\SHA256SUMS.txt"

    if (!(Test-Path -LiteralPath $Jar)) { throw "Set the correct staged Jar path" }
    if (!(Test-Path -LiteralPath $Installer)) { throw "Set the correct staged installer path" }
    if (!(Test-Path -LiteralPath $Hashes)) { throw "Set the correct staged checksum path" }

    $Line = Get-Content -LiteralPath $Hashes |
        Where-Object { $_ -match "ghostc-forge189-1\.0\.0\.jar$" } |
        Select-Object -First 1
    if (!$Line -or $Line -notmatch "^([0-9A-Fa-f]{64})\s+") { throw "Manifest mismatch" }
    $Expected = $Matches[1].ToUpperInvariant()
    $Actual = (Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash
    if ($Actual -ne $Expected) { throw "SHA-256 mismatch; installation refused" }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Installer `
        -Mode Prepare -GameDir $GameDir -JarPath $Jar -ExpectedSha256 $Expected
    if ($LASTEXITCODE -ne 0) { throw "Prepare failed" }
    Write-Host "Verified GhostC staged; now perform ONE meaningful licensed in-game test."
}
```

The code intentionally uses placeholders for staging folder and does **not** assume where you saved your GitHub artifact. The artifact may have an additional top-level folder depending on how you unpack it; point the three file paths to where they actually exist.

After a single meaningful runtime session (F7 UI, F8 status, remapped keys, HUD/ESP in an authorized world, disconnect/reconnect, stability) close Minecraft and generate the local report:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File "C:\path\to\downloaded-GhostC-artifact\tools\Invoke-GhostCAcceptance.ps1" `
    -Mode Collect -GameDir "$env:APPDATA\.minecraft"
```

To restore only the previously backed-up GhostC JARs, **close Minecraft first** and run:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File "C:\path\to\downloaded-GhostC-artifact\tools\Invoke-GhostCAcceptance.ps1" `
    -Mode Rollback -GameDir "$env:APPDATA\.minecraft"
```

## Transaction guarantees and deliberate refusals

The script changes **only matching GhostC-owned `ghostc-forge189*.jar` files** under the explicitly selected `mods` directory. It does not install Forge, touch unrelated mods, fetch unknown binaries, change registry/Java settings, or launch Minecraft. It:

- Refuses a missing/unrecognizable game directory, wrong source file name, wrong or absent expected SHA-256, symlink/junction at the recognized profile paths, ambiguous GhostC-named JARs and repeated Prepare with an outstanding transaction.
- Quarantines recognized old GhostC JARs into a transaction-specific backup directory under `ghostc-acceptance/backups/`, checks the newly copied JAR hash and writes an install-state manifest.
- Refuses Rollback if the active JAR or any saved backup differs from the recorded SHA-256; it does **not** silently overwrite changed files.
- Restores previous owned JAR files and removes the transaction manifest after a successful rollback.
- Generates a local `ghostc-acceptance/acceptance-report.md` with installation hashes, checkboxes and **only** safe aggregate `count.*` diagnostics. It does **not** copy raw `latest.log`, screenshots, world names, IP addresses, chat or private account details.

An interrupted filesystem transaction or unusual launcher file layout may require manual review; the script cannot guarantee power-loss atomicity. Its preflight and rollback are validated in CI with a disposable profile, **not** every possible real launcher.

## What is still required to declare GhostC game-ready

Screenshots and logs from an actual licensed Minecraft 1.8.9 client, accurate F7 GUI and module control behavior, render-state correctness, combat input interactions, FPS and a representative extended soak. Report any reproducible failures against [Issues #2–6](https://github.com/Trexzo/ghostc/issues). Redact logs before sharing.

**Never relabel this CI milestone as a successful real-game launch.**
