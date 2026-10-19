# GhostC R1.9 — movable feature HUDs with backward-compatible persistence

## Feature gap repaired

The R1.8 HUD editor allowed moving the Status overlay, but Keystrokes, CPS and Target HUD still drew at hardcoded locations on every frame. They could not be positioned independently or persisted across restarts.

## Changes

- A new pure-Java `HudPositions` model defines three independently movable widgets (`KEYSTROKES`, `CPS`, `TARGET`) with stable storage IDs and width/height contracts. Coordinates are fractions of available screen space, clamped in both editor and renderer, and remain valid across fullscreen or GUI-scale changes.
- `UiState.widgets()` exposes the model while the original `hudX()/hudY()/position()` behavior remains for the Status HUD.
- `UiSettingsStore` continues reading and writing the original **schema=1** and `hud.x` / `hud.y`. Six additional optional properties (`hud.keystrokes.x/y`, `hud.cps.x/y`, `hud.target.x/y`) provide independent positions. Legacy profiles with no new keys load default widget positions. All present values, including malformed nonfinite/out-of-range values, are prevalidated **before mutating any current UI preference**.
- The actual Forge Keystrokes, CPS and Target HUD renderers now compute screen coordinates from those same normalized positions. Their default positions remain near the lower-right or bottom-center, respectively.
- The real Forge F7 HUD editor displays four labeled draggable previews. Clicking a preview selects that specific item; moving updates only its own coordinates. The selected widget's normalized anchor persists on mouse release or GUI close. The original status drag remains functional.
- `HudPlacementTests` covers tens of thousands of deterministic screen sizes, drag boundaries, independent positions, status-compatibility, restart round-trips, schema-1 migration and rejection of corrupt new fields without partial mutation.
- Direct Forge integration tests now verify actual widget rendering, drag wiring and profile storage compatibility, rather than relying exclusively on headless policy classes.

## Automated and real-world acceptance

Core JDK17/21 regressions, Linux/Windows JDK8 Minecraft Forge 1.8.9 compilation/reobfuscation, full JAR integrity, and SHA256-gated Windows fake-profile install/collect/rollback are the required merge gates. Version metadata is **1.9.0**. The inventory gate covers 19 Java test classes and R08–R19 milestone docs; previous files must remain unchanged except for intentional edits.

The compiled mod is still **unverified in a running Minecraft GUI**. In the next single consolidated licensed local-world session, check dragging all four HUD elements, changing scale/resolution, restarting to confirm positions, rendering each module, and resetting/rolling back safely. Document observed evidence in GitHub Issues #2, #3, #5 and #6. See `docs/RUNTIME_R10.md` for installation and evidence collection.
