# GhostC R3.18 — downloaded Windows artifact acceptance gate

R3.18 adds a new Windows CI job after `windows-forge189` uploads
`ghostc-forge189-windows-build-unverified-ingame`. The job uses
`actions/download-artifact@v4` to extract the **user-delivered archive**
to a fresh directory. It does not rely on the original build workspace.

Checks on the actual extracted delivery:

- One versioned Forge JAR, matching SHA256SUMS, manifest, Java 8 class
  structure, and the workflow's checked-out source revision.
- Required acceptance scripts, Python verifier, auditor and instructions
  are actually included in the downloaded artifact.
- Verify mode succeeds with no profile modification.
- A deliberately incorrect source revision cannot change a temporary
  profile.
- Correct Prepare/Rollback on that very package restores the fixture.
- A tampered **copy** of the delivered JAR is refused before install.

The new Forge version 3.0.18 distinguishes this revised handoff bundle
from R3.17. All tests run only on temporary fake profiles in CI, not the
owner's Minecraft account, game files, raw logs or screenshots.

**Scope of evidence:** this is a downloadable package verification, not
a successful Minecraft launch, FPS measurement, UI/render inspection
or a 60-minute gameplay soak. The operator's native acceptance process
remains [R3.17](RUNTIME_R137.md) and requires human observation.
