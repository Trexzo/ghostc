# GhostC R2.10 — focus timing and independent warning visibility

## Source audit: actual correctness gaps

- **Unmeasured GUI time:** R2.9 suspended frame timing only in the Forge 2D overlay callback, but `RenderGameOverlayEvent.Text` is not guaranteed to run while the client displays chat or another GUI. END client tick continues and knows the actual focus state. Without suspending there, the first overlay callback after closing a GUI could incorrectly count the entire time away as a near-zero FPS interval.
- **Unadmitted world identity:** A transient render callback for a world/player pair other than the admitted owner identity returned early without suspending the prior timing reference. The next valid callback could include that gap.
- **Warnings disappeared with both Status/Metrics off:** The R2.2 measured HUD feature guards were correct, but fault/recovery warnings were placed *inside* the conditional requiring one of those two HUD modules. Users who had disabled both could not see an important module quarantine, profile/UI recovery failure or diagnostics-save warning even when the game overlay was otherwise rendering.

## R2.10 code changes

1. `HostRuntime.inputFocus(true)` suspends `FramePerformance` immediately in the independent client END-tick lifecycle, whether or not subsequent overlay callbacks fire. `performanceFrame` also refuses a stale callback that claims focus while the host's authoritative focus state is blocked.
2. `ForgeEventAdapter.onOverlay` suspends on an unadmitted world/player identity before returning. The existing opt-in gating, capped histogram and JVM heap tracking remain unchanged.
3. `FramePerformance` treats negative/reversed/overflowing elapsed samples as rejected observations (new saturating `invalidIntervals` aggregate) rather than inventing an FPS bin; export it as numeric-only `perf.invalid_intervals`.
4. Fault/UI/profile/diagnostics warnings are drawn in a separate GL guard **regardless of the Status/Metrics feature flags**. Their original placement beneath a measured combined HUD is preserved when present, while `HudWarningPlacement` supplies a tiny-viewport-safe fallback if both are disabled. The existing independent Status and Metrics fault guards remain intact.

## Regression evidence and limits

- New `LifecycleVisibilityTests` simulates 200 client focus-blocked END ticks **without** Forge overlay callbacks, checks no false FPS samples on reopening, fences a stray misreported overlay sample, verifies world-change suspension, and tests 13,000 seeded viewport warning positions including 1px windows.
- `FramePerformanceTests` now tests reversed timestamp admission, valid post-rejection samples and numeric-only invalid interval export. Source-level Forge wiring asserts actual lifecycle/error routing rather than simulated-only policy.
- The repository retains 28 Java regression classes and 23 cumulative milestone docs (R08–R30). R2.10 uses Java 8-compatible source; the CI gates remain Java 17/21 core, JDK8 Linux/Windows Forge 1.8.9 compilation/reobfuscation/JAR verification, plus fake-profile Windows install/collect/rollback.

**Unverified:** The tests do not launch a licensed Minecraft session, inspect rendered warning pixels, validate mod compatibility, measure actual FPS or perform a 60-minute soak. The existing runtime acceptance issues #2/#3/#5/#6 remain open.
