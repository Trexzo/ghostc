# GhostC R2.26 — configurable camera-facing ESP health bar

## Feature gap identified

The optional `show_health` gauge introduced earlier is a first-party camera-facing vertical HP line alongside the player's world-space model, with dark full-health background and red-to-green bottom-up fill. Prior to R2.26, placement was hardcoded to camera-relative Right at a `width / 2 + 0.18` world-unit offset. This works but cannot be repositioned to suit different ESP styles, outlines or crowded player scenes.

## Production changes

- Add two typed, persistent `render.esp` settings available through the existing searchable/scrollable ClickGUI: `health_side` is a choice of `right` or `left`, default **right**; `health_gap` is a numeric setting from **0.05** to **0.50** world units, default **0.18**. Existing profiles missing either value retain the exact original right-side geometry and distance.
- Pure Java8 `EspHealthBarGeometry.Side` explicitly resolves the two side IDs and rejects unknown values. The original public `EspHealthBarGeometry.emit(x,y,z,height,width,viewerX,viewerZ,current,maximum,sink)` remains in place and calls `emitStyled(...,Side.RIGHT,0.18,sink)`. Default source-visible vertex values are mathematically identical to the previous release.
- The new `emitStyled` validates the original height, width, camera and HP inputs plus the side, gap and finite world-space GL endpoints before emitting any line. Left/Right use opposite XZ offsets perpendicular to the viewer-player bearing; their background/fill vertical positions, fractional HP color semantics and camera-facing orientation remain identical.
- Forge resolves side and gap once per ESP frame only when `show_health` is enabled, passes them through the existing static `HEALTH_LINES` sink and restores `host.espLineWidth()` afterward. Existing GL state guard, line/fill tint, nine ESP modes, max_players limit, 80-slot nearest selection, visible-only/range gates, world cleanup and client event ownership remain unchanged.
- No packet, movement, combat, authentication or update-publication changes.

## Automated verification

- New `EspHealthPlacementTests`: **14,000 reproducible** randomized valid player/camera/gap/health scenes, opposite-side horizontal mirror symmetry, matching health fill percentage and vertical endpoints, finite GL coordinates and exact original API/Right+0.18 parity. Checks malformed/nonfinite/unknown side/gap rejection without partial output, owner-thread gating and saved settings/UI roundtrip.
- Existing `EspHealthBarTests` and `FeatureTests` remain green, with FeatureTests extended for default values and persistence. Forge source wiring requires both typed settings, the conditional once-per-frame runtime path, original legacy API, and a new Side class in the JAR inventory.
- Version **2.26.0**, **44 Java regression suites**, **39 milestone documents** (R08–R46). Merge requires Java17/21 headless core tests, Linux and Windows JDK8 Forge1.8.9 compile/reobfuscation/JAR verification and Windows fake-profile Prepare/Collect/Rollback.

**Acceptance limit:** Source/CI tests do not establish actual Minecraft GUI visibility, rendered HP bar thickness, camera position accuracy, real FPS impact or extended gameplay stability. A licensed Minecraft 1.8.9 run is still required. Keep the relevant runtime acceptance issues open.
