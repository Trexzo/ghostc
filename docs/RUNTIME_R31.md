# GhostC R2.11 — configurable ESP health bars

## User-visible implementation

- Add a new `render.esp.show_health` typed boolean setting, default **false**. It is available in the existing ClickGUI module settings and persisted by the same profile serializer, without changing the module ID, save schema, existing modes or other ESP preferences.
- The first-party Java-8-compatible `EspHealthBarGeometry` emits a camera-facing, vertical two-line world-space gauge: dark background for full height, colored foreground showing current/max health. Health is clamped to [0,1], so over-max values never extend beyond the bar. A zero-health ratio renders only a background.
- The health bar is independent of Box/Tracer/Both/Skeleton/Box+Skeleton styles, with the original line-width, range, visibility, occlusion/through-walls and GL restore rules. Position uses the viewer's horizontal direction relative to the interpolated entity position; this is a schematic player-space overlay, not exact bone rigging.
- Malformed width, height, coordinates, viewer geometry or health values **suppress only the optional bar**, leaving the underlying eligible ESP box/tracer/skeleton visible. The existing `ESP_INVALID_GEOMETRY` numeric-only counter records rejected gauge geometry without names, coordinates or server identity.
- Health-line GL scopes are balanced on errors, and the configured ESP line width is restored for subsequent primitives. A static sink and arithmetic emission avoid allocating per-frame joint or health line objects.

## Regression and acceptance boundary

- New `EspHealthBarTests` verifies half/full/zero/over-max fill, camera-horizontal rotation, translation invariance, invalid values and 14,000 seeded geometries. The updated `FeatureTests` verifies default-off setting, GUI toggling, profile persistence and all previous ESP styles; Forge source contracts and the JAR inventory require the new code path.
- All existing 28 Java suites are retained, yielding 29. Release version **2.11.0**; milestone documents R08–R31 total 24.
- Required automated gates: Java 17/21 Core CI; Forge 1.8.9 JDK8 compile/reobfuscation and JAR integrity on Linux/Windows; Windows fake-profile preparation, collection and rollback.

**Still unverified in a real licensed game:** Pixel-level health-bar appearance, depth behavior, occlusion, readability with different player skins, compatibility with other Forge mods, actual FPS and the 60-minute soak. Acceptance checklist includes optional health-bar behavior and positioning; Issues #2/#3/#5/#6 remain open until real runtime evidence is produced.
