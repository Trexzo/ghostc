# GhostC R2.84 — require a verified seal for backup recovery

## Source-level defect

Ordinary `ConfigIntegrity.read` supports unsealed schema-1 legacy
preferences for migration. `restoreBackup` reused this reader but
checked only schema and typed fields before promoting the copied
backup. As a result, a parseable **unsealed** backup could replace a
corrupt primary even though recovery and rotation are documented to
admit only modern checksum-verified copies. This allowed a backup with
no corruption evidence to displace a damaged primary.

## Fix

Before copying the backup into the primary location, require
`ConfigIntegrity.verify(staged)` to return true in addition to the
existing schema and typed checks. The restore path works from a private
copied snapshot and continues to leave live module/HUD state untouched
until promotion succeeds. Rejected backups do not modify the primary
or create `.corrupt` evidence. Valid verified backups retain their
existing atomic promotion and preservation of corrupt evidence.

The ordinary `ProfileStore.load` and `UiSettingsStore.load` paths
remain backwards-compatible with unsealed schema-1 files. Newly saved
files are sealed as before.

## Verification

Core Java8 `ConfigRecoveryTests.recoveryNeedsSealedBackup` runs 256
seeded direct unsealed-backup promotion attempts and sealed retries,
confirming no mutation on refusal, recovery when sealed, and retained
corrupt evidence. Integration checks cover profile keys and HUD
coordinates: both recovery entrypoints reject unsealed backups without
live mutation, ordinary legacy primary load still works, and the same
backup successfully restores once sealed. Forge source-wiring enforces
the exact `verify` admission gate before typed validation.

The release requires exact-head Core Java17/21 and Forge Linux/Windows
JDK8 build/JAR provenance, simulated install/rollback, tested/merged
tree parity and successful postmerge workflows. Native Minecraft
1.8.9 gameplay, GUI, FPS and extended stability are still untested.
