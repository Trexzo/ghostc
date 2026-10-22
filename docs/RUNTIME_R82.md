# GhostC R2.62 — opt-in Target HUD live distance

## Motivation

The existing Target HUD supports nearest/crosshair selection, values/percent health, and classic/glass palettes. It shows identity and health but does not show how far the selected player is from the local player.

## New optional setting

- Module `hud.target` adds a persisted Bool `show_distance` (default **false**) editable through the existing F7 typed-settings GUI.
- When enabled and a live eligible target passes the normal final health/range/visibility gates, the Forge HUD draws a distance in Minecraft **blocks**. The displayed value is computed from the target's and local player's **current render-frame positions**, not the tick snapshot's old position.
- Use the original fixed 160px card footprint. With HP display enabled, the distance is shown on a lower row beneath the existing health bar (`y+33`); without HP, it is shown below the player name (`y+18`). Font-height admission prevents drawing beyond the card.
- The pure Java8 `TargetHudDistanceText` validates finite coordinates and maximum range, formats with `Locale.ROOT`, measures font pixels, uses `5.0 blocks` when it fits, otherwise `5.0b`, otherwise returns no text. No input/packet/gameplay changes.
- When disabled (including legacy profiles) the previous geometry, palette, texts and draw paths are unchanged. Distance is optional in both Nearest and Crosshair selection modes.

## Verification

- `TargetHudDistanceTextTests`: numeric boundaries, overflow/invalids, pixel budget fallback and 100,000 deterministic random positions; profile default, save/reload and toggle-off tests.
- Forge wiring assertions require opt-in gating, live-world position use, font/height clipping, setting registration and JAR class entry.
- Gate: exact PR head Core Java17/21 and Forge Linux/Windows JDK8 compilation/reobfuscation, JAR provenance and fake profile transactional installation/rollback, tested-to-merged tree SHA parity and post-merge CI on exact main.

Actual in-game GUI/HUD rendering, FPS, multi-mod compatibility and extended stability remain unverified by CI.
