# GhostC R3.89 — quarantined combat reset-only and peer continuity

Parent: exact CI-passed R3.88 draft PR #226 at `61421050136d21ed4bdd248278092fb37afcc3c9`. This is a separate stacked draft; no main or visual changes, merge, installation or release.

## Source audit and narrowly scoped changes

The R3.88 Forge exception handler correctly caught uncaught shared combat `RuntimeException` and allowed independent downstream movement and inventory controllers to continue. However:

1. `combatCalled` checked `combatFrameAdmitted`, but the manual-hotbar and projectile suspension branches did **not**. An already-quarantined controller could still execute live suspension policy on the following frames, despite synthetic combat being blocked.
2. If the emergency `combat.reset()` threw, its internal sequential policy resets could abort before the action/rotation coordinator cancellation. The R3.88 outer catch swallowed that cleanup failure, leaving potentially stale combat-owned intents until ordinary expiration.

R3.89 adds `else if(!combatFrameAdmitted)combat.reset();` directly after the normal combat-tick branch, ahead of both live suspension branches. The old manual hotbar/projectile precedence remains unchanged when admitted; blocked frames perform a conservative reset only.

On a shared-frame fault, the Forge adapter calls `CombatFaultCleanup.contain`. It attempts `combat.reset()` then independently revokes only combat-owned camera and action requests (`combat.aimassist`, `combat.clicker`, `combat.triggerbot`, `combat.blockhit`) even if the reset fails. It does not call global service reset or clear actions and camera requests belonging to inventory, movement or projectile modules. There are no new KeyBinding writes, packets, attack schedulers or UI changes.

The existing `CombatFrameFaultFence` identity recovery and `NativePulseSettlement` ATTACK/USE uncertainty remain authoritative. A new world generation plus changed native world/player identity is still required before synthetic combat resumes. Serious JVM `Error` types are not suppressed.

## Evidence and limits

- `CombatFaultPeerContinuityTests`: actual `HostRuntime`, owner-thread `SessionServices`, real action/rotation coordinators, simulated throwing controller reset, preservation of unrelated inventory/bridge intents, active inventory feature callbacks while combat is quarantined, same-world reconnect suppression, identity-verified recovery, foreign-thread rejection, and 12,000 seeded state frames.
- Forge original Minecraft 1.8.9 Java8 `nativeCombatFaultPeerProbe`: API linkage and one real Minecraft `KeyBinding.onTick` input queue, with a failing emergency reset and independent surviving inventory intent; runs headless.
- `tools/test_forge_wiring.py` enforces the actual adapter ordering: the quarantined reset-only branch is before manual/projectile suspension, the fallback cleanup runs inside the shared-fault catch, native-pulse warning publishes from `finally`, and bridge/velocity/WTap/JumpReset/inventory controllers follow outside the catch.

**Acceptance:** exact-head Core Java17/21, Forge Linux/Windows Java8 and downloaded-artifact rehearsal must pass. This is not native gameplay testing: an original Minecraft 1.8.9 launch, real graphical observation, physical input and any server confirmations remain unverified. No merges or rollout without those gates.
