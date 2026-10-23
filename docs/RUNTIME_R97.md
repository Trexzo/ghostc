# GhostC R2.77 — HUD editor capture and idempotent persistence

## Source-level risks
HUD positioning previously wrote UI preferences on any mouse release or
keyboard nudge/reset, even when the final anchor was unchanged.
More seriously, resizing the GUI into a non-interactive viewport or
selecting another HUD tab could discard an active HUD capture without
calling its save path. Keyboard nudges also recomputed both normalized
coordinates from rounded pixels, potentially drifting an untouched axis.

## Changes
- Introduce owner-thread-only `HudDragCapture` storing the selected target,
  initial *serialized* normalized X/Y and original scaled viewport.
  A gesture commits iff its final exact coordinate differs from mouse-down.
  It cannot be committed twice.
- Wire native HUD drag to commit on release, resize/non-interactive
  viewport, next mouse click/tab selection and GUI closure.
  A normal release incorporates the final pointer position, matching
  slider capture semantics. On GUI close, avoid the duplicate drag write
  while preserving the ordinary close-time checkpoint when not dragging.
- Add changed-aware reset/nudge helpers. Keyboard controls persist only
  a real anchor change. Repeated default resets, zero motion or nudges
  beyond a screen boundary do not quantize or save.
  A horizontal nudge retains the exact serialized Y and vice versa.
- Preserve legacy void `reset`/`nudge` APIs for external callers,
  existing normalized positions and unchanged Forge input policy.

## CI and limitations
Java8 tests cover individual Status/Widget drag/return/cancel behavior,
owner-thread isolation and 80,000 seeded multi-move capture sessions
with an independent exact-change oracle, plus existing tab/footprint
and nudge tests. Forge source-wiring asserts all exit paths and the
new JAR verifier checks capture bytecode inclusion. Require exact-head
Core Java17/21 and Forge Linux/Windows JDK8 build and simulated
installation/rollback, tested-vs-merged tree equivalence and
post-merge workflows before certifying.

Actual Minecraft HUD dragging, visual fidelity, performance and
long-session stability require a later in-game acceptance check.
