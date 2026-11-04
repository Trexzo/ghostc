# GhostC R3.81 — final native attack target and held-item revalidation

Parent: fully CI-passed R3.80 stacked draft PR #218 at `982c702c6e91f1984d5f4506127ae5ef888b67fa`. Scope is combat-module attack admission only. No launch, merge, release, installation, visual-track changes, keybinding change or new packet output.

## Verified source-level defect

`ForgeCombatController` chooses one AutoClicker / TriggerBot `ATTACK` intent, applies optional shared Hit Select and then executes Minecraft's original `KeyBinding.onTick` through the existing `NativePulseSettlement`. The final guard already validates exact session world/player identity, key mappings, physical attack/use state, and TriggerBot's crosshair identity.

Two final-output conditions depended on stale tick-start evidence:

1. AutoClicker's final `NativeAttackFrameGate.target` ran only if `host.clickEntityOnly()` was true. But Hit Select has its **own** `host.hitRequireTarget()` setting. With Clicker entity-only OFF and Hit Select require-target ON, a live target at Hit Select preview could change or disappear before native dispatch, yet the final Clicker branch would skip exact target identity revalidation.
2. TriggerBot's `triggerTargetValid(...,sword)` accepted the **tick-start** sword boolean both at acquisition and the final native branch. When TriggerBot required a sword, a hotbar/item change after acquisition could leave the original sword snapshot true despite a non-sword held item before dispatch.

## R3.81 patch

- Add pure `NativeAttackFrameGate.requireExactClickTarget(clickEntityOnly, hitSelectWasApplied, hitSelectRequiresTarget)`. The Clicker native output now requires an exact live target if either entity-only is enabled or enabled Hit Select preview actually required a target. It reuses the existing final `NativeAttackFrameGate.target` against the original crosshair object, current world registry, current health and dead flag. Hit Select **without** require-target preserves original behavior; disabled Hit Select does not turn on entity-only.
- Refactor TriggerBot's `triggerTargetValid` to read the current player-held `ItemStack` through `nativeSword(EntityPlayerSP)` at both initial acquisition and final native dispatch. Add pure `NativeAttackFrameGate.triggerSwordAtOutput(required, currentlySword)` to preserve explicit non-sword opt-out and verify current item, rather than stale `sword` captured at the beginning of END tick.
- The winner, shared ATTACK arbitration, Hit Select cooldown, physical input / binding checks, native partial-pulse quarantine and existing rejection paths are untouched. A rejected final gate cannot call `KeyBinding.onTick` or commit a Hit Select / TriggerBot cooldown.
- No new attack, delay, priority, item switch, packet, key remap or target prediction is introduced.

## Coverage and limitations

`NativeAttackOutputRevalidationTests` (Core) checks 90,000 seeded final-boundary combinations of Clicker entity-only, Hit Select enabled/require-target, changed native crosshair or reused entity registry identity, target death/zero HP, manual attack presses/releases, and initial vs current held sword with independent expected oracles. Exact cases verify a rejected Hit Select preview cannot commit a cooldown and a previously READY TriggerBot candidate cannot dispatch after live sword loss.

Mandatory Forge Java8 `NativeAttackOutputRevalidationTests` checks genuine Minecraft 1.8.9 `EntityPlayerSP.getHeldItem`, `ItemStack.getItem`, `KeyBinding.onTick`, the new native controller held-item probe and pure target/sword vetoes, without launching Minecraft.

Python wiring assertions enforce final Clicker target requirements, current held sword recheck, source test registration and Forge mandatory test; retain existing R3.70 checks.

Required: exact-head Core Java17/21 and Forge Linux/Windows Java8 compilation/reobfuscation plus Windows downloaded-artifact acceptance. Headless tests do not establish real Minecraft gameplay, PvP hits or server packet acknowledgment.
