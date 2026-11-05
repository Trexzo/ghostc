# GhostC R3.86 — Combat module lifecycle revision fence

Parent: fully CI-verified R3.85 combat-only draft PR #223 at `626156e5c345e5a45215d0590592ad6a67dd5bd7`. Stacked draft only. No Minecraft launch, installation, release, merge or visual-track changes.

## Source-audited bug

`ForgeCombatController.tick()` normally calls `reset()` on a module when it **observes** that module disabled. But a user can turn a module OFF and back ON between two owner-thread client END ticks using `HostRuntime.uiActions().toggle`, `ModuleRegistry.toggleKey`, profile changes or fault recovery. Both sampled frames then see the same final ENABLED state, despite the intervening lifecycle transition.

Stale in-memory `BlockHitPolicy` `post_attack` or `on_damage` event provenance and prepared native USE could survive a fast OFF/ON cycle. Similarly a previously READY TriggerBot candidate or pending Hit Select ATTACK preview could persist across a module reactivation. Existing native ATTACK/USE session settlement and event-coordinator checks are valuable separate barriers, but cannot substitute for invalidating source policy state on a real module lifecycle event.

## Change

- `ModuleRegistry.Entry.lifecycleRevision()` is an owner-thread state-transition receipt. The per-entry monotonically increasing counter advances before each actual enable/disable/fault lifecycle callback, including onEnable failure, onDisable failure, feature fault isolation and global Panic. Explicit FAULTED→DISABLED reset also advances its revision. No increment for no-op `setEnabled`, ordinary settings edits, render or tick. Overflow fails closed.
- `CombatLifecycleFence` is a pure owner-thread, allocation-free per-frame diff of five module receipts: AIM, CLICK, TRIGGER, HIT and BLOCK. A fresh `SessionServices.generation` or explicit full reset invalidates all five; any genuine revision change resets **only** its affected module. Backward epoch or module revision is rejected. An OFF→ON cycle between observations changes the revision twice and can no longer masquerade as steady ENABLED.
- `ForgeCombatController.syncModuleLifecycle()` observes registry revisions before any live combat `tick()`, projectile suspension or manual-hotbar suspension. It discards pending Aim camera lock/switch and rotation, Clicker schedulers and physical attack-key arming, TriggerBot dwell/UUID identity, Hit Select prepared/cooldown policy and Block Hit staged USE/event/cadence for changed modules. It cancels affected `ActionCoordinator` owners. Unaffected sibling modules keep their independent behavior and genuinely committed cooldown history.
- Full teardown calls `moduleLifecycle.reset()`; the existing world identity switch/leave/enter, GUI/focus, server disconnect and F6 Panic handlers still call `combat.reset()` and/or a verified `SessionServices` epoch transition. `SessionServices` still resets actions, rotations, inventory and packet callbacks on world transition/Panic.
- Critically, `NativePulseSettlement` is **not reset** by module toggles, lifecycle changes, Panic or focus transitions. A partial/unknown ATTACK/USE native `KeyBinding.onTick` still requires the existing verified new world/player identity + session epoch to recover. No extra native input or invented server acknowledgment.

## Tests

`CombatLifecycleRevisionTests` exercises actual `HostRuntime` and `ModuleRegistry` enable/disable (including OFF→ON entirely between ticks), Block Hit stale post-attack event removal, TriggerBot READY and Hit Select pending attack invalidation, FeatureFaultGuard fault disable/re-enable, global Panic owned ATTACK/USE/camera action cleanup, world leave/re-enter SessionServices epoch changes and physical-owner-thread assertions. Another 100,000 deterministic frames vary no-op, single-module toggles, double off/on toggles, Panic-like all-module changes and generation changes against an independent bitmask oracle.

Mandatory Forge Java8 `NativeCombatLifecycleRevisionTests` checks the genuine Minecraft 1.8.9 KeyBinding API and the actual controller sync / projectile / manual-hotbar method linkage, stale Block Hit use revocation, native ATTACK uncertain settlement persistence after module toggles/numeric epoch, and verified new native-world recovery. Python wiring tests guard real integration of registry revisions into controller decisions and preservation of `NativePulseSettlement`.

Exact-head Core Java17/21 and Forge Linux/Windows Java8 compile/reobfuscation, plus downloaded Windows artifact prepare/rollback, must succeed. Headless checks do not establish actual Minecraft gameplay or accepted PvP hits.
