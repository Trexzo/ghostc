# GhostC R2.98 — ClickGUI keybind focus transfer

## Reproduction from source

GhostClickGui keybind capture accepts keys before search-field routing.
A user may arm module key capture with B or a click on KEYBIND, then
click search or another GUI control. The former mouseClicked handler did
not clear capture on pointer focus transfer. As a result, the next typed
character was assigned to the previous module instead of reaching search,
potentially modifying and persisting an unexpected module keybind.

Ctrl+F was similarly handled AFTER keybind capture in keyTyped, so
Ctrl+F while armed bound F instead of focusing search. This behavior
contradicted the GUI's explicit Ctrl+F search navigation.

## Fix

Before processing any mouse button event, cancel pending keybind capture.
A click directly on KEYBIND explicitly arms a new capture later in that
same handler; every other GUI click stays in normal search/category/module/
setting focus. Before the keyboard capture branch, recognize Ctrl+F on
the modules page as an explicit search focus transfer. All other ordinary
key presses, including F without Ctrl, still use existing validated
UiActions.bind. Escape cancels, F7 closes, held-key repeats remain blocked,
and binding duplicates/reserved keys remain rejected.

## Verification

GuiKeybindCaptureTests.explicitSearchTransfer verifies the complete
four-boolean focus matrix over 160,000 seeded cases and asserts
search ownership does not persist any keybind while plain F capture does.
Python Forge-wiring tests enforce pointer-capture clearing before
GuiScreen mouse dispatch and before the bind-row re-arm, and keyboard
transfer before capture dispatch. Existing GUI input/feature tests and
JDK8 Forge compilation remain required.

This is source-level/CI acceptance only; no claim of visual or native
Minecraft 1.8.9 runtime verification.
