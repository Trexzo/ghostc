# GhostC R2.7 — original skeleton ESP and measured geometry

## Motivation

The existing first-party `render.esp` module rendered boxes, tracers or both, but did not offer a skeleton-style player overlay. Its mode dispatch also treated any non-`box`/non-`tracer` style as both, making future styles unsafe to add. The 80-player render limit was incremented before position and bounding-box geometry checks, allowing invalid entries to reduce the valid render budget.

## Implementation

- Keep the stable `render.esp` module ID and all existing options; add `skeleton` and `box_skeleton` style choices. Existing schema-1 configurations and original `box`, `tracer` and `both` values remain valid and retain their original meaning.
- Add first-party `EspRenderMode` with explicit boolean draws for boxes, tracers and skeleton, no implicit fallback for unknown modes.
- Add `EspSkeletonGeometry`: 15 connected torso/head/arm/leg bone segments in world space; yaw interpolation across the ±180° seam, Minecraft body yaw orientation, simple crouch displacement and bounded limb-swing approximation. One immutable bone template is reused across players; bone emission uses a callback without per-frame joint arrays. This is a **schematic overlay**, not model-rig bone extraction or exact animation tracking.
- In `PlayerEspRenderer`, use the existing GL state-restore guard, depth and wall filters, distance/visibility gates, and entity interpolation. Draw the skeleton inside a balanced GL_LINES batch. Invalid skeleton poses or nonfinite positions are rejected before GL writes. Only validated players count toward the maximum of 80 drawn per frame.
- Forge JAR verification now requires the original skeleton mode and geometry Java 8 classes.

## Automated verification and limits

The new `EspSkeletonTests` suite covers all five mode combinations, unknown-mode rejection, seam-safe yaw interpolation, crouch/rotation examples, invalid inputs and **14,000 seeded poses** checking topology, rotation equivalence, height and coordinate bounds. Existing `FeatureTests` verifies that all old and new setting choices cycle and persist without changing the module ID. Direct Forge source-contract checks require the render path to use the new geometry and preserve the 80-player budget after validation. The repository inventory retains 25 Java suites and R08–R27 milestone documents.

Required gates: Java 17/21 core tests, Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation, JAR inspection and safe Windows simulated install/rollback. Version **2.7.0**. CI **cannot** confirm that skeletons appear correctly over players in a licensed Minecraft 1.8.9 session, have acceptable depth/occlusion, coexist with other mods or meet FPS targets. Runtime acceptance remains open in Issues #2, #3, #5 and #6.
