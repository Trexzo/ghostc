# GhostC R3.90 — world-transition combat reset fault isolation

Parent: exact CI-verified R3.89 draft PR #227 at `e39e59fab55c56efa7ac3362bf62854222394649`. Stacked draft only: main, visuals, release artifacts, installed profiles and actual player state are not modified.

## Audited defect

The Forge 1.8.9 END-tick adapter already protected the normal shared `combat.tick()`/suspend/reset branch in R3.88–R3.89. It **did not** protect its two earlier world lifecycle teardown blocks. On `WorldIdentityTracker.Transition.LEAVE` or `SWITCH`, and again on `ENTER` or `SWITCH`, `combat.reset()` ran before the bridge, velocity, WTap, jump-reset, projectile and inventory resets. A shared controller `RuntimeException` in those two early calls could abort that broader teardown and stop the rest of the owner-thread tick. That violates the independent peer-continuity objective already tested for ordinary combat frames.

## R3.90 patch

- Replace exactly the two early `combat.reset()` world-transition calls with `resetCombatAtWorldBoundary()`; leave the existing ordinary END-frame combat branch, manual/projectile ownership and native key writes unchanged.
- `CombatWorldBoundaryRecovery.reset` validates the owner-thread session, tries the real controller reset and catches only `RuntimeException`. On a failure, it **pins the currently faulting world/player at the current generation**, quarantines the shared frame and calls R3.89's combat-only fallback cleanup (a second best-effort reset followed by individually cancelled Aim/Clicker/TriggerBot/BlockHit coordinator owners).
- The helper then returns normally so the existing bridge/velocity/WTap/jump-reset, projectile and inventory reset operations run in their original order. Independent requests are not globally reset by the helper; the existing worldPresence session transition still performs its own normal full invalidation.
- Immediate, generic host warnings are published for shared combat and native ATTACK/USE uncertainty. Native `NativePulseSettlement` is never cleared or rearmed by this helper.
- A second worldPresence advance for ENTER alone does not make a SWITCH reset failure safe: when the new world's identity was pinned at the failing LEAVE boundary, the following ENTER has the **same** exact object and leaves the shared quarantine intact. A newer generation with a genuinely different native world/player is required for recovery.
- Serious JVM `Error` values still propagate; this is neither a broad exception-suppression system nor an artificial server ACK/reconnect.

## Tests and acceptance

- `CombatWorldBoundaryRecoveryTests`: real `HostRuntime`, `SessionServices`, `CombatFrameFaultFence`, `NativePulseSettlement`, exact world SWITCH/ENTER/LEAVE generation progression, throwing first and retry resets, owner-thread checks, preserved independent inventory/bridge intents, non-combat callback continuation, generic warning publication, serious JVM Error propagation and 16,000 seeded fault/recovery frames.
- `nativeCombatWorldBoundaryRecoveryProbe` runs through mandatory Forge Java8 `check` with original Minecraft 1.8.9 `KeyBinding` and adapter reflection, demonstrating cancellation of only synthetic combat intents without touching native physical input or peer inventory intents. Headless; no game or server starts.
- `tools/test_forge_wiring.py` requires exactly two early lifecycle wrapper calls and checks they remain between `host.worldPresence` and the unchanged independent movement/projectile/inventory reset call sites.

Full acceptance requires both exact-head Java17/21 Core and Linux/Windows Java8 Forge workflows, including Windows downloaded-artifact rehearsal. Real native Minecraft 1.8.9 gameplay, OpenGL warning pixels, physical input cadence and server effects **are not** validated. Keep draft/unmerged until original-client acceptance.
