# R0.8 — client lifecycle, physical-input admission, runtime diagnostics

## Reliability changes

1. END client tick now defines **world ready** only when both `Minecraft.theWorld` and `Minecraft.thePlayer` exist. WorldEvent callbacks no longer write session state before player initialization. The full read-only world snapshot is published after the owner's tick clock advances.
2. `ClientDisconnectionFromServerEvent` now submits a `minecraft.addScheduledTask(...)` callback rather than mutating the core or HUD/feature state on an unknown network-event thread. The callback ignores work after host close and a possibly stale disconnect after a new valid world appears. World leave is independently picked up by END tick.
3. `HostRuntime.disconnect()` is idempotent per joined session, including when world leave already happened, and does not report a phantom disconnect at initial menu. A subsequent world enter re-arms the disconnect reporting state.
4. `PhysicalInputGate` only authorizes synthetic combat pulses after observing an **attack key release in the currently active focused world**. Opening a GUI, losing focus, world absence, changing session generation or explicit reset revokes admission until a new release. Both mouse and keyboard attack bindings are supported, including LWJGL negative mouse codes.
5. Unbound attack key (code 0), reserved F7 GUI key (code 65), or coinciding attack/use bindings suppress synthetic pulses. Vanilla physical input is not blocked. Aim Assist with `require_click` follows the same admission gate.
6. CPS held-input state resets on lost focus, GUI or world readiness, avoiding phantom old mouse presses on reentry.

## Optional diagnostic evidence

The F7 RENDER module `hud.diagnostics` is **disabled by default**. When enabled it overlays only:
- current session generation and tick;
- pending action and rotation requests;
- counters for local aim corrections and extra attack/use pulses;
- count of focus losses and suppressed synthetic inputs.

Upon disconnect or host close, enabling the module also writes a bounded counter-only `config/ghostc/diagnostics-last.properties` with `.bak` backup. It contains no account/player identity, server hostname/IP, packet body, player coordinates, chat, screenshots, or time series. It uses a temp file with atomic replacement where the filesystem supports it. Disable the module to stop file exports.

Sample file fields: `schema=1`, `scope=counter-only-no-identity`, and `count.client_tick=...`, `count.extra_attack=...`. This allows basic acceptance evidence without private game-state capture. Counters reflect **client calls**, not verified server action receipt; do not treat a count of `extra_attack` as a measured network CPS or successful combat hit.

## Regression and acceptance

`RuntimeSafetyTests` on Java17/21 targets Java 8 bytecode and verifies deterministic rearm/focus/GUI transitions, conflicts, generation changes, 8,550 randomized input states, monotonic counter writes, privacy schema, bounded save/backup and repeated disconnect behavior. Forge CI checks versioned 0.8.0 reobfuscated mod JAR. These verify source/packaging, **not** actual Minecraft GUI launch or event-thread identity in a running Forge client.

At the next **consolidated real-game milestone**, install the GitHub Forge artifact in an authorized 1.8.9 Forge 11.15.1.2318 instance; enable the diagnostics overlay; verify transition/menu/click scenarios and behavior over a meaningful play session. Gather latest.log, screenshots, FPS estimates and the local counter-only report as runtime evidence for Issues #2, #4 and #5. No repetitive mini-tests are needed after each source PR.
