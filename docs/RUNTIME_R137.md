# GhostC R3.17 — verified Windows acceptance preflight

R3.17 adds `tools/Invoke-GhostCNativePreflight.ps1` to the existing
GitHub Forge artifact package. It is a *wrapper* around the already-tested
transactional `Invoke-GhostCAcceptance.ps1`, not a game launcher.

## Goal

Prevent an operator from accidentally preparing a real Minecraft profile
with an artifact from a different source revision, Windows/Linux build,
JAR version or checksum. Verify runs before Prepare is permitted to write.
The wrapper also checks required Forge JAR entries and the `mcmod.info`
version, and refuses symlinked bundle files or directories.

This is internal CI-bundle consistency, not signed publisher authentication.
Download the artifact directly from the official
[`Trexzo/ghostc` Actions page](https://github.com/Trexzo/ghostc/actions/workflows/forge189-ci.yml),
choose a *successful main-branch* Forge workflow, and use its **Windows**
artifact `ghostc-forge189-windows-build-unverified-ingame`. Do not use
the headless JAR or combine files from separate workflow runs.

## One-session Windows acceptance

Extract the whole ZIP together in a new staging folder. From that folder
(ensure `tools/` and `forge189/build/libs/` exist, or use the flat archive
layout), inspect the workflow's 40-character **head commit SHA**. Do not
copy a SHA from a different PR workflow.

From Windows PowerShell 5.1:

```powershell
$sourceRevision = '<full-40-character-main-build-commit>'
$bundle = (Get-Location).Path
$game = 'C:\path\to\isolated\licensed\Minecraft-1.8.9-Forge-profile'
$preflight = Join-Path $bundle 'tools/Invoke-GhostCNativePreflight.ps1'

# Read-only: no game files changed.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $preflight -Mode Verify -BundleDir $bundle -ExpectedSourceRevision $sourceRevision
if ($LASTEXITCODE -ne 0) { throw 'Bundle preflight failed; do not install.' }

# Only on an isolated profile with Forge 11.15.1.2318 and JDK 8.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $preflight -Mode Prepare -BundleDir $bundle -ExpectedSourceRevision $sourceRevision -GameDir $game
if ($LASTEXITCODE -ne 0) { throw 'Preparation failed; do not launch this test session.' }
```

The script will invoke the existing checksum-gated Prepare and back up any
previous, recognized GhostC JARs in that isolated profile. It does **not**
start Minecraft. Launch your licensed 1.8.9 Forge profile normally,
observe GUI, input, HUD, world transitions, settings persistence and FPS,
and only then collect the native checklist:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $bundle 'tools/Invoke-GhostCAcceptance.ps1') -Mode Collect -GameDir $game
```

The human checklist lives under
`<GameDir>/ghostc-acceptance/acceptance-report.md`.
The separate `audit_native_acceptance.py` in R3.16 validates that
report's session and SHA against the package, but **never** asserts gameplay
success without independent review. Leave raw logs, account names and
screenshots private. After the trial:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $bundle 'tools/Invoke-GhostCAcceptance.ps1') -Mode Rollback -GameDir $game
```

## CI-only evidence

Windows Forge CI checks the real built bundle in Verify mode, fails closed
against a wrong 40-character SHA, simulates Prepare against a temporary
non-game directory and tests exact rollback. Linux CI verifies the JAR
and provenance independently. The source-compiled and Forge tests remain
separate from real native gameplay.

Until a person actually runs the client and supplies redacted observations,
this gate certifies **only a consistent build and safe test preparation**.
