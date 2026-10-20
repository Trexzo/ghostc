# GhostC R2.20 — configurable first-party Player ESP colors

## Functional feature gap

The existing Player ESP renderer used a literal `GL11.glColor4f(0.62f,0.56f,1.0f,0.82f)` to color Box, Tracer, Both, Skeleton and Box+Skeleton line primitives. The existing line width, distance and visibility controls worked, but a user could not adjust their appearance without editing Forge source and rebuilding the client.

## Implementation

- Add four typed, persistent `Setting.Number` controls to the **same** `render.esp` module in the current ClickGUI: `red`, `green`, `blue` each 0.0–1.0, and `opacity` 0.1–1.0. Defaults (0.62, 0.56, 1.0, 0.82) exactly preserve the previously shipped purple and its transparency. All legacy profile fields, module IDs, schema and style IDs remain unchanged. An older profile without the four keys receives exactly the old appearance.
- A first-party Java 8 `EspLineColor` validates finite bounded normalized components, exposes immutable float values for OpenGL, and refuses invalid/nonfinite channels before any ESP draw. `HostRuntime.espLineColor()` reads it under the established owner-thread module gate.
- Forge `PlayerEspRenderer` reads one immutable line color per 3D render frame and binds it to the existing common pre-draw `GL11.glColor4f` call. Every existing Box/Tracer/Skeleton line mode follows that same tint, with no per-player arrays, palette maps, color objects or new shader/assets. The existing `RenderStateGuard` still owns GL state restoration and the per-frame 80-player cap, visibility, depth and geometry validation remain unchanged.
- The optional ESP health bar intentionally keeps its dark background and red-to-green current/max-health gradient so its semantic health information is not overwritten by the custom wire color.
- No combat, input, click handling, movement, packet, account, file-system publishing or authentication behavior is added or changed.

## Deterministic regression and acceptance

- `EspLineColorTests` checks all legacy default RGBA values, bound endpoints, explicit NaN/Infinity/out-of-range rejection, 25,000 seeded RGBA combinations, owner-thread access gating, GUI slider mutation and persistence using the original serializer. The existing `FeatureTests` adds an RGBA state/profile roundtrip. Forge source contract verifies common color application before all three primitive kinds, health bar independence, typed settings registration and required new JAR class.
- All 37 previous Java test classes remain, adding `EspLineColorTests` as the **38th**. Cumulative milestone docs R08–R40 total **33**. Version metadata is **2.20.0**.
- Required release gates: Java 17/21 core suite and source/wiring checks, Linux/Windows JDK8 Forge 1.8.9 compile/reobfuscation/JAR integrity and Windows fake-profile install/collect/rollback.

**Real Minecraft acceptance outstanding:** Use the existing Forge client to verify that color/opacity changes are visible in Box, Tracer, Both, Skeleton and Box+Skeleton, do not alter health bar semantic colors, persist across client restarts and respect through-walls/visibility behavior. The tests do not prove actual GL pixel output, FPS, other-mod compatibility or a representative 60-minute game session. Leave G2/G4/G5 acceptance issues open for that evidence.
