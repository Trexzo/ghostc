# GhostC R2.3 — integrity-checked configuration and guarded recovery

## Why

Previously GhostC saved .bak copies beside both default.properties and ui.properties but did not attempt recovery from them after a damaged primary. Even a truncated profile could be accepted because omitted module keys and enable fields have defaults. A subsequent save could rotate a damaged file over a good backup.

## Implementation

- `ConfigIntegrity` creates a deterministic SHA-256 digest from canonically sorted property names and values, using length-prefixed UTF-8 bytes and excluding the `integrity.sha256` field. Newly saved module and UI profiles are sealed.
- Normal `load` methods verify the modern digest **before mutating live settings**. Legacy schema-1 files without an integrity field remain readable for backward compatibility. The checksum detects accidental damage; it is not an authenticity signature.
- A 1 MiB read-size limit and 4096-field limit reject implausibly large corrupted settings input.
- New startup `loadRecovering` methods try the primary, then validate and load a .bak if the primary fails. A successfully validated backup is copied to a temporary file, checked again and moved into place atomically when possible. The damaged primary is retained byte-for-byte as a local .corrupt file.
- Recovery does not rotate or overwrite the good backup. A short status warning reports that the backup was used. A corrupt backup does not replace the original.
- The original strict load methods still report damaged configs directly when invoked, allowing testing, diagnosis and explicit validation.
- New `ConfigRecoveryTests` exercise tampering, property ordering, Unicode, size limits, legacy files, corrupt primary and backup, successful restoration, transaction atomicity, backup preservation and the HostRuntime startup path.

## Verification and boundaries

Core Java17/21 tests and Forge Linux/Windows JDK8 Minecraft 1.8.9 compile/reobfuscation/JAR-integrity checks remain mandatory, alongside the safe Windows fake-profile install/collect/rollback rehearsal. Version **2.3.0** retains all earlier source and milestone files; the repository-inventory gate now covers 23 Java suites and 16 runtime guides (R08–R23).

A legacy schema-1 file without a digest cannot be proven complete by this scheme, although present values still undergo typed validation. SHA-256 is an accidental-corruption detector, not malicious modification protection. A .corrupt file contains local configuration and must not be shared without inspection and redaction.

Actual licensed Minecraft GUI/gameplay, in-world rendering and long-session FPS/soak remain unverified by CI. Keep Issues #2, #3, #5 and #6 open pending a consolidated Windows runtime session. See `docs/RUNTIME_R10.md` for safe installation and rollback.
