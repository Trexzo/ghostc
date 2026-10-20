# GhostC R2.22 — translucent filled-box ESP

## Design and feature behavior

The seven existing ESP mode IDs and their order are preserved. Two new options are appended to the existing Player ESP style dropdown: **`filled_box`** and **`filled_skeleton`**. Filled Box renders six translucent world-space faces aligned to the same interpolated player AABB as the existing 12-edge wire Box, then renders the crisp colored wire outline. Filled+Skeleton adds the original 15-bone schematic skeleton in the same pass. Both reuse the same 80-nearest player budget, camera transform and visibility gates.

The `fill_opacity` persisted numeric setting ranges from **0.04 to 0.50**, default **0.18**, and controls *only* the fill-face contribution. Actual GL fill alpha = existing configured RGBA opacity multiplied by `fill_opacity`; wire outline, tracers and skeletons keep their original RGBA opacity. This permits a subtle volume without forcing wire alpha changes, while older profile files use exactly the same original Box mode, RGBA defaults and health bars.

## Architecture and safety

- New pure Java8 `EspFilledBoxGeometry` checks that all six AABB bounds are finite, sides are within 0.02–8.0 units, and no extent is inverted, degenerate or enormous. Valid boxes emit exactly **six quadrilateral faces / 24 vertices** to a caller-provided static sink, without per-player arrays/collections or vertex allocations.
- The Forge renderer calls `EspFilledBoxGeometry.valid` **before** admitting a candidate into the independent nearest-80 ESP buffer. Invalid geometries cannot displace nearby valid players. Rendering uses one `GL11.glBegin(GL11.GL_QUADS)` per volume and a `finally` `GL11.glEnd()`, draws the fill before wire/skeleton, and uses `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` blending. Existing depth-mask-off behavior, optional depth-test disabling for through-walls, global RenderStateGuard and class fault guard remain intact.
- The existing `Setting.Number` config/profile serialization and GUI scroll controls provide persistence. All original ESP ID/style order, line width/color, health-gauge semantics and state fencing are retained. No packets, combat, authorization, third-party assets or file-publishing behavior is changed.

## Deterministic verification

- `EspFilledBoxTests` validates all six distinct boundary planes and their 24 finite vertices, nondegenerate face area, rejects malformed/inverted/oversized/nonfinite world bounds before any GL sink emission, replays **12,000 seeded valid AABBs**, and tests UI style cycling and fill-opacity profile roundtrip.
- Existing `EspSkeletonTests` now explicitly checks the nine authoritative mode combinations and their primitive gates. `FeatureTests` confirms the new numeric setting default and persistence without profile migration.
- Updated Forge source contracts check pre-admission safety, fill alpha computation, `GL_QUADS` batch/finally and original wire/health preservation. JAR verification requires the new geometry class. Version **2.22.0**, **40 Java regression classes**, cumulative **35 milestone documents** (R08–R42).
- Required merge gates: Java 17/21 Core, Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation and JAR integrity, Windows simulated fake-profile prepare/collect/rollback.

**Runtime acceptance remains unverified:** Only licensed Minecraft 1.8.9 can verify translucent face appearance, through-walls depth, the opacity slider, rendering against other mods, real FPS and a representative 60-minute stability soak. Keep the runtime acceptance issues open until such evidence exists.
