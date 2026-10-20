# GhostC R2.12 — stable nearest-80 ESP admission

## Evidence-based problem

The R2.11 3D Player ESP renderer stopped after 80 validated entries while iterating `Minecraft.theWorld.playerEntities` directly. That list is not ordered by proximity. In a crowded world, more distant players encountered early could exhaust the budget before closer players were ever drawn. The same arbitrary selection affected boxes, tracers, skeletons and optional health bars. Existing R2.7 geometry validation kept malformed entities from consuming slots but did not solve ordering.

## Implementation

- Add Java-8-compatible `EspNearestBudget<T>` in the Minecraft-independent `features` core. A fixed reusable buffer of at most 80 references, ID keys and squared distances maintains **ascending distance**, tie-breaking by entity ID. It rejects null candidates, nonfinite/negative distances and duplicate entity IDs, and bounds storage irrespective of source-list size. It does not allocate per-frame lists, arrays or candidate objects.
- The Forge `PlayerEspRenderer` builds the candidate buffer from the current world under existing opt-in, visibility and distance filters, and runs the full finite coordinate/bounding-box/height and optional skeleton pose preflight **before** accepting a candidate. It then draws the nearest eligible players in deterministic order. Health bars retain their independent invalid-health tolerance and all five existing style choices remain unchanged.
- The buffer is owner-thread-fenced. A `finally` block clears every retained `EntityPlayer` reference after each render even if drawing a selected candidate fails; no old-world references can persist across successful frames. A render tick fraction outside [0,1] is rejected at entry for every style rather than only skeleton.
- The design intentionally keeps existing squared-distance semantics relative to Minecraft's local player state. It is not a visibility-occlusion oracle, and 80 is a frame budget rather than a guarantee that every loaded player appears.

## Regression verification

- `EspNearestBudgetTests`: invalid candidates and capacity, stable sorted tie order, duplicate admission, worker-thread rejection and cleared references; 280 shuffled 320-player worlds with equal-distance ties, plus 1,000 replayed frames with 180 offers each.
- `tools/test_forge_wiring.py` verifies budget selection precedes the main ESP draw, validation precedes admission, and the buffer is cleared inside a `finally` block; Java tests and required Forge JAR class inspection now include the new component.
- All 29 previous Java test suites retained, adding a 30th suite; R08–R32 documents now total 25. Release **2.12.0** updates Gradle, Forge Java metadata, mcmod.info and the Windows in-game acceptance checklist consistently.
- Required gates: Java 17/21 CI, Linux and Windows Forge1.8.9 JDK8 compilation/reobfuscation/JAR integrity and Windows simulated transactional install/collect/rollback. These test builds, implementation contracts and policies—not actual visual results.

**Outstanding licensed Minecraft acceptance:** On a crowded test world, verify nearest-80 visual selection, cross-world behavior, all ESP modes, health bar positioning, depth behavior, FPS and a 60-minute stability soak. Issues #2, #3, #5, #6 remain open until such runtime evidence exists.
