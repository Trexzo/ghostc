# GhostC R3.75 — native warning drawing and export isolation

Parent: CI-verified R3.74 draft PR #212 at `c35265a114dd48acbdd1d13df9be2382f64f2689`. Scoped to HUD safety-warning presentation, read-only diagnostics and tests. No combat state, native input, key pulses, module defaults, merge, install or release changes.

## Confirmed source defects

- R3.74 inherited `warningStatusX` from the draggable Status HUD. A right-anchored Status leaves almost no width for a longer ATTACK/USE uncertainty notice. Despite the native lane remaining correctly blocked, the visible message could be blank or meaningless after truncation.
- The warning was rendered directly through `RenderStateGuard.draw` without a per-warning `RuntimeException` boundary. A failed font paint could abort the remainder of `ForgeEventAdapter.onOverlay` rather than allowing independent HUD components to run. OpenGL state restoration remains handled by the existing GL guard; serious JVM Errors are not swallowed.

## Fix

`HudWarningPlacement.x` retains the Status anchor if the full warning fits, otherwise falls back to the independent viewport-left warning anchor. Uses wide arithmetic to avoid overflow and preserves existing vertical placement. `HudWarningDrawGate` contains ordinary warning-paint exceptions with owner-thread isolation and retries the message on later frames. The readout never mutates ATTACK/USE quarantine, native session generation, owner policies or module state. Optional `COMBAT_WARNING_DRAW_FAILED` and `COMBAT_WARNING_DRAW_RECOVERED` counters record **fault edges only**, not one failure per rendered frame. Under opt-out no extra counters are collected. The existing diagnostics writer persists fixed schema-1 anonymous counters with backup rotation; it never exports native identity, exception messages, packet data, names or server details. Host warning state remains independent of export, same-host leave/enter and diagnostics availability.

## Evidence and limits

`HudWarningRenderTests`: exact right-edge placements, overflow, ordinary and serious injected draw failures, retries, owner-thread checks, real `HostRuntime` export and backup rotation, worldPresence transitions, 90,000 randomized independent layout/draw tests. Mandatory original Java8 Minecraft 1.8.9 font-width/trim and KeyBinding linkage probe is added to Forge `check`. Source-wiring test protects the actual existing HUD warning paint site and shows no attack path modifications.

Headless only: the changes do not verify an actual OpenGL context, live rendering pixel output, Minecraft gameplay, hardware input or server acceptance. Broken GPU contexts may still fail to display text; the safety quarantine never relies on successful warning rendering.
