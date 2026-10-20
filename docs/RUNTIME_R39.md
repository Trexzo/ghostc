# GhostC R2.19 — bounded-rank profiler and cached HUD reads

## Reproducible source-level cost in R2.18

The existing opt-in diagnostics HUD renders every GUI frame while active. To draw the aggregate and last-mode statistics, it called `host.snapshotTelemetry()` twice (aggregate and last mode). R2.18 `SnapshotTelemetry.Bucket.snapshot()` calculated p50, p95 and p99 with **three complete linear scans** over up to 4,097 histogram bins. Two buckets therefore required up to six histogram scans, approximately **24,582 loop iterations per diagnostics HUD frame**, even when no new snapshot work sample arrived since the previous frame. The optional profiler could itself consume unnecessary CPU and skew the user’s attempt to measure the client.

This is a source-level bound, **not a measured FPS regression**.

## Production change

- Each fixed-size aggregate/mode bucket replaces the linear-scan histogram lookup with a one-based **Fenwick order-statistic index** of 4,098 long slots (for 4,097 microsecond bins). A successful sample updates at most approximately 13 index positions. Each p50/p95/p99 lookup searches the bounded index in approximately 13 steps, independent of populated bin count and session duration. Memory remains fixed, with no stored events, timings, names, coordinates or session identity.
- Exact legacy nearest-rank formula is retained: `floor(n/100)*percent + ceil((n mod 100)*percent/100)`. Values from 0–4095 µs keep their previous integer bins; the 4096th bin means **4096 µs or greater**; `maxUs` retains the uncapped largest observed duration. Existing saturating numeric count updates are preserved.
- Every bucket caches its immutable `SnapshotTelemetry.Snapshot` after the first read and clears only that bucket’s cache when a new sample arrives. Rendering repeated HUD frames without a new sample uses the same cached instance without bin scanning or new snapshot allocation. Other mode snapshots remain cached when a different mode receives a sample. Legacy aggregate-only `record` continues to affect the aggregate without falsely inventing classified modes.
- Forge event timing gates, four-mode classification, owner-thread isolation, HostRuntime and RuntimeDiagnostics APIs, numeric `snap.*` export schema, diagnostic HUD content, gameplay and ESP/combat behavior are unchanged.

## Deterministic verification and acceptance

- New `SnapshotRankIndexTests` performs **72,000 seeded valid samples**, including zero durations, 4,095/4,096 µs cutoffs, many overflow/outlier entries up to `Long.MAX_VALUE` elapsed nanoseconds and all four target-demand categories. An independent original-algorithm histogram calculates nearest-rank p50/p95/p99 and output aggregates at periodic checkpoints. The tests also verify immutable snapshots, cache reuse/invalidation, independent mode caching, legacy aggregate-only recording, owner-thread blocking and invalid-sample atomicity.
- Existing `SnapshotTelemetryTests` and `SnapshotModeTests` remain enabled without behavioral expectation changes. Forge source wiring requires bounded order-statistic index and cache invalidation and rejects the obsolete linear-scan percentile loop.
- Version **2.19.0**; Java regression suites **37**; milestone guides R08–R39 total **32**. Mandatory gates: Java 17/21 core regression and Forge source tests, Linux/Windows JDK8 Forge 1.8.9 compile/reobfuscation/JAR verification and simulated Windows installation/collection/rollback.

**Acceptance boundary:** The algorithm eliminates a known linear-per-HUD-frame source path, but only licensed Minecraft 1.8.9 runtime testing can quantify resulting FPS/frametime, real HUD readability, mode-specific snapshot timings, target correctness, rendering compatibility and a representative 60-minute soak. Leave runtime issues open until actual evidence exists.
