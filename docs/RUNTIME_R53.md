# GhostC R2.33 — attributable CI artifacts and non-destructive Windows acceptance

**This is a release-quality milestone, not proof of a real Minecraft 1.8.9 launch.**

## Evidence gaps confirmed at R2.32

- The Windows acceptance `Collect` path wrote a fresh `acceptance-report.md` each time. After an operator filled checkboxes or took notes, a later diagnostic collection silently erased that human evidence.
- The Linux and Windows Forge GitHub artifacts contained a JAR/checksum, but no structured record binding the downloaded JAR to the **exact checked-out source revision** and platform.
- Changes to `tools/**` could alter acceptance/package gates without triggering the path-filtered Forge workflow.

## Implemented R2.33

1. Linux and Windows CI now generate `GHOSTC_BUILD_MANIFEST.json` beside the JAR and `SHA256SUMS.txt`. Before publication the standard-library verifier reopens the JAR and checks the exact versioned filename, GhostC mcmod metadata, ZIP integrity, Java 8 class-file major=52, the SHA-256 and byte count, the canonical two-space checksum row, platform, and 40-character checkout commit. The manifest status is `CI_PACKAGE_ONLY_NOT_TESTED_IN_MINECRAFT`.
2. In pull requests GitHub Actions may checkout a synthetic merge commit. The `source_revision` field is **the local `git rev-parse HEAD` in that build**, not necessarily the PR head. On `main` it identifies the exact merged commit. Provenance establishes attribution, not deterministic byte-for-byte reproducibility, signing, trust in the build machine, or actual runtime behavior.
3. The Windows `Collect` operation creates `acceptance-report.md` **only if absent**. Every subsequent Collect leaves the operator checklist/notes byte-for-byte intact and instead refreshes `telemetry-latest.txt` with just the allowlisted numeric `count.*`, `perf.*`, and `snap.*` fields. Unknown/private values, raw logs, screenshots and usernames are not copied. Existing files are checked for unsafe reparse-point links before writes.
4. Forge CI triggers on relevant `tools/**` and `src/test/**` changes. Windows CI simulates Prepare, initial Collect, modifying a checklist plus an owner note, a **second Collect**, sidecar freshness/privacy, tampered-JAR refusal and byte-for-byte Rollback on an isolated fake profile.
5. Python regressions reject wrong/missing revisions, wrong platform, metadata tampering, malformed and multi-entry SHA256SUMS, swapped bytes/lengths, unrecognized manifest fields, and unsigned or invalid Java 8 mod containers.

## One meaningful owner acceptance milestone

The next evidence gate involves *one* real Minecraft session using an authorized Forge 1.8.9 profile on the operator's machine:

- Open [Forge 1.8.9 CI](https://github.com/Trexzo/ghostc/actions/workflows/forge189-ci.yml) at the exact **main** commit and download the Windows `ghostc-forge189-windows-build-unverified-ingame` artifact. Review `GHOSTC_BUILD_MANIFEST.json` and `SHA256SUMS.txt`; verify that the JAR hash is the one in both.
- With Minecraft closed, run `tools/Invoke-GhostCAcceptance.ps1` `-Mode Prepare` against the actual game directory and the exact JAR/hash; the script backs up only recognized GhostC JARs and refuses suspicious duplicates, bad hashes, unexpected transaction state, and links. It does *not* automatically launch a client or change unrelated mods.
- Start the licensed Forge 1.8.9 profile, verify version and F7/F8 GUI, focus/reconnect, HUD/ESP/Target visuals at representative GUI scales, save/reload one profile, then perform the representative 60-minute soak with diagnostics enabled, using a permitted local world/server.
- Quit Minecraft and run `-Mode Collect` in the same game directory. Review and complete `ghostc-acceptance/acceptance-report.md` yourself; collect again as needed for fresh numeric diagnostics in `ghostc-acceptance/telemetry-latest.txt`. Repeated collection never overwrites completed checklist boxes or owner notes.
- Run `-Mode Rollback` once the result is reviewed; the earlier GhostC version returns only if source/installed and backup hashes remain unchanged.

**Security/quality boundary:** Neither a checked box nor an offline report alone proves Minecraft behavior to GitHub CI. Provide only deliberately reviewed/redacted screenshots/log excerpts if needed. G1/G2/G4/G5 live verification and performance gates remain open until actual evidence is reviewed and attributed to a specific JAR hash and source commit.

## Verification targets

- Java 17/21 core regression suite and Python manifest/self-test.
- Linux and Windows Java 8 Forge compilation, reobfuscation, mcmod/JAR content verifier, checkout-bound manifest creation and independent verification.
- Windows fake-profile wrong hash/conflict/tamper/rollback; repeated evidence collection preserves owner edits and only emits allowlisted numeric telemetry.
- Post-merge verification must run on the exact new `main` SHA, not just the PR test head.
