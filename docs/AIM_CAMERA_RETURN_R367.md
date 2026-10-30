# GhostC R3.67 — Aim Assist / TriggerBot camera handback

**Stacked parent:** exact CI-green R3.66 draft PR #200 at `1d6aa7c2a953065d3701ab4473df2e636c43c8b7`. GitHub-only separate draft. Nothing merged into main, installed locally, or changed in parallel UI/HUD branches.

## Actual source gap

In the native Forge combat controller, Aim Assist correctly yields the camera on any genuinely verified, unheld TriggerBot crosshair target. Previous behavior then called `aimLock.clear()`, `aimSwitch.reset()` and `manualAim.reset()` on **every** yielded tick. Losing TriggerBot's target on the next tick meant `AimTargetSwitchPolicy` viewed the next Aim candidate as a *first* target, admitted it immediately, and skipped an explicitly configured nonzero `switch_delay_ticks`. Resetting `manualAim` while yielding also discarded observed mouse movement during TriggerBot's ownership, permitting an Aim camera write just as the player resumed manual control.

## New runtime behavior

- New owner-thread, world-generation/tick-fenced `AimCameraReturnFence` owns only **camera handback admission**, not mouse input, target selection or native rotations. `yieldCamera(epoch,tick)` is observed once each native TriggerBot camera-yield frame.
- On the first Aim frame after a yield, **positive** configured `switch_delay_ticks` requires an uninterrupted, fresh, fully validated candidate for that many consecutive game tick intervals. Candidate identity includes the exact native `EntityPlayer` object, entity ID, UUID, and `World` object; changing any one restarts the handback dwell. The ordinary `AimTargetSwitchPolicy` remains independently enforced, including its hard UUID target-lock behavior. `switch_delay_ticks=0` preserves the existing immediate handback default.
- A null/invisible/despawned/moved target, mismatched live source pose, missing required attack hold, physical manual mouse pause or feature failure calls `interrupt()`, preventing earlier handback dwell from being inherited. The handback is committed with `applied()` **only after** a successful real `RotationCoordinator` CAMERA write, and never when a merely proposed rotation loses. World/focus/disconnect/Panic resets discard all camera handback state.
- On valid TriggerBot-owned camera frames, the existing opt-in `AimManualInputPolicy` continues to receive the original Minecraft `mouseHelper.deltaX/deltaY` values. The same physical-input pause remains active when Aim Assist attempts to reclaim its camera. When `respect_mouse` is disabled (the original default), legacy behavior is retained.
- No new UI settings, packets, aim automation, native input pulses, targeting, movement, extra attack or retry have been introduced. AutoClicker/TriggerBot/HitSelect shared attack arbitration, R3.66 exact target identity, WTap, projectile hotbar ownership, diagnostics and visuals remain unchanged. Default OFF for Aim Assist/TriggerBot is preserved.

## Automated proof

- `AimCameraReturnTests`: 60,000 deterministic native object/world/UUID/ID, dwell, settings and manual interruption episodes, with standalone exact-limit checks, native ActionCoordinator-independent CameraReturn commit semantics, `AimManualInputPolicy`, `AimTargetSwitchPolicy` and `AimTriggerHandoff` integration.
- Java8 `NativeAimCameraReturnTests`: original Minecraft 1.8.9 `Minecraft.mouseHelper`, `MouseHelper.deltaX/deltaY`, `Entity.getUniqueID/getEntityId`, `World.getEntityByID` native API linkage plus handback policy. Mandatory Forge `check` task on Windows/Linux.
- `tools/test_forge_wiring.py` now requires correct ownership ordering (yield before handback candidate, return candidate before native camera request, native commit afterward), no output/packet writes in the pure gate, real mouse policy and test inclusion.
- Exact-head Core Java17/21, Forge 1.8.9 Linux/Windows JDK8 compile/reobf/native suite and downloaded-artifact Windows install/rollback rehearsal are required; these do **not** constitute launched Minecraft gameplay acceptance.

## Remaining real Minecraft 1.8.9 acceptance

With this exact candidate installed on an authorized original Minecraft 1.8.9 client, verify native TriggerBot crosshair acquiring and losing two nearby real players, Aim Assist `switch_delay_ticks` 0 versus a positive value, manual camera delta while TriggerBot owns the camera, world/target replacement, focus loss and F6 Panic. Record the exact commit, JAR SHA256, redacted gameplay pass/fail and opt-in diagnostics. No headless Java/Forge simulation proves a real remote server attack or actual camera movement in-game.
