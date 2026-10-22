# GhostC R2.64 — keyboard-only module keybind capture

## User-visible gap

After R2.55 module keyboard selection and R2.59 setting-row editing, the existing ClickGUI still required a mouse click to start a module's keybind capture. The screen already supported Escape cancellation, Delete/Backspace unbind, typed validation and profile persistence once capture was active.

## Behavior

- Open F7 ClickGUI, select a module with Up/Down, press **B** while the module list owns focus. The selected module enters the **same** binding prompt as clicking its keybind row.
- Release B and press the desired new key. Escape cancels without mutations; Delete/Backspace assigns unbound code 0. F7 remains reserved and closes the GUI rather than ever becoming a module bind. Duplicate keys cannot silently steal another module's keybind.
- When search has focus, B is entered into the search query instead of capturing. When a setting row is focused, B does not override its keyboard editing. HUD editor and keybind capture already active take priority.
- The B key must be an actual non-repeat key-down. While capture is active, LWJGL-generated repeated key events are ignored instead of immediately binding a held-down B or other repeating key.
- The existing `UiActions.bind` owner-thread checked, one-save-per-success path remains authoritative. Capturing does not create or persist settings, and changes neither game packets nor input outside the ClickGUI.
- The keybind row shows **KEYBIND [B]** as a discoverable shortcut.

## Tests and merge gates

- Pure Java8 `GuiKeybindCapture` focus predicate and `GuiKeybindCaptureTests`: 140,000 seeded focus states, successful capture, duplicate/reserved key rejection, Backspace clearing, owner-thread refusal and persistence count.
- Forge source wiring tests enforce capture priority, repeat suppression, search before shortcut, module-focus gating, existing keybind validation and visible hint.
- Exact PR-head Core CI Java 17/21, Forge Linux/Windows JDK8 builds/JAR/transactional install rollback, tree identity after merge and exact post-merge CI on main.

Native Minecraft keyboard and GUI interaction, appearance, compatibility, performance and extended stability remain unverified until in-game acceptance.
