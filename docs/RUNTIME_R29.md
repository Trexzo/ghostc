# GhostC R2.9 — opt-in FPS distribution and JVM heap evidence

## Remaining acceptance gap

Green Forge builds and R2.8 ESP draw-submission counters cannot establish FPS or memory stability over actual gameplay. Native FPS labels display point-in-time observations only. R2.9 adds **explicitly opt-in, aggregate** measurements to help a licensed Minecraft 1.8.9 acceptance run and its eventual 60-minute soak.

## Implementation

- New host-independent `FramePerformance` maintains a fixed 1001-bin integer-FPS histogram for rounded frame-to-frame **Forge RenderGameOverlayEvent.Text callback intervals**. No per-frame lists, private identifiers or retained timestamps; memory is bounded independently of run length. It computes min, median (p50), p95, p99 and max via nearest-rank order statistics, plus accepted frame samples and suspension count.
- The existing optional `hud.diagnostics` module enables the measurements. `ForgeEventAdapter.onOverlay` samples monotonic `System.nanoTime()` and `Runtime.totalMemory() - Runtime.freeMemory()` only with the module enabled and a validated world/player. Unfocused, chat, menus and other GUI callbacks suspend timing; world switches suspend timing; disabling the diagnostic module suspends timing. First callback after a pause only creates a new reference timestamp, not a fictitious FPS sample.
- Heap measurements show sampled current and peak **JVM heap usage in MiB**; not OS RSS, GPU memory or total process memory. The histogram is rounded FPS, capped at 1000; not GPU render time, Minecraft's built-in FPS calculation or proof of pixels presented on screen.
- The existing optional diagnostics HUD displays FPS min/p50/p95/p99 and current/peak heap alongside R2.8 ESP submission counters. On disconnect/close, the diagnostics export also writes numeric `perf.*` fields with explicit schema/scope; it never records IP, player IDs/names, UUID, coordinates, chat, packets or raw logs.
- Windows `Invoke-GhostCAcceptance.ps1 -Mode Collect` now whitelists only numeric `count.*` and `perf.*` keys and adds in-game observation, focus/GUI pause and 60-minute soak checklist entries. **No automatic game launch, screenshots or benchmark claims**.
- A new deterministic `FramePerformanceTests` tests exact quantile distributions, 12,000 reproducible frame/focus sequences, monotonic percentile and heap constraints, owner-thread enforcement, opt-in gating, pause/reconnect behavior and numeric-only report export. Existing 26 Java suites are preserved and extended to 27; R08–R29 milestone docs total 22.

## Verification and interpretation

Required gates remain Java 17/21 source/core regressions, Linux+Windows JDK8 Forge 1.8.9 compilation/reobfuscation/JAR validation and fake-profile Windows install/collect/rollback. **Passing these does not constitute a real licensed Minecraft 1.8.9 gameplay or soak run.**

To assess runtime, enable HUD Runtime Diagnostics in a real Forge 1.8.9 world, test focus/GUI transitions, then collect the numeric report after disconnect. Over long gameplay, compare min/p50/p95/p99 FPS, heap peak, fault count, ESP submission counters, real visual appearance and frame responsiveness. Explain run duration and conditions separately because the histogram intentionally stores no player/server identity or exact timestamps. Do not close Issues #2/#3/#5/#6 without actual game evidence.
