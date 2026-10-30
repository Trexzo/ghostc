# GhostC R3.66 — TriggerBot native entity identity and Aim Assist camera handoff

**Parent:** R3.65 draft PR #199 exact CI-green commit `196c2e14d5df0822a2497bc58188798626034127`. Changes are restricted to combat source, pure Core/native tests, and this GitHub-only draft; do not merge, install or modify concurrent ClickGUI/HUD work.

## Confirmed behavior gap

The existing TriggerBot `TriggerBotPolicy` measures the native crosshair acquisition dwell using **UUID alone**. A Minecraft 1.8.9 `EntityPlayer` object can be removed and replaced within a loaded world while retaining the same UUID (and in some conditions reusing the numeric entity ID). Although `ForgeCombatController.triggerTargetValid` checks the current-world object registry, ray/AABB, LOS, range, team and health when considering and dispatching a target, the previous candidate could still **inherit elapsed acquisition ticks from a replaced player instance**. A last-moment native or Hit Select veto also left the policy's previously accumulated UUID dwell intact, potentially dispatching on the next frame without reacquiring.

## R3.66 runtime changes

- Introduce `TriggerNativeIdentityFence`, a **pure owner-thread, epoch/tick-fenced** native candidate identity policy. It observes the actual live `EntityPlayer` object reference, numeric entity ID, UUID and `World` object on every eligible TriggerBot END tick. It stores only transient in-memory references, no names, coordinates, packet data or persistent IDs. Replacing the object, ID, UUID or world restarts pending acquisition even if the UUID is identical. Missing crosshair, manual attack/use, or disabled TriggerBot clears the candidate.
- In the actual Forge TriggerBot producer, the identity fence is sampled only after `triggerTargetValid` validates native crosshair reference and live world, ray, reach, LOS and other existing restrictions. When a replacement is observed, the new `TriggerBotPolicy.invalidateCandidate()` discards **firstSeen/readyTick/UUID** without resetting `lastDispatched`. This is crucial: replacing an entity can never bypass an already-earned cooldown.
- Just **before the original native `KeyBinding.onTick`** attack, the winning TriggerBot owner also requires the fence's exact same-tick UUID/entityId/**object identity/world** permission *and* reruns `triggerTargetValid`. A changed/missing/stale target or Hit Select denial invalidates the in-flight candidate, requiring a fresh dwell on a later live target; no synthetic attack is emitted and no cooldown is falsely consumed.
- `AimTriggerHandoff` continues to yield Aim Assist's camera for a valid unheld native crosshair target. This does **not** grant immediate TriggerBot dispatch: the exact native entity must still earn the existing `acquire_ticks`, shared `ActionCoordinator` attack selection and Hit Select gate. Physical attack has priority; manual mouse camera settings, WTap, AutoRod/AutoSnowball exclusive ownership and CombatFrameAcceptance R3.65 remain unchanged.
- Existing `TriggerBotPolicy.due` API and settings are preserved for older callers/tests. The new exact native object reference lives exclusively in the Forge adapter and the pure fence. No new button/setting, packet hook, rotation/write, auto-targeting, extra clicks or movement behavior is added.

## Automated gates

- `TriggerNativeIdentityTests`: **80,000 deterministic** candidate switch and last-moment native veto traces; exact UUID, entityId, object, world and next-tick permission; policy acquisition, cooldown preservation, source-switch invalidation, actual coordinator single-consumption, Aim Assist camera yield and owner-thread negatives.
- New mandatory Java8 Forge `NativeTriggerIdentityTests` probes genuine Minecraft 1.8.9 `Entity.getUniqueID`, `getEntityId`, `World.getEntityByID`, `MovingObjectPosition.entityHit`, real `KeyBinding.onTick/isPressed` plus original TriggerBot policy/fence binding. **No launched client, physical mouse, server ACK, attack packet or hit claim.**
- Mandatory `tools/test_forge_wiring.py` ensures the exact entity fence precedes original vanilla attack dispatch, is cleared on disable/panic/final veto, and never performs game output; require exact-head Core Java17/21, Forge Linux and Windows JDK8 compilation/reobfuscation and Windows downloaded-artifact rehearsal.

## Real Minecraft acceptance still required

Using this exact candidate Forge JAR on an authorized original Minecraft 1.8.9 test server, test a stationary same-UUID player whose entity object changes across a world refresh, native crosshair moving off-target in the attack frame, Aim Assist + TriggerBot together with and without physical attack hold, manual mouse movement and use-item priority, Hit Select veto, and a genuine projectile transaction handoff. Preserve candidate Git SHA, JAR SHA256 and a redacted pass/fail log. Headless CI is **not** a real gameplay acceptance.
