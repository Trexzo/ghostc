# GhostC R0.3 — ClickGUI / HUD acceptance

## Implementation
- F7: open an original rendered client-side GUI; ESC: close.
- Modules tab: select categories; search by module name or stable ID; left-click a row to inspect settings, click the right end to toggle.
- Setting rows: click Boolean to toggle; click Choice to cycle; click Number bar to position within the validated range.
- Click KEYBIND to capture a key. ESC cancels, BACKSPACE/DELETE unbinds, F7 is reserved and conflicts are refused.
- HUD tab: drag the status preview to choose resolution-independent placement (normalized coordinates).
- Two layout sizes (wide/compact), two palettes (indigo/graphite), saved across restarts.
- HUD status scale, style, shadow, FPS and XYZ telemetry settings.
- Module/profile preferences in `.minecraft/config/ghostc/default.properties`; UI/HUD preferences in `ui.properties`; both use staged validation, temp-file replacement and backup copies.

## Architecture
- `dev.ghostfusion.ui`: headless UI state, settings action gateway and preferences store. No dependencies on Minecraft/Forge.
- `GhostClickGui`: Forge 1.8.9 GuiScreen, only handles input/draw; data mutation routed through `UiActions`.
- `ForgeEventAdapter`: F7 key integration, world status and Minecraft-dependent metrics.
- `RenderStateGuard`: GL11 attribute and matrix push/pop with try/finally to restore renderer state even on exceptions.
- **No original Vape, Slinky or Onyx source, binary or artwork copied.**

## Verification
Offline CI can verify filter/category, settings, profiles, read/write, resolution clamps, toggle/clock boundaries. Forge CI can verify source compilation and reobfuscated JAR structure. **Neither proves ClickGUI rendering fidelity or in-game behavior.** Visual appearance, mouse interactions, OpenGL state and aspect-ratio screenshots must be verified in a real licensed Minecraft 1.8.9 Forge 11.15.1.2318 instance before the GUI acceptance gate is closed.

## Manual in-game acceptance (pending)
1. Open a world; F7 should show the menu; ESC returns to gameplay without leaving the key held.
2. Search for "status" and "metrics"; change category; toggle metrics, and change FPS/XYZ settings.
3. Toggle the status module via GUI and via F8; no double-toggle after key repeat or alt-tab.
4. Toggle graphite/indigo and wide/compact; close and reopen and verify persistence.
5. Drag the HUD preview to edges; observe actual HUD constrained to screen on 854x480, 1280x720 and 1920x1080 desktop windows, and at Minecraft GUI scale 1–3.
6. Rejoin worlds and restart; verify settings persist and OpenGL does not leak state into vanilla HUD.
7. Capture screenshots, exact versions, `latest.log`, measured FPS and any glitches on Issue #3.

CI artifact name ends in `unverified-ingame` for a reason.
