# GhostC R2.8 — ESP runtime evidence and malformed geometry admission

## Real source defects observed in R2.7

1. Skeleton draws checked the **current** `EntityPlayer.renderYawOffset` for finiteness but did not check `prevRenderYawOffset`. Passing nonfinite previous yaw into the interpolation function threw inside the render callback; the module-wide fault guard could disable ESP after this malformed entry.
2. Non-skeleton tracer mode emitted `yy + p.height * 0.5` without checking that the player's height was finite, nonnegative and plausible. Thus a malformed entity could submit NaN or infinite OpenGL vertices.
3. Core regression tests covered geometric policy, but there was no opt-in indication from an actual Forge draw path that **any** ESP players or skeletons had been sent for rendering. Real Minecraft visual acceptance was therefore harder to diagnose without collecting private logs.

## R2.8 implementation

- Validate `EntityPlayer.height` in **every** ESP style and, in skeleton styles, validate both previous and current body yaw, limb values and a bounded render interpolation fraction before calling `EspSkeletonGeometry.interpolateYaw`. Invalid entries are skipped and do **not** use the 80-valid-player cap.
- Add bounded, saturating, owner-thread-only numeric `RuntimeDiagnostics.Counter` entries: `ESP_RENDER_FRAME`, `ESP_DRAWN_PLAYER`, `ESP_DRAWN_SKELETON`, `ESP_INVALID_GEOMETRY`. Counters are incremented from the actual Forge `PlayerEspRenderer` code path. These indicate **draw submissions**, not confirmed visible pixels, accurate limb alignment, actual FPS or successful network behavior.
- Surface these counters in the existing optional `hud.diagnostics` panel and in its opt-in counter-only export on disconnect/host close. No player name, UUID, world coordinates, server address, chat, packets or screenshot information is collected.
- Extend `Invoke-GhostCAcceptance.ps1 -Mode Collect` with an explicit visual validation checklist for all five styles, depth filtering, player switching and diagnostics. It continues to collect only a safe local counter report; it **does not** auto-launch Minecraft or claim that a GUI session ran.
- Add `EspTelemetryTests` for owner-thread enforcement, snapshot behavior, exact counter serialization, opt-in file creation and privacy. Update Forge source wiring contracts and preserve the 14,000-pose `EspSkeletonTests` and existing module/profile tests.
- Update version to **2.8.0**; retain 26 Java regression classes and 21 R08–R28 cumulative milestone guides.

## Acceptance requirements

All Java 17/21 tests, Linux/Windows JDK8 Forge 1.8.9 build/reobfuscation and JAR integrity, and Windows fake-profile install/collect/rollback must pass on the exact branch head. These are **offline gates**.

For a meaningful licensed Minecraft 1.8.9 acceptance run, the user must still confirm Forge Mods listing, F7/ESC, world transitions, enabled ESP, five render modes and occlusion visually. With `hud.diagnostics` enabled, actual frames/players/skeletons should rise when a real visible target is present; invalid geometry should not spike. Only then can an in-game acceptance claim be made, subject to gameplay and soak tests. Keep Issues #2/#3/#5/#6 open.
