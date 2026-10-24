# GhostC R3.09 — preflight host close against active module lifecycle

## Problem

R3.08 sealed the asynchronous event ingress before module onDisable
callbacks, correctly refusing all late producer work. However,
ModuleRegistry.shutdown refuses to run while an onEnable/onDisable
callback is active. HostRuntime.close did not perform that refusal
until AFTER sealing its ingress, saving profiles and exporting optional
diagnostics. A module which tried to close its host in a directly invoked
onEnable/onDisable callback could thus leave it apparently live
(closed=false) but with its event queue terminally sealed, even though
the callback's IllegalStateException was caught by module fault isolation.

## Change

Add owner-thread ModuleRegistry.assertShutdownAllowed, performing the
same active-lifecycle check as ModuleRegistry.shutdown. HostRuntime.close
calls this before setting closing=true, sealing the queue or performing
ANY persistence and teardown. Shutdown itself continues to invoke the
same check. A denied close therefore leaves the input/event queue open,
the host able to tick and the already-saved files untouched.

Normal owner close still seals ingress before module onDisable, as
required by R3.08. No change to input, module keybinds, gameplay,
config file formats or in-game visuals.

## Regression

HostRuntimeTests.testClosePreflightDuringModuleLifecycle registers
dedicated malicious-close test modules for both enable and disable
callbacks. It asserts contained fault state, no partially closed host,
no premature profile file, continued enqueue/tick behavior and a later
successful legal close. ModuleLifecycleReentryTests explicitly checks
that preflight refuses both lifecycle callback scopes without setting
shutdown or altering module state. Forge source wiring pins ordering of
preflight, seal, persistence and module shutdown.

Exact-head Core Java17/21 and Forge Linux/Windows JDK8 CI, identical
tested/merged tree and green postmerge CI are required. These are
offline/packaging checks, not native Minecraft acceptance.
