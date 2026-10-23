# GhostC R2.75 — final-value slider capture persistence

## Issue and change
The previous `GuiSliderDrag.changed()` latched dirty permanently during
each capture. Dragging a numeric setting away from, and back to, its
initial value still caused a profile write on release, even though the
final serialized value matched the original.

R2.75 captures the actual numeric value at mouse-down and observes the
applied value after each successful `UiActions.previewFraction` call.
Dirty state now equals whether the last successfully applied numeric value
differs from the starting value. A return to baseline clears dirty.
Values are compared with `Double.compare`, preserving signed-zero
semantics. Nonfinite initial and observed values are rejected before use.

The existing begin/changed overload is retained for legacy pure capture
consumers and tests; native Forge sliders use the baseline-aware API.
Owner-thread and viewport guards remain active, and numeric mutations
still go through the typed UiActions gateway rather than direct writes.
Successful aborted/interrupted drags that end at a different value still
commit once. No file I/O is introduced on pointer movement.

## Tests and acceptance limits
Java8 headless coverage explicitly verifies revert-to-baseline,
real-change commit, interrupted dirty commit, nonfinite input and revoked
capture, plus 75,000 seeded multi-move sessions with an independent
initial/final oracle. Existing pointer geometry and profile regressions
remain in Core CI. Forge wiring tests enforce baseline capture and
observations against the final applied numeric value.

Exact PR-head Java17/21 Core and Forge Linux/Windows JDK8 build,
reobfuscation, JAR verification and simulated installation/rollback
must succeed before merge. After merging, source-tree identity and
postmerge Core/Forge CI must be verified. Real in-game drag visuals,
mouse behavior and FPS remain untested.
