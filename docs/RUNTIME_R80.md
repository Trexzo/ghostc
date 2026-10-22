# GhostC R2.60 — final live ESP paint admission

## Problem

A Forge ESP render pass collects and ranks its nearest candidates before iterating the chosen players for GL emission. R2.49 ensured that each draw candidate still resolved to the identical player in the live world and had valid health/visibility. However, source positions and bounding-box geometry could change after admission. An entity or local player outside the configured ESP range at paint time could still be drawn. Additionally, optional distance fade would use the previous admission distance rather than the updated location.

## Change

- A lightweight Minecraft-independent `EspLivePaintGate.distanceSquared` validates current target and local coordinates and the configured range immediately before any GL geometry. It returns NaN for invalid/nonfinite/moved-out data, otherwise the finite squared distance.
- The Forge render loop adds a second call to the existing `renderable` validation directly before geometry construction, so stale invalid bounding boxes/poses cannot emit GL vertices.
- The optional distance fade consumes the same validated current distance. With fading disabled, alpha and legacy RGB/palette remain unchanged; no extra rays are performed.
- Candidate selection remains nearest-N via the unchanged 80-item buffer, with its original LOS and world-object-identity gates, per-frame clear, style and draw budget. No change to HUD, combat, rotation, packet, settings, or saved profiles.

## Verification

- `EspLivePaintGateTests`: exact range boundaries, target and local movements, invalid/overflow positions, legacy fade off and live fade on, 120,000 randomized local/target/range scenarios against an independent mathematical oracle and shared HUD range predicate.
- Forge source checks enforce final admission and geometry revalidation between identity check and GL box emission, correct current distance used for fade, unchanged original nearest selection.
- Exact PR-head Core CI Java 17/21 and Forge Linux/Windows JDK8 build/reobfuscation, artifact provenance, simulated profile install/collect/rollback. Verify exact merged tree and post-merge workflow SHA.

Native Minecraft 1.8.9 rendering, visual output, true FPS, compatibility and prolonged stability are still unverified.
