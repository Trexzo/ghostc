# GhostC R3.00 — Ctrl+F after keybind capture

## Source-level failure

GhostClickGui's R2.98 priority rule correctly sends an already-held
Ctrl+F key event to search before processing an active module keybind.
However, on a normal keyboard Ctrl is pressed **before** F. Minecraft's
GuiScreen.keyTyped receives Ctrl key-down first. Previously that event
immediately completed the pending keybind, assigned Ctrl to the module
and cleared capture. The later F could then focus search, but only after
an unwanted module binding was saved.

## Behavior

When keybind capture is armed, defer LCONTROL/RCONTROL assignment on
key-down without saving. A matching Ctrl **release** observed via
GuiScreen.handleKeyboardInput commits Ctrl alone through the existing
UiActions.bind validation and persistence. Ctrl+F while Ctrl remains
held cancels the pending assignment before the normal search focus path.
Any other ordinary key commits through the existing capture action and
cancels the pending Ctrl; key repeat does not cause duplicate binds.

Pointer focus changes, explicit module selection, viewport invalidation,
switching to HUD and GUI closure discard the deferred state. Both left
and right Ctrl remain bindable on release. Reserved F7, duplicate-key
validation, Escape cancellation, mouse button capture and all other
module activation policies remain unchanged.

## Regression

GuiKeybindCaptureTests.controlChordAndRelease replays 150,000 seeded
ownership/release scenarios plus explicit Ctrl-down → F and Ctrl-only
sequences through the pure Java policy and UiActions persistence gateway.
Forge source wiring verifies the mouse, search, keyTyped, and
handleKeyboardInput release order. The Windows JDK8 Forge compile
ensures the GuiScreen keyboard override is valid in the actual
Minecraft 1.8.9 source interface.

No native Minecraft test has been run: the CI coverage validates source,
bytecode/JAR packaging and synthetic focus policies, not real input timing.
