# GhostC R2.17 — opt-in snapshot work profiling

## Measurement gap found in R2.16

The R2.15/R2.16 changes reduce source-level line-of-sight calls and allocations in target selection, but the previous diagnostics measured overall overlay frame intervals and JVM heap only. They could **not** quantify how long the Forge END-tick target snapshot publisher took or how many candidates and LOS ray checks it actually performed. This release adds a tightly bounded, explicitly opt-in measurement path so the owner can gather evidence in licensed Minecraft 1.8.9.

## Implementation

- `SnapshotTelemetry` is a Minecraft-independent Java-8-compatible owner-thread collector with a fixed **4,097-element** microsecond histogram (0–4095 µs plus 4096+ µs overflow bucket). It reports nearest-rank p50/p95/p99 snapshot work duration, an **uncapped** maximum measured duration, successful measured snapshot ticks, and aggregate player scans, range-admitted players, LOS ray calls and published `WorldSnapshot.Entity` count. It stores **no entity IDs, names, positions, server IPs, packet data or raw timing history**.
- `ForgeEventAdapter.onClientTick` captures `System.nanoTime()` only if the existing `hud.diagnostics` module is enabled. During the actual **Forge END-tick snapshot publication** it increments small local counters as each loaded `EntityPlayer` is scanned, passes active demand/range admission, and triggers `canEntityBeSeen`. Publication count is the finalized immutable snapshot list size, after its independent validity check. The completed work sample is recorded **only after a successful `SessionServices.publish`** and before the `finally` releases raw candidate refs. A failed publish does not produce a successful sample.
- Forge diagnostics HUD displays snapshot timing p50/p95/p99 µs, cumulative scanned/in-range/LOS queries, published targets/tick count and maximum µs. The established optional `diagnostics-last.properties` export includes only numeric `snap.*` fields, along with existing `count.*` and `perf.*` fields. Existing `RuntimeDiagnostics.save` overloads remain available for older callers; no session identity is collected. Windows `Invoke-GhostCAcceptance.ps1 -Mode Collect` explicitly whitelists the new strictly numeric `snap.*` keys.
- Runtime Diagnostics remains disabled by default. With it off, there is **no microsecond timestamping or per-player profiler counting**; existing targeting/ESP logic, profile schema and settings remain unchanged. Timing includes Forge scan/admission, LOS calls, immutable object construction and publish, but excludes later Aim Assist/Target HUD rendering and total client tick work.
- A capped p95/p99 of 4096 µs means at least that quantile is in the 4096+ bucket, **not** precisely 4.096 ms. The uncapped max helps identify worse spikes. JVM and FPS values are separate aggregate observations; none of these measurements are evidence of GPU frametime.

## Verification / next runtime acceptance

- New `SnapshotTelemetryTests` covers exact histogram quantiles and saturation, 16,000 seeded time/volume sequences with aggregate monotonicity, cross-thread rejection, invalid samples, real `HostRuntime` opt-in gating and schema-safe disconnect persistence.
- Existing `FramePerformanceTests` is extended to accept the new strictly numeric field family without relaxing identity/privacy invariants; Forge wiring source contracts verify timing start only when enabled, scan/range/LOS counters, and recording strictly **after** successful publication.
- R2.17 version metadata **2.17.0**, 35 Java test suite classes, 30 R08–R37 milestone guides, Windows acceptance checklist.
- Mandatory merge gates: Java 17/21 tests, Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation/JAR inspection, fake-profile Windows install/collect/rollback. CI can verify instruments and deterministic policy but cannot run or benchmark a licensed Minecraft session.

**Remaining acceptance:** In-game sampling should compare all-target-disabled, visible-only, unfiltered-only and mixed HUD/AimAssist settings under consistent worlds, note measured sample counts/duration and p50/p95/p99/max µs, LOS queries, FPS/JVM heap, verify ESP/HUD behavior and complete a representative 60-minute stability soak. No actual game session or FPS improvement is claimed by R2.17.
