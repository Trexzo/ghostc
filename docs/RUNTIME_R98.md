# GhostC R2.78 — strict duplicate-key configuration parsing

## Issue
Java `Properties.load` silently accepts repeated assignments of the
same decoded key; the final assignment wins. Because GhostC's SHA-256
integrity digest is over parsed canonical fields, a sealed config can
contain a conflicting earlier value and still have a valid checksum.
An escaped alias (for example `v\\alue` versus `value`) has the
same ambiguity. This applies to module profiles and HUD UI settings.

## Fix
`ConfigIntegrity.read` now uses a small `Properties` subclass that
rejects a second decoded occurrence of any key during the JDK's normal
parser insertion, before integrity verification or typed staging.
It additionally enforces the existing 4,096-field cap incrementally.
Single-occurrence schema-1 legacy and modern integrity-sealed files
remain supported without changing their serialization.

Both recovery flows treat duplicates as invalid primaries and restore
only from validated backup snapshots. Backup rotation refuses a
primary containing duplicates; original corrupt bytes remain available
for incident diagnosis.

## Verification
- Unit regressions: identical duplicate assignments, conflicting values,
  different separators, escaped aliases, repeated schema or digest
  fields, unique legacy and modern success, and 3,000 seeded duplicate
  versus unique sealed-file comparisons.
- Integration: `ProfileStore.loadRecovering` and
  `UiSettingsStore.loadRecovering` reject ambiguous primaries and
  recover exact expected state from known-good backups, retaining
  backup bytes and `.corrupt` evidence.
- Existing Core Java17/21, Forge Linux/Windows JDK8, JAR verification,
  fake-profile install/rollback, backup rotation and source-wiring gates
  remain required.

Real Minecraft 1.8.9 runtime and long-session acceptance are separate.
