# GhostC R2.72 — eliminate unchanged numeric profile writes

## Defect and fix

The existing `UiActions.fraction` invoked validated `previewFraction` and persisted unconditionally, even when the fraction selected the setting's current value. This can write profiles during repeated endpoints, clamped out-of-range fractions or constant numeric ranges without any changed state.

R2.72 now calls the existing persistence callback only if `previewFraction` returns true. It does not change numeric parsing, range validation, owner-thread checks, normal slider commits or the UI's keyboard/mouse action routing.

## Verification

- Java8 `UiTests.numericNoopWrites` checks unchanged fractions, repeated upper endpoints, clamped values, constant ranges, invalid inputs and exact persistence counts across 65,000 deterministic randomized fractions.
- Existing `UiTests.modelAndActions` still expects all actual changes to persist, including regular numeric adjustment.
- Required merge gates: exact PR-head Core Java17/21; Forge Linux/Windows JDK8 compile/reobfuscate, JAR verification and simulated profile install/rollback; equal tested and merged trees; successful post-merge workflows.

Native Minecraft behavior, graphics, FPS and long-session stability still require in-game acceptance.
