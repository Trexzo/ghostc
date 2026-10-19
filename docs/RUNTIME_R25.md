# GhostC R2.5 — typed-valid backup admission

## Confirmed source defect

R2.4 checked backup candidates for staged-byte integrity, SHA-256 and schema=1. A file can nevertheless have a **correct integrity digest** while containing typed-invalid settings: a reserved/out-of-range/non-numeric module keybind, a malformed module setting, an invalid UI layout/theme, or a nonfinite/out-of-range widget anchor. A normal save could overwrite a loadable `.bak` with this unloadable candidate.

## Fix

- `ConfigIntegrity.rotateVerifiedBackup(primary, typedValidator)` applies a side-effect-free application-level validator to the exact staged bytes, **before** promoting them to `.bak`. Existing size limits, checksum validation, atomic move and temporary cleanup remain in place.
- `ProfileStore.stage(Properties)` now provides both normal load and backup admission with the same enabled-flag, keybind, setting-type and duplicate-key validation. No live registry state changes during the backup preflight.
- `UiSettingsStore.stage(Properties)` similarly parses the layout, theme, main HUD anchor and every optional widget position before any state change. Both normal load and backup admission call it.
- Candidates failing either integrity or typed validation are rejected. Existing backup bytes survive unchanged, while a fresh, valid primary may still be written. Legacy schema-1 without integrity digest stays readable but not eligible to replace a verified backup.
- No changes to profile schema, established module IDs, packet behavior, rendering entry points, or Java 8 target compatibility.

## Evidence and limitations

`BackupRotationTests` now tests **valid SHA-256 seals over invalid data** for module keybinds and UI enums/coordinates, verifying byte-for-byte retention of the previous backup and continued writing of a sealed primary. Direct Forge/source wiring tests require production saves to perform typed backup admission and normal loads to share the validator. All 24 previous Java test suites and R08–R24 guides are retained (18 guides through R25).

Required CI checks: Java 17/21 offline suite; Linux and Windows JDK8 Forge 1.8.9 compile/reobfuscation plus JAR checks; Windows fake-profile checksum install/collect/rollback. Version **2.5.0**. Real licensed Minecraft 1.8.9 launch, visuals, UI interactions, PvP behavior, and 60-minute soak are **not verified** by these gates, and Issues #2/#3/#5/#6 remain open. SHA-256 checks accidental corruption, not malicious tampering.
