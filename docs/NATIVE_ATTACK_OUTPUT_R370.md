# GhostC R3.70 — native combat output revalidation

**Parent:** R3.69 stacked draft PR #207 at `eab56c07c46ef2c21fb68209e117d83609afd23d`. Github-only combat branch; no main merge, visual changes or installation.

## Actual source-level issue

Before R3.70, `ForgeCombatController` calculated its physical `held` admission once near the beginning of an END tick, but AutoClicker's final `KeyBinding.onTick` gate did **not** verify that the same attack binding was still held at dispatch. A release, GUI/focus transition, world/player swap or key remap between the sample and output could therefore leave a stale eligible click request. TriggerBot checked live unheld input and the target but did not consistently verify the complete originating player/world/input-binding frame. Block Hit's final USE gate also inherited the earlier attack-held state.

## Implementation

- Capture original native attack and use keybinding numbers alongside the initial physical attack sample.
- Shared pure `NativeAttackFrameGate.sameFrame` requires same exact player and world object references, same attack/use binding values, player still present in the world registry, game focus and no GUI; reserved/unbound attack and overlapping attack/use mappings are vetoes.
- `attackState` requires the actual physical attack still held for Clicker and Block Hit, **still unheld** for TriggerBot, and the native use key unheld in both cases. Neither stage can reinterpret the initially sampled physical source.
- Entity-only Clicker and target-required Block Hit now demand a fresh exact crosshair/registry/health validation before the native key pulse; TriggerBot keeps its stronger native ray, reach, visibility, team and identity admission.
- Block Hit additionally checks that the use binding is not unbound/F7 and that the item still equipped at output is a sword. A denied pulse is not counted as an actual dispatch.
- No new packets, key presses, aim outputs, defaults, click rates, triggering cadence or input polling. The gate may *reject* stale already scheduled intent, but cannot produce a new action.

## Required verification

- `NativeAttackFrameGateTests`: exact transitions and 90,000 seeded frame/binding/manual use/target replacement traces against independent expectations.
- `NativeAttackOutputTests`: Java8 original Minecraft 1.8.9 `KeyBinding` getter, physical key-state and `World.getEntityByID` linkage. Forge `check` requires it; source wiring enforces all three final output gates.
- Exact-head Core Java17/21 and Forge Linux + Windows Java8 check/reobfuscation, plus Windows downloaded artifact acceptance rehearsal.
- This is offline/linkage evidence **only**; no launched Minecraft, real-time hardware event race, native network packet, target hit or server acknowledgement has been observed. Keep draft pending authorized real gameplay acceptance.
