# GhostC R2.23 — deterministic back-to-front transparent ESP volumes

## Specific problem found in the R2.22 source

Filled Box and Filled+Skeleton used alpha blending with the depth-write mask disabled. The Forge adapter iterated the nearest-80 player selection from closest to farthest and emitted each volume's six faces in a fixed +/−X, +/−Y, +/−Z order. Source-level OpenGL blending therefore accumulated overlapping translucent surfaces in an ordering unrelated to camera distance, which can lead to visually inconsistent transparency, especially as the camera moves.

This does **not** establish that an actual Minecraft session exhibited a measured rendering defect. The change supplies a more appropriate painter ordering for those static world-space translucent boxes.

## Implementation

- Add pure Java8 `EspFilledBoxGeometry.emitBackToFront`: validate the existing six AABB bounds and the finite camera position, preflight all **six finite squared face-center distances** to reject malformed or overflowing geometry before emitting any GL vertices. Then select faces farthest-to-nearest, with stable original face index tie-breaking.
- Selection uses a six-bit emission mask and fixed loop bounds, **no new per-player arrays or collections**. Original six quads, winding and 24 vertices remain byte-equivalent geometrically; the existing `EspFilledBoxGeometry.emit` fixed-order API remains available for existing consumers/regression oracles.
- Forge's `PlayerEspRenderer` reuses the existing sorted nearest-80 buffer, but when `mode.fill` is true traverses it **backwards**, submitting farther chosen players before nearer ones. Non-filled Box/Tracer/Skeleton/Corner modes retain original nearest-first iteration. Each filled volume emits faces back-to-front based on `RenderManager.viewerPosX/Y/Z`, then its RGB wire outline and optional skeleton, preserving current alpha/depth settings and the `RenderStateGuard`.
- Configuration and visual identity unchanged: the seven original mode choices, Filled Box/Fill+Skeleton modes, RGBA/opacity sliders and health gradient, player cap, visibility/range fencing and profile schema remain. No combat, packet, network, auth or persistent-file mutation introduced.

## Regression and acceptance

- New `EspBlendOrderTests` uses the retained original six-face API as an **independent geometry oracle**, comparing exact coordinates of every quad emitted in sorted order and checking nonincreasing camera distance. It replays **11,000 seeded valid AABBs/camera positions**, symmetric inside-camera tie cases, invalid/NaN/overflow inputs, absence of partial output, and null-sink handling.
- Forge wiring/source contracts enforce camera-sorted volume emission inside guarded `GL_QUADS`, per-player painter reversal **only when filled**, unchanged legacy scan loop and preservation of original wire overlays. Existing `EspFilledBoxTests` / all prior suites remain intact.
- Version **2.23.0**, **41 Java regression classes**, **36 R08–R43 milestone guides**. Merge requires Java17/21 headless core checks, Linux+Windows JDK8 Forge1.8.9 compile/reobfuscation/JAR integrity and Windows fake-profile install/collect/rollback.

**Limits:** Sorting faces by center is an approximation for translucent intersecting or very large overlapping volumes, not order-independent transparency. Real Minecraft 1.8.9 gameplay is needed to judge blended pixels, motion, other-mod compatibility and FPS. No in-game performance or stability verification is claimed.
