# GhostC R2.2 — measured Status/Metrics layout and fault isolation

## Defect corrected

The Forge overlay used only the Status label's width and height to determine position, even if the optional FPS/XYZ Metrics text was much wider. With Status disabled, the Metrics overlay still reserved an invisible Status-sized footprint; the content could leave the display. Unlike other feature HUDs, the entire Status/Metrics renderer ran as one unguarded callback.

## Implementation

- New pure Java `StatusHudLayout` measures the **combined** actual text footprint in Minecraft scaled pixels. It shares one normalized anchor for Status/FPS/XYZ, including when Status is disabled. Oversized content is clipped to the viewport, with Forge text-width trimming and offscreen row checks.
- Actual `ForgeEventAdapter.onOverlay` now calculates font widths and runs `hud.status` and `hud.metrics` callbacks through separate `HostRuntime.runFeature` guards and separate OpenGL state scopes. A standard Java RuntimeException disables only the failing feature, leaving the other one active.
- The F7 HUD editor's Status preview and nudge footprint now measure the same combined Status/Metrics contents, including dynamic FPS/XYZ line width and optional rows. When both are disabled, the Status anchor is still editable in the preview.
- Sanitized fault/config warnings use a bounded position and trimmed display text. Existing preferences, schema 1, all four normalized HUD anchors, movement/gameplay logic and module IDs are retained.

## Verification and remaining limitations

`StatusHudLayoutTests` checks measured row widths/heights, metrics-only status-disabled mode, extreme small viewports, 60,000 seeded screen and text combinations, and independent Status/Metrics exception quarantine. Direct Forge source wiring tests require the actual renderer and editor to invoke the shared geometry and fault gates. Java17/21 Core CI, Linux/Windows JDK8 Forge1.8.9 compilation/reobfuscation, JAR-integrity validation and SHA-256 safe fake-profile Windows installer/rollback tests are required.

R2.2 releases version **2.2.0**, preserving all prior files and requiring 22 regression suites and 15 milestone guides (R08–R22). No real licensed Minecraft game process is run in GitHub Actions. Live on-screen text, mouse operations, Minecraft transitions and long soak/FPS remain unverified until the consolidated local runtime test. See `docs/RUNTIME_R10.md` and existing Issues #2, #3, #5, #6.
