# GhostC R2.52 — Aim Assist local-source pose freshness

## Source-audited failure mode

R2.47 added same-tick target-position and live entity checks to Aim Assist immediately before changing the local camera. But `AimAssistPolicy.suggest` derives its absolute yaw/pitch from the **snapshot's local player position and angles**, plus the live eye height passed to the policy. Neither camera orientation nor source XYZ/eye height was rechecked before applying that suggestion. If a movement/camera callback, mod, or stance transition changes these values before the final write, the suggestion would overwrite the new viewpoint with a result computed from a stale source pose.

## Implementation

- Each immutable `Suggestion` now retains the snapshot's numeric source XYZ and yaw/pitch and the source eye height used in its calculation. No Minecraft objects, UUIDs or world references are retained.
- New pure `sameSourcePose` refuses any nonfinite current source coordinate, angle or eye height, and requires exact equality to the values used by the policy. Exact equality is intentional for a same-owner-tick camera fence, not a movement smoothing feature.
- `ForgeCombatController` performs the check **after** obtaining the suggestion and validating the selected target, but **before** queueing a rotation intent or writing any camera angles.
- Preserve target-selection/FOV logic, rotation arbitration, module settings, defaults and vanilla key/packet paths. If another module has already rotated the camera, Aim Assist yields rather than overriding that newer pose.

## Verification

- Java8 `CombatTests` includes explicit valid-origin, horizontal/vertical/depth movement, yaw/pitch, eye-height and malformed numeric tests, plus 120,000 seeded source-pose mutation cases.
- Forge source-wiring checks assert source capture and the check's ordering ahead of `session.rotations().request`. Existing tests continue verifying target identity, same-target pose, selection and arbiter constraints.
- CI gate: exact final PR commit Core Java17/21; Forge Linux and Windows JDK8 compilation/reobfuscation, artifact integrity/provenance, fake-profile install/collection/rollback. Verify merged source-tree equality and post-merge commit CI.

## Limitations

No actual Minecraft 1.8.9 launch, visual parity, measured FPS or extended stability is established by source tests alone. G1–G5 continue to require runtime acceptance.
