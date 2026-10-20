# GhostC R2.24 — optional distance-dependent Player ESP fading

## Visual limitation addressed

R2.23 shows the same configured RGBA alpha for every player in range, from close targets to the most distant of the nearest 80. On busy multiplayer scenes, this can create clutter and make far translucent overlays compete with the foreground. R2.24 introduces optional, distance-sensitive opacity without removing or reprioritizing any eligible target.

## First-party implementation

- **`render.esp.distance_fade`** is a persisted boolean, **false by default**. Missing values in older profiles retain the precise existing output. The Forge renderer bypasses even the extra squared-distance fetch and square root when fading is off.
- **`fade_start`** is a persisted fraction (0.0–0.90, default 0.50) of configured Player ESP range (2–80 world units); closer players keep the full configured RGBA opacity.
- **`fade_min_opacity`** is a persisted multiplier (0.05–1.0, default 0.25). For admitted players beyond the fade start, `EspDistanceFade.factor` computes a deterministic linear decay in *physical distance* (square-root of the already-used squared range metric), reaching the floor at the selected maximum range. The factor stays finite and clamped within its configured bounds.
- Box, Tracer, Skeleton, Corner and Filled modes all use the same attenuated line alpha while retaining the same RGB hue. Filled faces additionally multiply by the existing `fill_opacity`, so the volume never becomes more opaque than its wire. The optional HP overlay is intentionally not faded: its existing health color and GL line state remain independent.
- The policy is Java8 pure, allocation-free for each player and carries no Minecraft, packet or OpenGL dependency. Color is still resolved once per frame, and the scalar factor once per admitted player. All original nine mode IDs, nearest-80 membership, visibility gates, GL state restoration, camera-dependent R2.23 painter ordering, settings/profile serializer and world cleanup remain unchanged.

## Verification and runtime boundary

- New `EspDistanceFadeTests`: **18,000 seeded** range/fraction/floor combinations compared with an independent fade equation, enabled/disabled parity, monotonic farther attenuation, bounds and malformed-input checks, compositional face/wire alpha, real HostRuntime settings update and saved-profile roundtrip. Existing `FeatureTests` now checks legacy defaults and GUI control persistence.
- Forge source contracts verify that disabled fading skips extra distance lookups, fill+line alpha uses the same factor, settings registration is complete, and HP overlay remains separate; the Forge JAR inventory requires the pure new fade policy class. Version **2.24.0**, **42 Java regression suites**, **37 milestone guides** (R08–R44).
- Required gates: Java17/21 Core regression and source checks, Linux and Windows JDK8 Forge1.8.9 compilation/reobfuscation/JAR inspection, Windows fake-profile install/collect/rollback.

**Unverified:** In-game alpha blending, actual visual clutter reduction, other-mod compatibility, measured FPS impact and a representative 60-minute Minecraft gameplay soak. Only a real licensed Minecraft 1.8.9 run can establish those outcomes. Keep runtime acceptance issues open.
