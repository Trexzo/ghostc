# GhostC R3.70 — final native Aim CAMERA pre-write veto

**Stacked parent:** exact CI-green R3.69 draft PR #203 at `b93e8cac21dcd75696a75e53ab1e86326a246ed4`. Separate GitHub-only draft. No main merge, local installation, GUI/HUD/ArrayList changes, new settings or new default-enabled modules.

## Source-backed audit

R3.69 captured the exact original native source entity for each immutable snapshot row and verified it **before** submitting a CAMERA rotation request. However, `ForgeCombatController` previously called `session.rotations().decide()` and then immediately assigned `player.rotationYaw` and `rotationPitch` whenever Aim Assist won the rotation coordinator. It did not refresh the world/player references, source camera pose, target coordinates/status, GUI/focus state or native mouse and attack/use-key state at the **actual camera assignment boundary**.

This is normally one synchronous Minecraft client-owner callback, NOT an asynchronous server ACK; no independent callback is expected between the coordinator's decision and the two field assignments. The improvement is defense-in-depth against reentrant/native state changes within that interval, or future code changes that create a nontrivial gap.

## Implementation

New pure, owner-thread `AimFinalCameraWriteGate` captures an ephemeral, in-stack ticket after all earlier live-native checks have passed, immediately before the existing `RotationCoordinator.Scope.CAMERA` request. A ticket includes the world, local player, immutable snapshot and exact target object references; the session generation/client tick; target ID/UUID; validated source XYZ/yaw/pitch/eye height; target XYZ/health/dead/invisibility; and the original native focus, GUI, enabled, connected, optional require-click/respect-mouse flags, physical attack/use-down, using-item, and mouse-helper delta availability/samples. No game types are stored globally or logged.

After a true winning `RotationCoordinator.decide()` result, immediately **before the first** `player.rotationYaw` write, Forge resamples these values from native Minecraft and requires:
- exact same world/player/snapshot/target object and session; no reused UUID/ID;
- unchanged native source pose (including camera rotation) and target position/health/liveness/visibility;
- unchanged physical attack/use and item-use state, focus, GUI, connection, enabled and opt-in settings;
- when `respect_mouse` is ON, the same native mouse-helper deltas and an available helper. When OFF, changed deltas are deliberately ignored to preserve legacy settings;
- independently rechecked current-world target registry, `LiveCombatTargetGate`, immutable suggestion source/target pose, and R3.69 snapshot-to-target native witness.

If any final check fails, the code cancels the Aim rotation, interrupts any pending TriggerBot-to-Aim handback, clears Aim hard target lock and resets Aim switch state; **no native camera field is written or synthetic click triggered**. Successful identical state uses the exact existing camera output/commit logic with unchanged Aim speed, FOV, delay, retention, TriggerBot, HitSelect, projectile or mouse behavior. There is no queued retry and no authority granted by the veto itself.

The ticket is a local object that becomes unreachable when the synchronous callback ends; it is not persisted or added to `WorldSnapshot`. Only the Minecraft owner thread may access the pure policy. These checks are a last-moment read-only guard, not a way to make the pair of yaw/pitch Java writes atomic with all external systems.

## Mandatory verification

- `AimFinalCameraWriteTests`: **80,000 deterministic** last-minute session/world/player/snapshot/target/UUID/ID/source-pose/target-health/input/mouse/focus/GUI/config change traces; explicit mouse-respect OFF compatibility, require-click safety, malformed/foreign-thread inputs and actual `RotationCoordinator` grant-then-veto cancellation regression.
- Java8 original Minecraft 1.8.9 `NativeAimFinalCameraWriteTests`: real `Minecraft.theWorld/thePlayer/inGameHasFocus/currentScreen/gameSettings`, `MouseHelper.deltaX/deltaY`, `GameSettings.keyBindAttack/keyBindUseItem`, `Entity.rotationYaw/rotationPitch` field linkage plus an independent final-gate probe. Mandatory in Forge `check` on Linux and Windows.
- Python `tools/test_forge_wiring.py` requires the ticket be captured before request and the native veto be executed **after decide but before the first camera write**, with session/target/snapshot and physical input checks, cancellation, and all Core/native tests mandated.
- Require exact-head Core Java17/21, Forge 1.8.9 Linux/Windows JDK8 build/reobfuscation/native suite and downloaded Windows-artifact prepare/rollback rehearsal before declaring CI success.

## Real Minecraft 1.8.9 gameplay acceptance is still NOT performed

On an authorized original Minecraft client, verify manual physical camera movement and mouse-respect ON/OFF while Aim Assist is enabled, hotbar/use-key/attack toggles, GUI/focus transitions, source pose movement, target replacement under same ID/UUID, target death/world swap, TriggerBot camera handoff, and alternate RotationCoordinator ownership. Capture exact candidate Git SHA, installed Forge mod JAR SHA256 and redacted pass/fail observations. Neither the native Java8 linkage probe nor the deterministic unit suite confirms an actual camera movement or a server-accepted PvP hit.
