# GhostC R3.30 — lifecycle-aware feature fault reporting

`ModuleRegistry.dispatch` (R3.27) avoids running a second
`onDisable()` when an event callback has already disabled
itself or shut down the registry before throwing. The separate guarded
Forge feature callback path, `FeatureFaultGuard.execute()`,
already prevented double cleanup, but continued inserting a new feature
fault warning and invoking its diagnostic/report listener for a module
that had **already completed its own shutdown**.

Consequences were false on-screen "Module disabled" warnings and an
inflated `FEATURE_FAULT` counter during legitimate lifecycle
transitions. A second case occurred when a guard-triggered `onDisable`
failed: the registry's authoritative lifecycle fault could be obscured
by a misleading additional *feature callback* warning.

R3.30 checks the final module state before making a guard report:

- An active feature that throws and is successfully disabled **by the
  guard** retains exactly the existing quarantine, warning and one
  diagnostic event.
- If the callback already disabled its module, shutdown completed or
  the lifecycle itself marked it FAULTED, no second guard warning or
  diagnostic event is generated.
- If the guard's own disable causes a lifecycle FAULTED state, preserve
  the `ModuleRegistry` fault type as authoritative instead of
  synthesizing a second unrelated callback warning.
- When a previously warned feature is manually retried and explicitly
  self-disabled, any stale guard warning for it is removed; warnings for
  unrelated still-unresolved features remain.
- Reentrant lifecycle transitions that leave the entry ENABLED still
  propagate the original exception to the registry fault boundary.
  Serious JVM Errors are still not swallowed.

`FeatureFaultLifecycleReportingTests` exercises 1,024
self-disable-then-throw sessions, 1,024 registry-shutdown-then-throw
sessions, 1,024 failed-cleanup scenarios, 768 warning-recovery
sequences, 96 real host shutdowns inside guarded callbacks, and a
genuine feature failure. Existing R3.21 expectations are updated
to match the final lifecycle authority, retaining all privacy,
recursion and ordinary quarantine checks.

Offline gates: Core Java 17/21, Forge Linux/Windows JDK8,
independently downloaded Windows ZIP Verify/Prepare/Rollback/tamper,
and matching PR/merge Git trees. **Native Minecraft 1.8.9 GUI,
rendering, input and FPS remain unverified.**
