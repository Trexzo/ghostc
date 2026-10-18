# GhostC architecture and acceptance gates

## Product direction
Aim for a high-quality, configurable Minecraft 1.8.9 ghost client with reliable module orchestration, consistent visual design, safe configuration and reproducible builds. Better-than-competitor claims require *paired, reproducible measurements*, not a feature list.

## Ownership model
- **Runtime adapter (not yet implemented):** translates Minecraft/Forge events into immutable host-independent core events.
- **ModuleRegistry (R0.1):** stable IDs, deterministic lifecycle and per-module fault containment; callbacks confined to the host owner thread.
- **InputRouter (R0.1):** edge-triggered keybindings and typing focus suppression.
- **BoundedIngress (R0.1):** bounded cross-thread queue with rejection counters and owner-thread drains.
- **ProfileStore (R0.1):** typed settings, schema version 1, backup and preflight.
- **Shared services (future):** one owner per rotation, targeting, packet, inventory and render operation. Features issue requests to coordinators rather than compete to overwrite globals.
- **Visual platform (future):** searchable ClickGUI, scalable fonts, draggable HUD and render-state restoration.

## Gates
1. **R0.1 — core:** javac Java 8 target, offline suite, demo and CI on JDK 17/21.
2. **G1 — Forge 1.8.9 adapter:** launch in game, world lifecycle, focus, shutdown, screen/input tests, instance isolation, no native injection for the baseline.
3. **G2 — GUI/HUD:** search, layout persistence, theme abstraction and FPS measurements with screenshots at several resolutions.
4. **G3 — gameplay service contracts:** event phase ordering, target snapshots, rotation arbitration, packet mutation boundary and backpressure tests.
5. **G4 — feature behavior:** modules implemented against G3 contracts; each has unit, integration and in-game tests, no phantom feature claims.
6. **G5 — quality:** clean-room provenance, binary reproducibility, crash containment, 60-minute soak, world hopping, profile migration, instrumentation and release checksums.
7. **G6 — new versions:** version-specific adapters only after the 1.8.9 gate passes.

## Constraints
Do not mix decompiled proprietary sources or obfuscated native payloads into GitHub. Forge/Minecraft integrations must use dependencies under applicable terms. Document results as **offline**, **CI**, **Windows runtime** or **in-game verified**, never conflate them.
