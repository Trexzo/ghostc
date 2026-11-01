# GhostC R3.78 — WTap to Jump Reset damage continuity

Parent: fully CI-verified R3.77 draft PR #215 at `6d15b434d427ab2bd5bc4610faee3f6673d2c138`. Scope: combat-module coordination only. The separate visual branch, main, releases, installations, default toggles, WTap sprint timing and native attack output are untouched.

## Source-audited interaction flaw

`ForgeEventAdapter` captures WTap's reservation at the **start** of each END tick and `ModuleInteractionStateMachine` correctly denies Jump Reset for that full frame, including a frame in which WTap restores sprint. Before R3.78, that denial always called `jumpReset.reset()`. Consequently the Jump Reset policy discarded its previous `lastCommitted` cooldown and `previousHurt` baseline every time WTap held the resource. A fresh hit immediately after WTap released the resource could bypass a cooldown previously committed by Jump Reset. The previous reset also prevented a continuous record of hurtTime transitions during WTap ownership.

## Change

- Add `ForgeJumpResetController.observeWTapReservation()`. This **read-only owner-thread** path checks the enabled module, connected session, active game window and exact native local-player world registry identity. It invokes `JumpResetPolicy.preview(epoch,tick,hurtTime,false,minHurt,cooldown)` through the existing feature fault boundary. `false` explicitly forbids a native jump, but preserves the prior committed cooldown and consumes the actual hurt-time edge. No `EntityPlayerSP.jump()`, KeyBinding, motion, sprint or packet write is possible in this observer.
- `ForgeEventAdapter` now distinguishes a denied `JUMP_RESET` grant caused specifically by `WTAP_RESERVED` from all other denial reasons. Only WTap reservation uses the observer. GUI/world/focus/disabled denial retains the previous hard reset. The observation is performed even if WTap releases sprint later in that END tick, because the original arbitration frame governs ownership.
- The existing R3.77 Velocity vertical-motion handoff runs unchanged when Jump Reset is granted. WTap continues to preempt AutoSprint/Jump Reset/AutoRod/AutoSnowball for exactly the original reservation window.
- Exception handling is unchanged and fail-closed. A stale unregistered player causes a hard reset, not a synthetic damaged-player observation. No new native output is added.

## Evidence

`WTapJumpContinuityTests` exercises a real WTap attack/reservation and RESET/HOLD/RESTORE cycle, a prior committed Jump Reset cooldown, a fresh hurt edge during WTap reservation, the restore tick, a new eligible edge still inside the *original* cooldown, boundary permission, same-tick duplication, GUI/epoch reset and foreign-thread guard. It also runs 90,000 deterministic randomized ticks with an independent cooldown/damage baseline model, WTap reservation state, window/GUI denial, manual input and R3.77 vertical admission.

`NativeWTapJumpContinuityTests` is a mandatory original Forge 1.8.9 Java8 headless linkage probe for the new observer, preserved Velocity input signature, vanilla `EntityPlayerSP.jump()` and Forge `AttackEntityEvent` plus its pure native timing policy. `tools/test_forge_wiring.py` asserts that the WTap observer contains no native jump/motion/KeyBinding action and is only selected for the `WTAP_RESERVED` denial.

Require exact-head Core Java17/21 and Forge Linux/Windows Java8 compilation/reobfuscation and downloaded-artifact acceptance. These tests are **not** proof of real Minecraft gameplay, combat physics, native runtime input or server hit acknowledgement. Keep draft and do not merge into main.
