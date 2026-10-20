# GhostC R2.21 — 3D Corner Box ESP with adjustable length

## Feature and architecture

Five preexisting ESP styles were Box, Tracer, Both, Skeleton and Box+Skeleton, with the Player ESP default remaining Box. These remain unchanged, with two new appended choices: `corner_box` and `corner_skeleton`. Unlike the complete 12-edge Box, Corner Box draws **three short arms at each of eight bounding-box corners**, inwards along the X/Y/Z axes (24 independent segments per player). Combined Corner+Skeleton adds the same first-party schematic 15-bone pose from R2.7 inside the corner frame; it is not a native Minecraft rig or exact bone replay.

`corner_length` is a new persisted normalized number on the existing `render.esp` module, with bounds 0.10–0.50 and default 0.26. The number measures the fraction of each local box side used by each arm, rather than a constant world-space length. It is available from the existing scrolling GUI setting rows, and uses the existing validated typed profile serializer without a schema migration. All previous five style IDs and their order are retained so old profiles and UI cycle expectations remain compatible.

The Java8 pure `EspCornerBoxGeometry.valid` screens nonfinite minima/maxima, zero/inverted/oversized boxes and invalid fractions. Forge applies that preflight **before nearest-80 candidate admission**, so a corrupt geometry candidate cannot displace a valid nearby player. A valid box emits exactly 24 straight lines using a reusable static GL sink with one `glBegin(GL_LINES)`/finally-`glEnd()` batch per candidate. No per-player segment lists, arrays, matrix objects or heap allocation are introduced.

The renderer keeps the exact R2.20 per-frame RGBA tint and transparency, shared through all wire styles; optional health bars retain semantic HP colors. Existing depth/through-wall, visible-only, ESP range, fixed width, interpolation, world identity and GL-state restoration remain under the same runtime feature guards. No packet, combat, target motion or external game-memory behavior is introduced.

## Offline verification

- `EspCornerBoxTests` checks all 24 segments, finite and bounded endpoints, consistent three-axis corner topology and scaling at 25%/50%, malformed fraction and nonfinite/degenerate geometry rejection, 15,000 deterministic valid 3D bounding boxes, and saved profile/UI style selection/length settings.
- Existing `EspSkeletonTests` now checks seven authoritative `EspRenderMode` definitions and verifies new modes never silently enable full boxes or tracers. `FeatureTests` extends saved setting and existing style order checks. Forge wiring source contracts require pre-admission validation, exact new mode branches and strict `glBegin`/`finally glEnd`, and JAR inspection requires the new geometry class.
- Java regression suite count **39**; cumulative milestone documents **34** (R08–R41). Release version **2.21.0**.
- Mandatory CI: Java17/21 Core regression, Linux and Windows JDK8 Forge 1.8.9 compile/reobfuscation/JAR integrity, Windows fake-profile install/collect/rollback.

**Runtime acceptance remains open.** In Minecraft 1.8.9, verify that the corner frame is visibly distinct from Box, the length slider adjusts the segments, Corner+Skeleton renders both sets, RGB/opacity/health/through-walls behave as configured, and the client stays stable under representative multiplayer crowds. CI cannot prove rendered pixels, FPS or a 60-minute in-game soak.
