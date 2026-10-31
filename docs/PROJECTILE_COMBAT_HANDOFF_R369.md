# GhostC R3.69 — projectile transaction to Aim/Trigger camera handoff

Stacked GitHub-only on CI-green R3.68 draft PR #206 commit `b2ccdca4aa6a36a7df75d7397a7fa95f61e631bb`. No merge into main, no local install, no changes to parallel visual branches.

## Source-level gaps

In `ForgeEventAdapter`, the exclusive AutoRod/AutoSnowball frames bypassed `combat.tick()` and invoked `combat.reset()`. This correctly cleared native attack/TriggerBot identity and click rearm, but **also reset `manualAim`**, so a physical mouse turn made during projectile SELECT/USE/RESTORE was never registered toward Aim Assist's configured `respect_mouse` pause. On the first legitimately unowned subsequent tick, camera rotation could resume prematurely.

A second edge: if an earlier projectile owner relinquished during its `tick()`, the returned `rodOwns/snowballOwns` could both be false while its **beginning-of-frame owner** was still ROD/SNOWBALL. The adapter could run combat on that same tick. In-game entity/pose checks exist, but this same-frame re-entry violates ownership isolation.

## Fix

- `ProjectileCombatHandoff.reserved` holds exclusive native camera/attack admission throughout any END tick in which there was an owner **at the start** or an owner **after controller execution**. Same-frame release/cancel does not permit a combat controller. This does not add a future cooldown: the next truly unowned END tick can run combat.
- `ForgeCombatController.suspendForProjectile` samples the already-published native `MouseHelper.deltaX/deltaY` for Aim Assist **only** while `respect_mouse` is on and the game is connected, focused and GUI-free. The method still resets all camera target state, input rearm, click schedulers, TriggerBot acquisition and pending rotation/action intents. Physical mouse pause alone is retained; no camera, key, packet or hotbar writes originate from the suspension.
- Missing mouse helper, disabled Aim Assist or disabled `respect_mouse` resets the manual sample. GUI/focus loss, Panic and world transitions continue using the full reset. The pre-existing opt-in R3.68 manual camera veto consumes preserved history when combat resumes.
- Read-only `CombatFrameAcceptance` receives full-frame projectile reservation flags, so a mid-tick release remains an exclusive frame in diagnostics. The first free tick after RESTORE still has the ordinary physical key release/re-press requirement; TriggerBot's acquisition state restarts from idle.
- No settings, new attack/camera dispatch, packet hooks, server-acceptance assumptions or post-projectile delay added.

## Verification

- `ProjectileCombatHandoffTests` covers end-of-tick cancellation, fresh native release and press, TriggerBot reacquisition, physical mouse pause through rod/snowball and 90,000 deterministic independent input/owner traces.
- `NativeProjectileCombatHandoffTests` links original Java8 Minecraft 1.8.9 MouseHelper + KeyBinding and the handoff guard; `forge189/build.gradle` makes it mandatory under `check`.
- The Forge source-wiring suite verifies exclusive ordering, continued manual mouse sampling during suspension and the first truly unowned combat call.
- Require exact-head Core Java17/21, Forge Linux + Windows JDK8 reobfuscation and downloaded Windows artifact rehearsal. **Not evidence of a launched original Minecraft 1.8.9 instance, real physical mouse freshness or server hit acceptance.** Keep the PR draft until native gameplay acceptance.
