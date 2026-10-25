# GhostC R3.21 — rendering fault quarantine after owner shutdown

`FeatureFaultGuard.execute` guards RuntimeException from a single
render/movement feature and disables only that feature while retaining
a sanitized exception class. An edge case remained: a feature callback
can close its owning HostRuntime (which shuts down ModuleRegistry) and
then throw a normal RuntimeException. The old catch block attempted
`registry.setEnabled(id,false)` despite shutdown and leaked a second
`IllegalStateException("Core shut down")` into the caller. The actual
render exception then escaped the intended fault boundary.

R3.21 keeps the earlier behavior for normal callbacks, but first examines
the module state. If the module was already disabled or faulted during
the callback (including by legitimate shutdown), no second registry
transition is attempted. If a pending lifecycle transition refuses
disabling **while the module is still enabled**, the guard propagates
the *original* callback exception to ModuleRegistry's lifecycle fault
boundary. It never reports successful quarantine of an active module.
The callback failure is recorded only as stable ID and exception class;
private exception messages and stack traces remain excluded.

The R3.21 regression includes:
- 1,000 synthetic callback shutdown-and-throw cases, with independent
  peer cleanup and sanitized warning/report checks;
- self-disabling callbacks that then throw, without duplicate onDisable;
- an actual HostRuntime `close()` inside `runFeature`, followed by
  a callback RuntimeException;
- an active onDisable lifecycle transition that must fault under
  ModuleRegistry's boundary without a fake guard report or recursion.

JVM Errors intentionally continue to propagate, consistent with the
existing policy. Forge version is bumped to 3.0.21. GitHub CI provides
headless Java and Forge package verification only; native Minecraft
rendering/FPS/gameplay remains unverified.
