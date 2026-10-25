# GhostC R3.29 — closing-state feature eligibility and telemetry fence

R3.28 blocked public key/focus events, feature callback execution and
producer event ID reservation during HostRuntime.close(). The \`closing\`
window occurs **before** ModuleRegistry shuts down its enabled entries:
each \`onDisable\` sees its own entry as ENABLED until it returns.

Four read-only/telemetry host paths were not yet fenced by \`closing\`:

- \`featureEnabled()\` could advertise a live feature during shutdown,
  even as \`runFeature()\` correctly refused executing it.
- \`metricsFrameRequested()\` could expose prior frame intent.
- \`snapshotProfilingEnabled()\` could continue to admit snapshot
  profiling in the pre-shutdown checkpoint interval.
- \`performanceFrame()\` could add FPS/heap samples after close started.

R3.29 denies feature eligibility and frame intent when closing, and
prevents FPS/heap and snapshot telemetry from mutating in that window.
Live-game behavior and already-completed metrics remain unchanged.
\`diagnosticsEnabled()\` remains deliberately readable during close so
the trusted final, opt-in local diagnostic export can still run.

\`HostClosingEligibilityTests\` covers 192 independent closing-window
fixtures with active target HUD, metrics HUD and diagnostics. It proves
live eligibility before/after simulated closing, fixed FPS/heap/snapshot
counters during the fence, untouched snapshot demand-mode labels, and
final closed-host ineligibility. Another 192 real \`close()\` sessions
observe the still-ENABLED module during its own \`onDisable\` and prove
it cannot advertise itself as an active feature.

The separate Core Java 17/21 and Forge Linux/Windows JDK8 workflows,
with independently downloaded Windows package Verify/Prepare/Rollback,
are offline CI gates. This does not imply real Minecraft 1.8.9
GUI/render/input/FPS acceptance; native validation remains open.
