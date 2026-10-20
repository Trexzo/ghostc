# GhostC R2.18 — comparable snapshot profiling by target demand

## Source-audited measurement error

R2.17 accurately timed the Forge END-tick publication path, but its **single cumulative histogram** mixed multiple fundamentally different workloads: disabled targeting, visible-only target HUD/Aim Assist, unfiltered-only targeting, and mixed visibility requirements. A benchmark collecting one minute in OFF and another in MIXED would report one blended p95 and hide the per-configuration cost. The profiler also recorded samples when the Minecraft screen was open or the game was unfocused, even though combat is disabled in those periods. These issues undermine a before/after or between-configuration FPS/tick study.

## R2.18 fixes

- `SnapshotTelemetry` retains the original bounded 4097-bin aggregate and numeric `snap.*` report fields, while maintaining a separate fixed 4097-bin histogram and workload counters for **OFF**, **VISIBLE_ONLY**, **UNFILTERED_ONLY**, and **MIXED**. The demand category is selected from the actual `SnapshotDemand.needVisible` and `.needUnfiltered` flags on each successful Forge tick. No dynamic maps, lists, player identifiers, positions, chat, server addresses, or history of timestamps are retained.
- Existing `SnapshotTelemetry.record(elapsed,scanned,range,los,published)` and both prior `RuntimeDiagnostics.save` APIs remain source-compatible. The new `saveWithSnapshotModes` method adds only numeric keys such as `snap.mode_off_samples`, `snap.mode_visible_only_p95_us`, `snap.mode_unfiltered_only_los_queries`, `snap.mode_mixed_max_us`, without breaking the Windows acceptance collector's strict `snap.[a-z_]+=digits` whitelist.
- New `HostRuntime.snapshotProfilingEnabled()` checks active world, diagnostics opt-in and **authoritative client-tick input focus** before starting a stopwatch. On open GUI/chat or alt-tab, Forge still publishes its normal world snapshot for session correctness, but it performs no telemetry time reads or counter collection. `HostRuntime.recordSnapshotWork` independently enforces the same gate against stale callback state.
- Diagnostics HUD now shows the **last successfully sampled mode**, its mode-specific p95 and sample count beneath the existing aggregate snapshot figures. The aggregate FPS/JVM/ESP counters are unchanged, and no additional network or gameplay actions are created.
- Report counters are cumulative across world generations and module-setting changes within one host lifetime, **but separated by mode**, not per world/server. Compare modes only under similar worlds/entity counts and note the scanned/LOS counts and sample sizes. A capped quantile of 4096 µs means 4096+ µs, and the uncapped maximum remains separately reported.

## Tests and evidence

- New `SnapshotModeTests` runs 16,000 repeatable classified timing cases with independent mode counts, percentile ordering and summed aggregate checks; validates no partial updates on malformed samples, cross-thread rejection, legacy aggregate-only record compatibility, host opt-in/focus gating, and numeric-only disconnect export.
- Extended Forge source wiring checks require mode flags to be passed after successful snapshot publication, no blocked-focus timing, numeric aggregate/mode persistence, and the HUD's last-mode marker. Java suites total **36**, and R08–R38 milestone documents total **31**.
- Required merge gates: Java 17/21 offline core/source tests, Linux and Windows JDK8 Forge 1.8.9 build/reobfuscation/JAR verification and Windows fake-profile install/collect/rollback.

**Boundary:** R2.18 creates a more meaningful *measurement facility* but does not establish real Minecraft FPS improvement, valid onscreen targeting, actual visual ESP quality, or a 60-minute stability soak. Live measurements still require licensed Minecraft 1.8.9 acceptance. No runtime issue should be closed based solely on this CI evidence.
