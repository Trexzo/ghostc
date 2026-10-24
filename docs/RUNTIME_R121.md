# GhostC R3.01 — ClickGUI setting focus must stay visible

## Source-level bug

ClickGUI can assign keyboard focus to a typed setting through Tab or a
row click. Mouse wheel scrolling then updates settingScroll without clearing
keyboardSetting. The selected row can move entirely outside the drawn
settings viewport while its invisible focus remains live. The next arrow,
Home/End, Enter, Space or R event still edits or resets that now-hidden
setting and persists the result without an on-screen selection indicator.

Similarly, a scaled window/layout resize can reduce visible setting rows
while preserving focus on a formerly painted row.

## Change

GuiSettingKeyboard.visibleFocus is a pure, constant-time focus-policy
boundary. When the focused row no longer belongs to the clamped visible
range, it returns -1; otherwise it keeps the same stable settings index.
The Forge ClickGUI enforces this after manual settings wheel scroll,
during module-settings draw after resolving visible rows, and immediately
before any keyboard setting mutation. It NEVER scrolls back against the
user's wheel direction to keep a stale row highlighted. Explicit Tab/Shift
Tab still uses existing reveal and is the documented way to re-focus an
off-screen setting; direct row clicks also restore focus.

Module list selection, keybind editor, search, slider dragging and saved
profile formats remain unchanged.

## Regression

GuiSettingKeyboardTests.manualScrollFocusSafety checks visible and invisible
row boundaries, overscroll, zero rows, and 125,000 deterministic randomized
count/viewport/scroll/focus combinations. It also verifies that a hidden
focus is never silently revived on scroll-back and cannot mutate typed
settings/persist profile, while explicitly re-established focus can.
Python Forge wiring pins the wheel, draw and keyboard-action focus guards.

Only exact-head Core Java17/21 and Forge Linux/Windows JDK8 CI,
tested/merged Git tree equality and passing post-merge workflows certify
the changes. Native Minecraft GUI acceptance is not claimed.
