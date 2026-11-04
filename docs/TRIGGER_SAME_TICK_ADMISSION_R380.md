# GhostC R3.80 — idempotent TriggerBot native attack admission

Parent: fully CI-verified R3.79 draft PR #217, commit `e757f607e87d032b12750cf384ab830a7cd80cc9`. Combat-only PR; not merged, installed or released. Separate visual work, module defaults, attack pacing, and projectile SELECT/USE/RESTORE remain unchanged.

## Source-audited issue

The native TriggerBot path uses both `TriggerNativeIdentityFence.observe` and `TriggerBotPolicy.due` during the Forge combat END-tick. `TriggerBotPolicy.due` previously permitted a second `READY` result on the same generation/tick while its first candidate had not yet called `dispatched`. At the same time, `TriggerNativeIdentityFence.observe` threw an `IllegalArgumentException` for an identical duplicate observation because it rejected `tick<=lastTick`. Reentrant/duplicated same-tick combat observation could thus produce duplicate candidate intent or quarantine the TriggerBot module for an otherwise harmless repeated snapshot. The ActionCoordinator and pulse settlement already constrain actual native dispatch, but they do not justify duplicate source proposals or unnecessary module faults.

## R3.80 patch

- `TriggerBotPolicy.due` returns false for a repeated tick. If the duplicate indicates physical/manual attack ownership, candidate disappearance, changed target UUID, or live acquire setting change, the existing `invalidateCandidate` path revokes a pending ready token **without clearing** `lastDispatched`. An identical repeat leaves the already-staged first permission intact for normal final native validation; a second proposal is never emitted.
- `TriggerNativeIdentityFence.observe` treats an identical repeated native world/entity/UUID/ID snapshot as passive. A changed player object, UUID/ID, world, or missing crosshair during the same tick **clears** the original identity and refuses to register a replacement until a later native tick. Invalid input parameters and backward time still throw, foreign threads remain rejected. This prevents a same-tick object replacement from laundering earlier dwell.
- Existing actual Forge integration still calls `triggerIdentity.observe`, invalidates candidate on replacement, validates `TriggerNativeIdentityFence.permits` against the exact world/player immediately before output, and checks physical input using `NativeAttackFrameGate.attackState`. AutoClicker retains priority over TriggerBot during manual attack ownership; both still share one ActionCoordinator ATTACK decision and one optional Hit Select cooldown. Projectile interruption continues to reset pending attack/dwell but retain completed cooldowns from R3.79.
- No packets, Minecraft keyboard writes, new native input, new scheduler delay, or target prediction is added. Session/lifecycle hard reset and native pulse uncertainty quarantine are unchanged.

## Evidence and limitations

`TriggerSameTickAdmissionTests`: exact same-tick duplicate, manual override, same-UUID replaced native player, target change, no same-frame reacquisition, single ActionCoordinator winner, shared Hit Select and committed TriggerBot cooldowns, next-tick fresh dwell, owner-thread rejection, and 80,000 randomized target/acquisition/cooldown scenarios.

`NativeTriggerSameTickAdmissionTests`: mandatory Java8 original Minecraft 1.8.9 KeyBinding / Forge AttackEntityEvent native API linkage, unchanged ForgeCombatController entrypoint, identical duplicate and changed-object policy transitions; no game or server launched. Existing `TriggerBotTests` and `TriggerNativeIdentityTests` updated to assert idempotent duplicate observation rather than a fault. The Python source wiring tests guard actual output path, idempotent policy checks and mandatory Forge `check` registration.

Require exact-head Core Java17/21 and Forge Linux/Windows Java8/reobfuscation with Windows downloaded-artifact rehearsal. These headless checks do not establish actual Minecraft gameplay, PvP hit acceptance or real packet timing.
