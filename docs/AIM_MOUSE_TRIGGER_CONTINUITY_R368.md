# GhostC R3.68 — continuous manual-mouse precedence across TriggerBot handoffs

**Parent:** CI-verified R3.67 draft PR #205 at `799fdfe0aa7c79867a0cf7fcec3581a83e5a41ac`. Stacked combat-only GitHub draft. No main, visual, release or local installation changes.

## Confirmed failure in R3.67 Forge controller

`ForgeCombatController` checked `yieldToTrigger` and `aimRequireClick && !held` before it sampled `AimManualInputPolicy.suppressed`. Both branches invoked `manualAim.reset()`. When a physical mouse turn occurred during a valid TriggerBot crosshair handoff, no manual-mouse movement was recorded. Losing the crosshair target on a subsequent tick could immediately reenable Aim Assist despite configured `respect_mouse=true` and `mouse_pause_ticks` — an ownership discontinuity. A missing required attack hold could produce the same premature resume.

## Change

- When Aim Assist is enabled, read already-sampled native Minecraft `MouseHelper.deltaX/deltaY` and update the bounded owner-thread manual policy **first**, even during TriggerBot camera yield or no-required-click frames.
- When the physical mouse is still within its configured pause window, Aim Assist skips its camera write and clears stale target switch/hard-lock hints. Otherwise valid TriggerBot crosshair handoff still yields the camera, and missing required attack continues to veto the write.
- Do **not** reset the manual pause solely because TriggerBot owns the crosshair or the required attack key is unheld. Reset still occurs on disabled Aim Assist, disabled `respect_mouse`, focus/GUI/game reset, Panic, world/epoch reset, or policy failure. Missing MouseHelper while `respect_mouse` is enabled fails closed.
- No change to default `respect_mouse=false`, mouse threshold/pause timing, `switch_delay_ticks=0`, TriggerBot independent acquisition/cooldown, native click paths, projector ownership or camera writer. This adds neither packets nor new automated combat behavior.

## Tests and release gate

- `AimHandoffContinuityTests`: exact crosshair-loss and 4-tick physical mouse pause timeline, missing-required-click case, TriggerBot reacquisition/cooldown after target loss, world and owner-thread resets; 90,000 seeded mouse/target/manual-input traces against an independent oracle.
- `NativeAimHandoffTests`: genuine original Minecraft 1.8.9 `MouseHelper.deltaX/deltaY` and `KeyBinding` API linkage, plus headless timing and handoff cases on Java8 Forge.
- Mandatory `tools/test_forge_wiring.py` asserts native manual sample precedes mouse, TriggerBot, and required-click camera veto branches and forbids pause reset inside those early returns.
- Require exact-head Core Java17/21, Forge Linux/Windows Java8 check/reobf and Windows downloaded-artifact rehearsal before marking CI-verified. No headless check verifies actual native mouse event timing, gameplay aim feel, server ACK or live hit. Keep draft and test in authorized original Minecraft 1.8.9 before merge.
