# GhostC R2.34 — bind acceptance reports to an individual install transaction

## R2.33 problem discovered in source audit

R2.33 made the user's acceptance checklist non-destructive under repeated `Collect`. But after `Rollback`, `acceptance-report.md` survived in the profile. A **new** `Prepare` could install another JAR, or even the same JAR again, and the following `Collect` would keep the old checked boxes and notes **as though they applied to the new session**. The R2.33 report recorded a JAR hash but no transaction identity. Comparing only version or SHA-256 cannot distinguish two different live testing sessions.

## R2.34 behavior

- Each newly generated `acceptance-report.md` and `telemetry-latest.txt` includes the unique `install-state.json` **Session transaction** identifier, alongside the existing artifact name and SHA-256 identity.
- During `Collect`, an existing report is reused **only when all three identifiers match**: transaction, artifact filename and installed checksum. For that same active transaction, checked boxes and free-text notes remain **byte-for-byte** unchanged; telemetry counters are still refreshed separately.
- A report with a missing/mismatched session marker (including reports from R2.33 and older), an unexpected artifact name or a different hash is **not silently overwritten or reused**. It is moved, unchanged, into `ghostc-acceptance/history/<fresh-random-id>/acceptance-report.md`; its prior numeric telemetry sidecar is archived with it when present. A fresh unchecked report and new numeric sidecar are generated for the active transaction.
- If the report is missing but an orphaned sidecar exists, the sidecar is archived rather than attributed to the new test.
- The archive directory and existing files are checked for symlink/junction reparse points; collision detection refuses to overwrite historical evidence. No raw game logs, screenshots or secrets are collected.
- **Prepare and Rollback retain their current guarded installation semantics**; archives are created on the next `Collect`, not by altering the running game or JAR transaction. A historical report may remain at the top level between Rollback/Prepare and the first Collect of the next session; use the report's transaction marker and the current `install-state.json` to distinguish them.

## Automated verification

- Windows CI tests wrong-hash/conflicting JAR preflight, first Prepare/Collect, a human-like completed checkbox and free-text note, repeat Collect without edits, numeric privacy allowlisting, tampered-JAR rollback refusal, and exact restoration of the previous JAR.
- **New:** A second Prepare of the exact **same JAR hash** gets a different transaction; Collect must archive the previously edited report *byte-for-byte* with its old telemetry, produce a fresh unchecked and unverified report with the new session ID, preserve that new report on a second Collect, and perform another successful byte-for-byte Rollback.
- Core/Forge wiring tests assert transaction tags, history moves and the new Windows regression markers. The prior R2.33 JAR source, Java/Forge build and provenance verifications remain standard required CI gates.

**Important evidence boundary:** Neither these simulated sessions nor all-green Core/Forge CI constitute a real Minecraft 1.8.9 launch or complete G1/G2/G4/G5 acceptance. Continue with one authorized actual Windows session using the [R2.33 acceptance guide](RUNTIME_R53.md); the new R2.34 report is more robust against attribution mistakes but still requires human runtime verification.
