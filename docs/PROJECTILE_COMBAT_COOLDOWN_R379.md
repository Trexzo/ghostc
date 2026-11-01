# GhostC R3.79 — projectile handoff preserves completed attack cooldowns

Parent: CI-verified R3.78 draft PR #216 at `5f12ee90608572ae269514fc56fc1dbad0803819`. Only combat/transaction coordination changed; no Minecraft launch, merge, installation, release, or separate visual branch modification.

## Confirmed source defect

R3.69 correctly reserves a projectile SELECT/USE/RESTORE frame from its original hotbar owner and suppresses AutoClicker, TriggerBot and Aim Assist native output. During that hotbar reservation, however, `ForgeCombatController.suspendForProjectile()` called `resetNonMouse()`, which called both `triggerBot.reset()` and `hitSelect.reset()`. Those are **full world/focus teardown** operations: they erase `TriggerBotPolicy.lastDispatched` and `HitSelectPolicy.lastAccepted` even though those fields were already committed following a genuine KeyBinding.onTick. An AutoRod or AutoSnowball transaction could therefore shorten a previously committed trigger cooldown or the shared Hit Select minimum gap.

## Fix

- `resetNonMouse(boolean preserveCooldowns)` distinguishes temporary live projectile suspension from full lifecycle teardown. On a projectile-owned frame it still clears Aim candidate/switch state, both AutoClicker schedulers, PhysicalInputGate key rearm, Block Hit, native TriggerBot object/UUID fence, all pending ActionCoordinator ATTACK/USE requests and camera rotations.
- During the exclusive projectile frame, `HitSelectPolicy.cancelPrepared()` drops pending preview and `TriggerBotPolicy.invalidateCandidate()` drops acquired UUID/dwell and READY authorization **without erasing previously committed** `lastAccepted` and `lastDispatched`. This never emits native input, gives no free readiness, and does not add an arbitrary post-projectile delay.
- Full `reset()` invoked on world/focus/GUI/Panic or disabled combat retains `hitSelect.reset()` and `triggerBot.reset()` and clears committed cooldowns exactly as before.
- Original projectile arbitration, same-frame reservation after hotbar RESTORE/cancellation, manually selected hotbar precedence, AutoRod/Snowball identity and inventory safety, mouse history and attack rearm are unchanged.
- Hit Select/TriggerBot policies, any packet logic and all client module defaults/settings are unchanged.

## Verification

`ProjectileCombatCooldownTests` covers successful native receipt at tick 2, an exclusive rod/snowball sequence, invalidated pending previews, fresh post-projectile UUID acquisition, TriggerBot cooldown at 8 ticks and shared Hit Select at 10 ticks, independent first-allowed tick, new-world full reset and owner-thread protection. Forty thousand deterministic varied acquisition/cooldown/Hit Select gap/transaction durations check both original committed timestamps against an independent oracle.

Mandatory Java8 `NativeProjectileCombatCooldownTests` checks the actual original Minecraft 1.8.9 rod/snowball/KeyBinding linkage, Forge combat lifecycle method signatures and original pure policy transitions. Source wiring distinguishes preserve vs full reset and verifies remaining native arbitration boundaries.

Require exact-head Core Java17/21 + Forge Linux/Windows Java8 reobf and Windows downloaded-artifact preparation/rollback. Headless checks do not prove actual Minecraft gameplay, native packet server acceptance or real PvP hit outcome.
