# GhostC R2.6 — bounded config admission and transaction-safe startup recovery

## Confirmed gaps in R2.5

1. ConfigIntegrity.read inspected Files.size before parsing, but a file growing after that check could exceed the 1 MiB read bound. Legacy schema-1 files without an integrity field bypassed the 4096 parsed-property field count check.
2. ProfileStore.loadRecovering and UiSettingsStore.loadRecovering first applied backup settings **to live objects**, then asked restoreBackup to copy the backup onto disk. If disk restoration failed or the backup changed after parsing, memory could disagree with disk and a failed recovery could still modify enabled modules, keybinds or HUD layout.
3. The original restoreBackup read/validated only the integrity seal of its newly copied bytes, not the caller's full typed schema. R2.5 typed validation had been added to **normal backup rotation**, not to startup recovery's promoted copy.

## R2.6 implementation

- Read bounded bytes from the actual open input stream with an 8192-byte buffer; reject a read that would exceed 1 MiB even if the initial size check passed. Parse only the bounded snapshot bytes. Apply the 4096-field cap to both legacy and digest-protected schema-1 files.
- Recover using ConfigIntegrity.restoreBackup(primary, typedValidator), which stages one private snapshot of the backup, verifies checksum, schema and exact caller typed settings, then promotes the **same bytes** atomically where supported. As before, preserve damaged primary as .corrupt before replacement and leave existing .bak intact. Failed staging/preflight does not create a misleading .corrupt file.
- ProfileStore and UiSettingsStore now call the stage validator before promotion, then load the promoted primary into live state **after** successful file replacement. There is no preliminary loadFile(backup) that could mutate live state when recovery later fails.
- Preserve legacy schema-1 compatibility; new writes remain integrity sealed and typed backup rotation from R2.5 remains intact.

## Tests and acceptance

ConfigRecoveryTests adds (a) an over-4096-field legacy file, and (b) separate profile and UI cases where primary checksums are damaged, backups have valid SHA-256 checksums but typed-invalid settings, and a failed recovery must not modify live state, primary/backup bytes or invent .corrupt artifacts. Existing healthy recovery tests must continue to pass. Source wiring regression checks require bounded stream reads and typed restore-before-live-load paths.

CI requirements: Java 17/21 core regression suites (24 retained), Linux/Windows JDK8 Forge 1.8.9 compile/reobfuscation/JAR integrity, Windows fake-profile installation/rollback, and 19 cumulative R08–R26 milestone guides. Version **2.6.0**. No in-game Minecraft client launch, on-screen rendering, input behavior, competitive network behavior or 60-minute soak is proven by CI. Issues #2/#3/#5/#6 remain open for this evidence.
