# GhostC R3.16 — Native acceptance readiness audit (not gameplay verification)

The existing Windows Prepare/Collect/Rollback helper can safely install an
artifact, back up the previous GhostC JAR, and produce an editable, local
`ghostc-acceptance/acceptance-report.md`. Its checkboxes are **human-only**.
CI cannot launch the user's licensed Minecraft 1.8.9 environment, observe
OpenGL rendering, or measure real FPS/soak.

R3.16 adds `tools/audit_native_acceptance.py`, a **read-only**, offline
checker distributed alongside the Forge JAR in both Linux and Windows CI
artifacts. It requires a real versioned Forge JAR, its manifest and
`SHA256SUMS.txt` from the same CI bundle. It verifies the on-disk JAR hash,
metadata, source revision syntax, Java 8 class-major metadata, Minecraft 1.8.9,
manifest schema and checksum filename. This is an internal-consistency check,
**not** a signed provenance chain or independent runtime proof. Run the existing
`forge_build_manifest.py verify` as the additional JAR structure verifier.

## Operator sequence (real native acceptance requires a human)

1. Download the **Forge** workflow artifact with its JAR, manifest, checksums,
   `Invoke-GhostCAcceptance.ps1` and `audit_native_acceptance.py`. Never use
   the headless Core CI JAR in Minecraft.
2. Work in a backed-up licensed **Minecraft 1.8.9**, **Forge 11.15.1.2318**, **JDK 8** profile. Use `Invoke-GhostCAcceptance.ps1 -Mode Prepare -GameDir ... -JarPath ... -ExpectedSha256 ...` with the digest printed in `SHA256SUMS.txt`. Do not run a CI fixture as a real profile.
3. Run the game normally, exercise UI, status HUD, focus, world transitions,
   profile reload and a measured 60-minute gameplay soak. Then run
   `-Mode Collect -GameDir ...`. Check boxes yourself only after observing
   the behavior; the full checklist remains intact and owner-owned. Do not
   upload raw `latest.log`, account names, chat, screenshots or credentials.
4. Run the audit, pointing `--report` to that session's Collect output:

   ```powershell
   python tools/audit_native_acceptance.py --jar .\ghostc-forge189-3.0.16.jar --manifest .\GHOSTC_BUILD_MANIFEST.json --sha256sums .\SHA256SUMS.txt --report '<GameDir>\ghostc-acceptance\acceptance-report.md'
   ```

   It prints JSON with package SHA, **unverified** session ID, checked/total
   counts and missing critical observations. `AWAITING_MANUAL_OBSERVATIONS`
   means the operator marked all minimum critical boxes but there is no
   session-specific manual evidence record.
5. Optionally create a **manual** local `native-observations.json`, after
   actual play, containing *exactly* this schema, with the session/sha read
   from the Collect report and artifact (not values copied from this example):

   ```json
   {
     "schema": 1,
     "session_transaction": "<32 lowercase hex digits from Collect>",
     "artifact_sha256": "<64 lowercase hex digits from manifest>",
     "minecraft": "1.8.9", "forge": "11.15.1.2318", "java_major": 8,
     "soak_minutes": 60,
     "observations": {
       "launcher": true, "mods_list": true, "clickgui": true,
       "keybind_and_hud": true, "local_world": true,
       "focus_reconnect": true, "profile_restart": true,
       "rendering": true, "performance": true, "soak": true
     }
   }
   ```

   Values above are **illustrative placeholders only**. Write `true`
   exclusively for things you personally confirmed in the matching session.
   Run with `--evidence <path>`; optional `--require-review-ready` returns a
   nonzero code until all minimum operator observations are present.
   Even a complete report produces **`OPERATOR_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW`**, never `INGAME_PASS`.
6. Review the original local observations independently. Roll back the
   installed client with `-Mode Rollback -GameDir ...` when complete.

## What CI actually verifies

- `test_audit_native_acceptance.py`: forged/mismatched report session or hash,
  altered manifest and JAR, duplicate/missing checkboxes, symlink input,
  oversized report, false/missing observations, toolchain mismatch and a
  synthetic fully complete checklist that must **not** become a gameplay pass.
- Linux and Windows Forge CI run the audit on their own produced package with
  **no report**; expected output is `AWAITING_NATIVE_REPORT`.
- Both Forge workflows package the auditor and this documentation, alongside
  the existing transactional acceptance tool.

This revision does not generate a game launch, replay screenshots, certify
native gameplay or claim measured FPS. Existing runtime code is unchanged.