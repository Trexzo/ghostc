# GhostC R3.69 — exact native provenance from immutable snapshot to Aim camera

**Base:** exact CI-green R3.68 draft PR #202 `991893de0ad621d6ba66df6f3bf899ae443df2d5`. Stacked draft GitHub-only; no main merge, installation, or visual branch change.

## Source-audited gap

R3.68 corrected **retention across camera ticks**, but during a *single* client END tick the immutable `WorldSnapshot.Entity` contained only numeric ID, positions, health and flags. `ForgeCombatController` later re-resolved the ID and compared exact **position** plus fresh current-world object-registry identity. If the original `EntityPlayer` was replaced with a new object with the same numeric ID, UUID and identical coordinates **between snapshot publication and combat output**, the new object could still pass the independent native pose/current-world checks despite never having supplied the snapshot row. `AimTargetSwitchPolicy` and `AimTargetLock` did not establish original snapshot provenance; they begin at the later camera selection point.

## R3.69 implementation

A new pure Java, owner-thread, bounded `AimSnapshotNativeFence` witnesses exact native `EntityPlayer` reference and UUID **only for rows actually copied into an immutable WorldSnapshot**. It also binds the originating local player, world, generation and END tick. No native object or UUID is added to `WorldSnapshot`; other clients of that immutable primitive-only class and its public constructors are unchanged.

`ForgeEventAdapter` starts the optional provenance witness before a real enabled Aim snapshot, after world/client state admission. Each row is registered *only after* the matching native-source row is added. Up to `MAX_ROWS=514` are admitted, matching the existing capped snapshot including its reserved pins. A duplicate ID, missing UUID, local self-target or overflow fails closed and clears retained references rather than publishing a partial trusted witness. A failed snapshot publication also clears the evidence.

`ForgeCombatController` now receives this witness and—after all R3.68 actual native source/target pose, alive, visible, world-registry, switch-delay and manual-input rules pass but **before** requesting any `RotationCoordinator.Scope.CAMERA` output—requires `aimSnapshot.matches` to verify the exact original native row instance, UUID, source world/player, generation and same tick, plus the **currently registered world entity**. Different object with same ID, UUID, XYZ and health is rejected; no speculative camera write, no stale lock or switch commit. Existing diagnostics still distinguish `AIM_APPLIED` from a rejected native candidate.

Native references exist only transiently in the owner tick: cleared immediately after the `combat.tick()/combat.reset()` phase in a `finally` block, on any exception before the combat phase, after a failed snapshot publication, at the next tick's start, and on disconnection. Identity is never exported, logged, serialized or retained in profiles. No additional Minecraft packet, key, inventory, camera or player write was introduced.

Because the snapshot witness is an **additional admission guard**, it cannot itself authorize a rotation or override R3.66 TriggerBot, R3.67 handback, R3.68 retention, manual mouse priority, HitSelect, projectile ownership or F6 Panic. It does not make the snapshot globally authoritative or claim server acknowledgement. Existing options and default-OFF behavior remain.

## Mandatory evidence

- `AimSnapshotNativeFenceTests`: **80,000 deterministic** independent exact capture-to-camera identity cases, each with cleanup, plus 514-capacity negative control, duplicate ID, foreign owner thread, wrong source world/player, wrong generation/tick, unrecorded target, wrong world registry and *same ID, UUID and XYZ with a replaced object* scenario using actual immutable `WorldSnapshot` + `AimAssistPolicy.suggest`.
- Forge Java8 `NativeAimSnapshotFenceTests` mandatory `check` probe: genuine Minecraft 1.8.9 native `Minecraft.theWorld/thePlayer`, `Entity.getEntityId/getUniqueID`, `World.getEntityByID`, `Entity.rotationYaw/rotationPitch` linkage with exact source witness.
- `tools/test_forge_wiring.py` must prove row collection precedes immutable publication, witness is verified before native camera request, all native references are erased after combat or failure, and native/Core tests remain mandatory.
- Exact-head Core Java17/21, Forge 1.8.9 Linux/Windows JDK8 build/reobf and Windows downloaded-artifact prepare/rollback rehearsal required before classifying the draft as CI-verified.

**Real in-game acceptance not performed.** Validate on a separately authorized launched Minecraft 1.8.9 client: two nearby players, same-UUID/ID respawn or object replacement, GUI/world/focus transitions, Aim target switching + lock, manual camera movement, TriggerBot handback and missing snapshot; preserve the exact candidate commit, installed Forge JAR SHA256 and redacted pass/fail. The headless API tests are not a live attack, mouse/camera observation or remote server confirmation.
