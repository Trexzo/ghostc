# GhostC R3.45 — native action outcomes and cross-module evidence

**Stacked Draft on R3.44** `5671fb05aa10a5028fa65cfa58114bcdc4067554` (PR #177). Existing independent visual PR #164, `main`, and every parent PR remain unchanged.

## Why these counters differ from R3.44

R3.44 showed **permissions and denials**. A granted click or sprint resource is not proof that the controller made a real input change. R3.45 instruments the **actual Forge controller methods** at their vanilla click/key/sprint API boundaries. This makes it possible to separate:

- **ATTEMPT:** an original Minecraft `playerController.windowClick(...)` returned without throwing, or the WTap policy admitted an actual native attack event. This does **not** mean the server accepted an action.
- **LOCAL_CHANGE:** on a subsequent owner tick, an inventory source (Chest Stealer) or both source and target (Inventory Manager) slots changed, or AutoArmor observed the separate armor removal/equip phase. This is **local observation only**, not a server transaction ACK.
- **TIMEOUT:** a previously attempted inventory transfer produced no required local slot change within the controller's existing 12-client-tick deadline. The original fail-closed policy still blocks the affected GUI session.
- **CANCELLED:** a pending action was interrupted by GUI closure, manual input, module preemption, world/focus change, or Panic before its local resolution. Never replays a request.
- **REJECTED:** a native preflight/candidate invariant or final combat action gate rejected the proposed action before performing an API call. It may also close a pending action when an expected pinned armor candidate changed.
- **DISPATCHED:** a native `KeyBinding.onTick` AutoClicker/BlockHit pulse or WTap `EntityPlayerSP.setSprinting` API invocation was executed. A dispatched click **is not evidence of a successful attack or hit**.
- **LOCAL_COMPLETE:** WTap completed its observed sprint-reset/restore sequence locally. Does not prove remote server sprint/knockback handling.

The outcome ledger `ModuleActionTelemetry` is owner-thread and has **six resource lanes**: `CHEST`, `SORTER`, `ARMOR`, `WTAP`, `CLICKER`, `BLOCKHIT`. Each pending lane can end **only once** via a local change, local completion, timeout, cancellation or rejection; repeated callbacks cannot inflate completion counts. The existing action coordinator and the R3.43 cross-module state machine still make every actual gameplay decision; R3.45 **observes**, never grants new authority or changes packets.

## What is wired in

- **Chest Stealer:** attempted vanilla shift-click, source-slot change, 12-tick timeout, manual/world/Panic cancellation, and preflight rejection.
- **Inventory Manager:** attempted vanilla mode-2 hotbar swap, BOTH source and destination slot changes, timeout, cancellation, and rejected malformed source/target.
- **AutoArmor:** every real vanilla quick-move attempt including the **separate REMOVE and EQUIP stages**, each with local observation or timeout, cancelled/rejected pinned candidate handling. The original two-phase safety and empty-storage checks remain mandatory.
- **WTap:** native attack-policy admission, sprint OFF/ON API dispatch and local lifecycle completion; cancel on eligibility loss/focus/Panic.
- **AutoClicker / BlockHit:** actual vanilla keybinding pulse dispatched, or emitted coordinator action rejected by the last native gate; never fake server hit confirmations.

All calls pass through `HostRuntime.nativeAction(Lane,Event)`, which checks existing **F7 Runtime Diagnostics** opt-in. With diagnostics disabled, no action telemetry is recorded. It uses only a fixed enum vocabulary, and all counters saturate at `Long.MAX_VALUE` via the existing diagnostics class.

## UI/report and privacy

With F7 **Runtime Diagnostics enabled**, an additional bottom-left HUD line displays the latest classified native event, such as `Action: CHEST LOCAL_CHANGE` or `Action: CLICKER DISPATCHED`, beneath the R3.44 state ownership/denial lines. It is a *last recorded event*, not a live server confirmation. No module label can contain dynamic player names, coordinates, server IP, chat, item names, packet contents or inventories.

Existing anonymous `diagnostics-last.properties` `schema=1` reports gain a fixed set of `count.action_<lane>_<event>` integer keys. Backups from pre-R3.45 remain valid even when all new `ACTION_` counters are missing; original mandatory fields, unknown-key rejection, file-size bounds and symlink protection remain intact. **No sensitive values are added**. Existing `diagnostics-last.properties.bak` may be rotated safely.

## Tests and real-game limitations

`ModuleActionTelemetryTests` simulates **60,000 seeded independent pending-lane events** with an exact oracle, checks double-completion rejection, owner-thread isolation, null inputs, immutable snapshots, and F7 opt-in persistence with older anonymous schema-1 backups. It is invoked through `IntegratedModuleCatalogTests` alongside all R3.42 native Netty S12/S27, R3.43 cross-module state-machine, R3.44 denial, old diagnostics backup safety, and existing combat/inventory tests.

The Forge 1.8.9 Linux/Windows JDK8 build verifies all native controller instrumentation compiles and the JAR can be installed into a fake profile with transactional rollback. **It does not launch original Minecraft or prove server acceptance.** Native 1.8.9 acceptance must still check server transaction rejections, delayed inventory corrections, actual WTap packets, real combat key dispatch behavior, GUI/Panic ownership transitions and HUD positioning. Failed local checks remain diagnostics evidence rather than automatic resends.

Future extension: controlled client-side receive/send packet acknowledgements with exact session ownership, and observed target hurt outcomes, only after real original Minecraft acceptance verifies present hooks. No unverified remote-hit success claims.
