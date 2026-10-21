# GhostC R2.54 — module lifecycle re-entrancy and sealed shutdown

## Source-audited G1/G5 failure mode

`ModuleRegistry.setEnabled` invokes untrusted module `onEnable/onDisable` callbacks **before** writing the new state. Such a callback can call `setEnabled` on itself, re-entering the same callback repeatedly until `StackOverflowError`. An `onEvent` failure's fallback `onDisable` cleanup had the same vulnerability. `ModuleRegistry.shutdown` previously set `shutdown=true` only after calling every module's disable callback, so an early callback could register, re-enable or bind a module during shutdown. A module's shutdown call while enabling could leave it enabled after the registry was shut down.

## Implementation

- Each module entry owns a short-lived `transitioning` fence. Enabling/disabling the same module recursively fails with `IllegalStateException`, quarantined by the original callback boundary. Healthy peer callbacks and future toggles retain normal semantics.
- The event-fault cleanup path acquires the same lifecycle fence when it calls `onDisable`. This prevents recursive cleanup from a faulted event.
- When `onDisable` itself fails, record `FAULTED` without invoking `onDisable` a second time. Fault metadata stays exception-type-only; private messages/stacks are not retained.
- Reject `shutdown` during a not-yet-committed lifecycle callback, and set the terminal shutdown marker **before** invoking disable callbacks. Re-entrant shutdown after sealing is idempotent. No callback can re-enable peers, register new modules or mutate keybinds during shutdown.
- Preserve the existing ENABLED/DISABLED/FAULTED states, fault-reset workflow, owner-thread policy, and persisted settings format. No Forge feature, packet, input or rendering adapter behavior changes.

## Tests and gates

- New Java8 `ModuleLifecycleReentryTests` covers self-enable and self-disable loops, cross-module cyclic activation, event failure cleanup, shutdown-time re-enabling/registration/bind attempts, shutdown from onEnable, and 5,000 repeated normal toggle pairs.
- Integrated into Java17/21 CI, repository test inventory, and Forge source wiring regression. Required Forge JDK8 Linux/Windows build/reobfuscation, JAR integrity and simulated Windows install/collect/rollback remain release gates.
- This is offline lifecycle contract validation, not a real Minecraft 1.8.9 launch or FPS/visual/stability acceptance.
