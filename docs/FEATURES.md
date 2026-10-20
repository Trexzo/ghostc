# GhostC R0.5 — first functional modules (retained in R0.6)

The original Forge 1.8.9 UI exposes all five modules under **F7**. Existing status/FPS overlays from R0.3 remain supported. The new modules are **disabled by default** and persist to `.minecraft/config/ghostc/default.properties`. No third-party binaries or recovered client sources were imported.

| Module / stable ID | Implementation | Configuration |
| --- | --- | --- |
| Keystrokes `hud.keystrokes` | Minecraft-native forward/back/left/right indicators and optional SPACE/SHIFT indicator. Rendered in screen-space. | `show_jump`, `show_sneak` |
| CPS Counter `hud.cps` | Left/right mouse **press edges** counted over sliding 1 second using monotonic nanosecond clock; ignores GUI clicks; clears on world change. | `show_right` |
| Target HUD `hud.target` | Uses current session's immutable read-only world snapshot and deterministic `TargetSelector`; shows nearby player name and optional health bar. No attack/aim behavior. | `range` 2–24, `visible_only`, `show_health` |
| Player ESP `render.esp` | World-relative 3D player boxes, tracers or both; schematic skeleton and box+skeleton in R2.7. Optional camera-facing `show_health` bar in R2.11 (disabled by default). Rendered after world scene with interpolated positions and GL state restoration. | `range` 2–80, `visible_only`, `through_walls`, `line_width` 1–4, `style` |
| Auto Sprint `movement.sprint` | At the end of a client tick requests local sprint only if focused, moving forward, grounded, not sneaking/using item/flying/blind or blocked by hunger. Never forces sprint off. | enabled / disabled |

## Ownership / safety boundaries

- All feature logic is called on the Forge client thread.
- The ESP renderer owns only OpenGL draw calls inside `RenderStateGuard`; it does not change entity movement, inventory, combat or networking.
- Target HUD consumes read-only `SessionServices.snapshot()` and uses `TargetHudPolicy`; it does not automatically attack or write rotation.
- Sprint is a narrowly scoped, one-way client-side setting request through `EntityPlayerSP.setSprinting(true)`; vanilla remains authoritative for subsequent updates.
- CPS telemetry counts physical mouse edge events; world/unfocus resets remove stale held states.
- No Netty packet hook, auto-clicker, aim assist, velocity manipulation, anti-cheat bypass, or other combat claim is part of this batch.
- Keystrokes/CPS HUD use a compact screen-space anchor on the right; Target HUD sits above the hotbar. Separate per-element drag placement is **not** part of R0.5; only the earlier status HUD editor supports dragging.

## Verification

JDK 17/21 GitHub Actions run Java 8-targeted Core/Host/UI/Services/Feature suites. The feature suite covers rolling CPS windows, focus transitions, deterministic target selection, sprint policies and module/profile persistence. Forge 1.8.9 CI compiles and reobfuscates the JAR, and asserts first-party feature classes are bundled. **CI cannot verify** actual mouse/key hooks, GL render correctness, visibility handling, interaction compatibility or gameplay responsiveness.

## Outstanding real in-game acceptance

On a clean licensed Minecraft 1.8.9 + Forge 11.15.1.2318 instance, using the reobfuscated JAR from GitHub Actions:

1. Open F7; find and enable each feature, then restart and verify persistence.
2. Verify Keystrokes reflects rebound Minecraft controls, not hardcoded keyboard letters.
3. Click in game repeatedly; CPS should count distinct presses only and clear after 1 second. GUI clicks must not be counted.
4. Verify Auto Sprint respects stopping, sneaking, collisions, low hunger, GUI focus and vanilla sprinting controls.
5. Use two players on a controlled local server; test Target HUD visibility/health and ESP boxes/tracers at different distances and against wall occlusion.
6. Verify OpenGL depth, blending and texture modes restore without corruption at GUI scales 1–3, on 854×480, 1280×720 and 1920×1080.
7. Save `latest.log`, screenshots and performance measurements in Issue #5. **Do not close Issue #5 from CI alone.**
