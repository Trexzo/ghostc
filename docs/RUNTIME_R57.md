# GhostC R2.37 — compensating Windows acceptance rollback

## Audited defect in R2.36

The R2.36 rollback implementation removed the installed, checksum-verified GhostC JAR **before** moving the original JAR backups into the live mods directory. If a restored original was blocked by a file lock, disappeared, or failed its post-copy SHA verification, rollback threw an exception after the active client was already removed. The saved transaction was left active, but its expected installed JAR was missing, so a normal retry could not proceed.

This is a recovery-integrity defect even when all earlier preflight and backup-junction guards work correctly.

## Implementation

1. Fail closed on a pre-existing `rollback-stage/` directory under the active transaction backup folder. Never overwrite staged recovery evidence from an interrupted previous attempt.
2. Before changing live `mods/`, **copy** every saved original into a new staging directory and verify each copy's exact SHA-256. The original backup copies remain intact through the operation.
3. Move the installed, verified active JAR into a guarded temporary path in the same transaction backup area. Verify its SHA-256 after the move.
4. Move the staged originals into `mods/` one at a time and verify each resulting JAR hash.
5. If any *handled* restore or state-commit step fails: verify and remove only successfully restored staged originals, move the exact active JAR back into the original live position, and leave `install-state.json` and the original backups intact for retry. Emit `GHOSTC_ROLLBACK_REVERTED`, not a success result.
6. If compensation itself fails, explicitly emit `ROLLBACK_RECOVERY_INCOMPLETE` with the retained stage location rather than destroying remaining recovery files or claiming a clean state.
7. Only remove `install-state.json` after all originals have been successfully restored and checked. Stage cleanup thereafter is best-effort, with a warning if manual review is required. Original backup copies remain preserved for extra recovery safety.

## Deterministic Windows regression

The fake-profile Windows JDK8 Forge workflow exercises both normal rollback and a **third** transaction with two distinct pre-existing GhostC JARs:

- Existing stage directory and marker: rollback refuses and leaves the installed client and marker untouched.
- Internal CI-only `-CiFailRollbackAfterRestoreCount 1` test hook injects a controlled failure after the **first** of two originals has entered live `mods/`. The hook is rejected outside GitHub Actions and outside a runner temporary fixture.
- Following the injected error, the installed JAR and its hash, transaction state, and both original backup JARs must all still be present and correct. No old JAR may remain in `mods/`, and no incomplete transient stage may be left behind.
- Retrying Rollback normally must restore **both** originals byte-for-byte, remove the active installed JAR and consume the active transaction.
- All earlier checksum, junction, diagnostics freshness, archived-session-report, and provenance checks still run.

## Scope and limitations

This is **handled exception safety**, not crash or power-loss atomicity. A process kill or power failure precisely between file moves may still need supervised manual recovery; the script refuses to overwrite a surviving stage. Nor does successful JDK8 Forge/CI simulation establish the actual Minecraft 1.8.9 run, graphics correctness, measured FPS, or one-hour stability. G1/G2/G4/G5 live acceptance gates stay open.
