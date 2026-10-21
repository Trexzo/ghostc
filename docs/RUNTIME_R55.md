# GhostC R2.35 — reject backup-root junctions in Windows acceptance

## Source-audited filesystem safety gap

R2.34 validated the Minecraft profile root, `mods/` directory, `ghostc-acceptance/` and individual transaction backup folders against reparse-point links. It did **not validate the existing parent** `ghostc-acceptance/backups/` before writing or reading child backups. A Windows directory junction there can redirect `Move-Item` operations beyond the intended profile while the files themselves still satisfy name and SHA-256 checks.

That is a file-movement safety defect in the owner test installer, not a Minecraft rendering or gameplay defect.

## R2.35 correction

- Validate a pre-existing `ghostc-acceptance/backups` is a real directory and **not** a reparse point before entering Prepare, Collect or Rollback.
- During Prepare, validate `backups/` again once created; reject a pre-existing transaction-ID backup folder instead of reusing it. Validate the newly created transaction folder before moving any GhostC JAR.
- During Collect and Rollback, validate the backup root itself (the missing parent check) and then the exact transaction directory and SHA-256 of every original backup before touching the installed JAR.
- Preserve existing checks against symlinks/junctions at the profile, mods, acceptance/history, source and state-file levels. Preserve the existing checksum-gated Prepare, single-session evidence, archival behavior and rollback semantics.
- No changes to combat, network packets, client configuration, UI module defaults, actual 1.8.9 gameplay code or diagnostics report schema.

## Negative Windows CI replay

Using an isolated fake Minecraft profile and an external folder containing a sentinel:

1. Plant a real Windows directory junction at `ghostc-acceptance/backups` **before Prepare**. A valid, checksum-approved JAR must still be refused without removing the prior GhostC JAR, creating a transaction state, or changing the external sentinel. Remove just the junction; confirm external content survives.
2. Complete regular checksum/duplicate-name preflight, then a valid Prepare and initial Collect. Save report owner edits and refreshed numeric diagnostics.
3. Temporarily move the legitimate backup tree elsewhere within the acceptance fixture, replace its root with a junction to the sentinel folder, and attempt **Rollback**. It must reject the unsafe path without removing/changing the currently installed JAR or transaction state. **Collect** must also reject it without altering the accepted report.
4. Remove just the junction, put the verified backup tree back and continue the R2.33/R2.34 tests: wrong installed hash rejection, exact original-JAR restoration, two separate Prepare/Collect/Rollback sessions on the same JAR, byte-for-byte historical report preservation and fresh unchecked second report.
5. Require Core Java17/21, Linux/Windows JDK8 Forge/JAR/provenance CI green on the exact PR head, and once merged rerun both workflows on the final `main` SHA.

**Evidence boundary:** This proves guarded filesystem behavior on CI fixtures, not that a licensed Minecraft 1.8.9 process started or that gameplay/visual performance is correct. The G1/G2/G4/G5 live acceptance tasks remain open. The human acceptance handoff remains [R2.33](RUNTIME_R53.md) with [R2.34 transaction isolation](RUNTIME_R54.md).
