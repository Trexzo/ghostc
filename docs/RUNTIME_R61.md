# GhostC R2.41 — continuous numeric slider dragging in ClickGUI

## Audited usability defect

In R2.40 the ClickGUI drew numeric settings as fill bars, but `mouseClicked` was the only input path that updated their values. `mouseClickMove` supported HUD-widget positioning only, so users could click a position but not hold the left mouse button and adjust a setting continuously.

Naïvely calling the existing `UiActions.fraction` on every mouse move would also write the entire persisted profile on each event. That is unnecessary disk I/O during a live pointer gesture and could repeatedly rotate recovery backups.

## R2.41 implementation

- Introduce Minecraft-independent `GuiSliderDrag` that captures the **exact stable module ID, numeric setting ID, mouse-down track left/width and scaled viewport dimensions**. Invalid geometry, nested capture and off-owner-thread calls are rejected. An active capture computes a clamped fraction from mouse X (including outside-track positions), using long arithmetic to avoid coordinate subtraction overflow.
- On a numeric slider's left-button press within its actual rendered bar, the GUI captures that exact setting and immediately previews the current fraction. Subsequent `mouseClickMove` events update the same setting continuously through `UiActions.previewFraction`; a scrolled row or different module cannot silently receive the gesture.
- `UiActions.previewFraction` validates and applies the same number range as the original click API, but does **not persist on every movement**. It returns whether a value really changed. The existing `fraction` method retains its persist-once behavior for programmatic/single-click callers.
- Mouse release applies the final pointer position and commits **one** profile save when the gesture changed a value. Inactive/unchanged captures require no new save.
- Clicking another control, scrolling, changing module/category, changing the viewport dimensions, entering a non-interactive small GUI, switching to HUD editor, closing the GUI, or losing the left-button drag cancels the capture and commits any pending change once. The new capture state is cleared before another setting can be selected.
- No Minecraft packet, game state, combat logic, default setting, keybind or profile serialization format changed. Real-time numeric preview is a GUI quality improvement, not a headless-rendered screenshot.

## Deterministic coverage and CI gates

- Java 8-compatible `GuiSliderDragTests`: owner confinement, invalid/no capture, release/cancellation and one-shot dirty commit; exact endpoints and out-of-bar clamping; negative and extreme int mouse coordinates without overflow; viewport mismatch; stable module/setting identity; a real `ModuleRegistry`, numeric `Setting.Number`, and `UiActions` with a counting persister proving live previews avoid writes and changed captures persist only once. Includes **16,000 randomized pointer gestures**, each with varying track width, viewport and pointer positions.
- Forge source regression verifies that the slider is captured only from the number's existing drawn hitbox; mouse movement previews values through the stable ID; release/close/navigation/scroll are commit fences; stale viewport captures are refused; profile persistence does not happen inside the move loop.
- Exact PR-head Core CI on Java 17 and 21 compiles all core and tests targeting Java 8, and executes the new regression. Linux/Windows JDK8 Forge CI recompiles/reobfuscates the actual mod, requires `GuiSliderDrag.class` in the JAR and reruns SHA/provenance plus simulated Windows prepare/collect/rollback. Verify the exact merged `main` commit separately before declaring the new baseline fully CI-certified.

**Still unverified:** live Minecraft 1.8.9 GUI pointer behavior, visual correctness at unusual scale, actual performance and 60-minute soak. G1/G2/G4/G5 in-game acceptance gates stay open.
