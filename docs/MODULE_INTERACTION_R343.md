# GhostC R3.43 — Cross-module action/state arbitration

**Draft on R3.42** (exact head `4fe8f4f7b6e8c2f0958995af08bd61d396c01da1`). The other chat owns the visual PR #164. No change to `main` or parent PRs.

## Why another coordinator?

The existing `SessionServices.actions()` and `SessionServices.rotations()` already arbitrate **combat requests**. R3.43 does not replace them or invent duplicate player attacks. It adds a single owner-thread `ModuleInteractionStateMachine` to arbitrate **resource ownership across module families**. A module can be enabled in F7 yet be denied a native action when the context/other owner does not allow it.

The important design is **domain ownership**, not a single massive monolithic combat state. The state machine grants a fresh immutable plan on every native client tick, fenced by `SessionServices.generation()` and `SessionServices.tick()`. Render-only module behavior remains independent.

| Native domain | Allowed state | Owner and priority |
|---|---|---|
| Combat input and camera | Connected live game, no GUI, OS + Minecraft focus | Existing `ForgeCombatController` and `ActionCoordinator`/RotationCoordinator; no duplicate combat pipeline |
| Auto Sprint and WTap | Connected live game, no GUI, focus | **WTap** exclusively preempts AutoSprint only while a **real observed attack** has an outstanding reset/hold/restore. Original attack tick is unchanged; AutoSprint resumes only after WTap completes |
| Bridge Assist | Connected live game, no GUI, focus | Bridge controller owns synthetic Sneak; physical Sneak always overrides it |
| Velocity packet/local-motion | Connected live game, no GUI, focus | Velocity controller owns exactly one of legacy hurt or packet mode; previous Netty channel/identity fences remain |
| Player inventory clicks | Real player inventory GUI + active OS window | **AutoArmor first; Inventory Manager only if AutoArmor disabled**. Both may be enabled, but only one may write |
| Chest clicks | Real chest GUI + active OS window | Chest Stealer only, never alongside the two player inventory controllers |
| Other GUI, world/connection loss, focus lost or F6 Panic | None | All gameplay input and inventory write permissions revoked; controllers reset/pending input released |

### Existing native behavior preserved

The `ForgeEventAdapter` still publishes one exact-world `WorldSnapshot`, calls native controllers in the existing order and respects the existing independent module-specific physical, native-entity, and container checks. New gate output does **not** bypass `host.runFeature`, click confirmation, bridge Sneak ownership, native identity verification, Forge event filters, vanilla inventory actions or network packet forwarding.

The tick order is now:

1. Host lifecycle reconciliation + one snapshot publication.
2. One `ModuleInteractionStateMachine.advance(epoch,tick,world,scene,focus,modules,wtap.pending())` call.
3. `features.tick(plan.autoSprint)`: only while AutoSprint holds sprint resource. An active WTap reset prevents a conflicting sprint enable before WTap's native callback.
4. Combat, Bridge Assist, Velocity and WTap receive their grants; otherwise their owned state is reset.
5. Exactly one of Chest Stealer, Inventory Manager, or AutoArmor may tick; all other inventory controllers reset.

World generation switches and F6 Panic also `moduleState.reset()`. UI-only screens and inactive OS windows receive no native game-action plan. WTap's new `pending()` method is owner-thread checked and read before the next feature tick; no new packet injection or forced native sprint toggles.

### Why this is a state machine and not just booleans

`Phase`: `INACTIVE`, `GAMEPLAY`, `PLAYER_ARMOR`, `PLAYER_SORT`, `CHEST_TRANSFER`, `UI_PAUSED`, plus separate resource owners `SprintOwner` and `InventoryOwner`. It remembers exact session generation, monotonic tick and previous phase; `transitioned` reports ownership changes. Previous-session grants cannot survive world transitions or Panic.

A single phase cannot completely model all concurrent modules: combat + edge Sneak + packet Velocity must run **together** during gameplay, while inventory clicks must remain exclusive. Domain-specific owners solve both problems without serializing unrelated actions.

### Verification

`ModuleInteractionTests`: >65k seeded frame inputs with at least 700k assertions verifying focus, world/epoch transitions, F6 reset, all-enable combinations, strict inventory exclusivity, no AutoSprint during real `WTapPolicy` sprint hold, independent combat/Velocity/Bridge access and correct resumption. The full test is invoked through existing `IntegratedModuleCatalogTests`, preserving Core Java17/21 CI, parent combat/visual/WTap/inventory suites, and the native Forge R3.42 S12/S27 Netty probe.

Forge Linux/Windows JDK8 compile/reobfuscate + download/install rehearsal still verify **packaging and native library compatibility**, not actual original Minecraft gameplay. Real user-owned Minecraft 1.8.9 testing must confirm physical input, key state, sprint packets, dropped focus, two-stage armor upgrading, chest transfers, WTap/AutoSprint coexistence, Bridge Assist, S12/S27 and Panic. Keep Draft until that evidence exists.
