# GhostC R3.76 — WTap native event admission and one-write sprint cadence

Parent: fully CI-verified R3.75 draft PR #213 (commit `4ae8a228b65a21ff5a3cbe54f3b410d374ef4d28`). GitHub-only module-behavior branch; no merge, release, installation or parallel visual changes.

## Audit of the module interaction

WTap stores a real Forge `AttackEntityEvent` and schedules native sprint off the following client tick, with AutoSprint preempted by the existing `ModuleInteractionStateMachine`. The existing policy can return `RESET` on the first call of a tick and `HOLD` on a second call of that same tick; the Forge adapter would run `EntityPlayerSP.setSprinting(false)` again. The attack listener previously had default Forge priority and no explicit canceled-event check, so an earlier listener's canceled event could still be considered by an inappropriate callback path.

## R3.76 implementation

- `WTapPolicy` tracks one owner-thread `lastDecisionTick` per session; identical-tick `RESET`, `HOLD` and `RESTORE` decisions are never repeated. The original next-tick reset / 1–3-tick hold / restore cadence is unchanged.
- An ineligible or disabled call still **immediately cancels** its pending WTap reservation, even if it arrives after another callback within the same tick. A same-tick reenable does not replay old output.
- `ForgeEventAdapter.onAttackEntity` now listens at `EventPriority.LOWEST`, excludes canceled native events via `receiveCanceled=false` and `event.isCanceled()`, and keeps exact native local-player and world-identity admission. No fabricated KeyBinding, packet or server hit evidence is introduced. Events canceled by a listener running after this callback still cannot be ruled out; these are client-side only checks.
- AutoSprint/WTap sprint resource ownership and the existing velocity/jump-reset arbitration remain unchanged; only duplicate sprint-state writes and already-canceled attack admission are corrected. User timing settings and default enablement remain untouched.

## Evidence and limitations

`WTapNativeCadenceTests` verifies exact WTap/AutoSprint reservation transitions, invalidated same-tick movement ownership, Panic/session resets and 80,000 seeded tick/output/attack traces against an independent oracle. Original `WTapTests` updated to assert duplicate RESET produces `NONE`. Mandatory Forge Java8 `NativeWTapAdmissionTests` checks original Forge 1.8.9 `AttackEntityEvent` cancelable annotation, actual handler priority/receiveCanceled metadata, target field and original Minecraft sprint setter linkage; also checks WTap policy next-tick outputs. Source wiring asserts the controller and state-machine integration.

Require exact-head Core Java17/21 and Forge Windows/Linux Java8/reobf + downloaded-artifact rehearsal. Native probes are headless, not actual Minecraft 1.8.9 gameplay, sprint physics, packets or server-hit verification.
