# GhostC R2.4 — corruption-safe backup rotation on every save

## Source audit finding

R2.3 introduced SHA-256 integrity checking and validated startup recovery from `.bak`, but **normal saving still rotated the current primary into the backup unconditionally**. If a file became corrupted after successful startup, changing any UI preference or toggling a module could overwrite the sole remaining healthy `.bak` with the damaged primary. The next startup would then have no valid recovery point.

## Implementation

- New `ConfigIntegrity.rotateVerifiedBackup(primary)` stages the primary's **exact bytes** into a private temporary file in the same directory, verifies the staged file under the 1 MiB/4096-field input guards, requires schema 1 and the modern canonical SHA-256 digest, then promotes the validated copy to `.bak` with an atomic rename where supported.
- If the source is missing, corrupt, truncated, oversized, malformed or is an older schema-1 file lacking a digest, no backup rotation occurs. **Existing `.bak` remains untouched.** The save can still write a newly sealed, valid primary; the *next* healthy save resumes normal backup rotation.
- A legacy profile remains loadable (R2.3 compatibility). It is deliberately not treated as an authenticated backup-rotation source until successfully saved as a newly sealed modern file.
- Both `ProfileStore.save()` and `UiSettingsStore.save()` now use this shared verified-rotation function; direct unconditional `Files.copy(current,.bak)` is removed.
- Staged temporary files are cleaned on both successful and rejected rotations. Corrupted originals are not silently copied into the last known-good backup. Startup's R2.3 `loadRecovering` and `.corrupt` preservation remain unchanged.

## Automated evidence and remaining limits

`BackupRotationTests` exercises healthy first/second-save rotation, rejected direct backup promotion, over **200 successive corrupted-profile → healthy-save** transitions, over **100 seeded damaged-UI → healthy-save** transitions, malformed digest rejection, oversized primary refusal, legacy schema-1 promotion refusal, new file integrity checks and temporary-file cleanup. Exact preservation of existing `.bak` bytes is asserted after every refused rotation.

A direct Forge/source integration contract checks the **actual profile and UI save paths**, requiring staged SHA validation and prohibiting unconditional copying. This supplements all existing Java17/21 regression classes, Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation, JAR integrity verification and fake-profile Windows install/collect/rollback. Version: **2.4.0**. Repository inventory must retain 24 Java tests and 17 milestone guides R08–R24.

SHA-256 here is an accidental-corruption detector, **not an authenticated signature against a deliberate attacker** who can modify and reseal files. An unsealed legacy file cannot be proved intact by a digest. GitHub CI does not launch licensed Minecraft or prove GUI, render, combat, or soak behavior. All these runtime acceptance issues stay open.
