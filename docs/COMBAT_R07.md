# GhostC R0.7 — Block Hit, Hit Select and shared input safety

## Implemented and visible in F7 COMBAT

### Block Hit (`combat.blockhit`)
- Default **OFF**; sword required, physical attack binding held, game focused, world connected, vanilla use-item key not down, player not already using an item.
- `interval_ticks`: 2–10, default **4**, rounded to a whole owner-client tick.
- `require_target`: default **true**, requires a living entity under the native crosshair.
- Requests at most one `USE/OUTPUT` intent through the existing `ActionCoordinator`; **only** `ForgeCombatController` may issue the corresponding vanilla `KeyBinding.onTick(useItem)` pulse.
- The first attack-hold tick only arms the timer; leaving the target, dropping the sword, releasing attack, pressing use, disabling the module, losing focus or disconnecting resets the timer.
- When a synthetic AutoClicker ATTACK is queued in the same tick, Block Hit **defers** the USE request rather than delivering simultaneous extra actions.
- No packet interception, forced block state, spoofed item ownership, or continuous right-click key state.

### Hit Select (`combat.hitselect`)
- Default **OFF**; modulates **only supplemental AutoClicker-generated ATTACK pulses**. Never cancels physical/vanilla mouse clicks, so it is not a complete hit-suppression system.
- `max_hurt_time`: integer 0–10, default **2**. Extra pulses require target's native `hurtTime <= max_hurt_time`.
- `min_gap_ticks`: integer 1–8, default **2**, minimum tick distance between approved extras.
- `require_target`: default **true**, requires a living crosshair entity. When false, absent target is treated as hurtTime 0.
- Runs only when AutoClicker's own hold/CPS policy requests an extra pulse; rejected pulses are dropped rather than queued for catch-up.
- Epoch-bound, monotonic tick validation and deterministic replay; resets on loss of host focus/world or module disable.

## Shared input behavior

Forge's single `ForgeCombatController` is responsible for requesting/consuming both `ATTACK/OUTPUT` and `USE/OUTPUT`, with shared `SessionServices` generation fencing. Each type can be consumed at most once per owner tick. Other modules may not inject native keyboard pulses directly. The controller rechecks physical attack hold, use hold, world, focus and equipped sword before native USE, and suppresses synthetic ATTACK while use item is already held/in progress.

The contract deliberately does not intercept Minecraft's `clickMouse()`, game packets, inventory or native mouse input. Accordingly, **Hit Select does not stop manual hits**, Block Hit cannot guarantee server-side sword block state, and timing/click effects may vary with vanilla multiplayer behavior. This project has no anti-cheat evasion algorithms and makes no detection-resistance claims.

## Test and integration gates

`CombatMoreTests` runs on Java 17 and Java 21 using Java 8 bytecode and checks tick cadence, attack/use mutual exclusion, activation/deactivation, corrupted times, session resets, target-hurt-time windows, seeded replay, keybind/profile persistence, wrong-thread rejection, and client-coordinator intent consumption. `Forge 1.8.9 CI` compiles/reobfuscates, checks the module policies are present in the JAR and verifies its bytecode and 0.7.0 metadata.

**In-game validation is still outstanding.** Use a licensed Minecraft 1.8.9 Forge 11.15.1.2318 instance and an authorized local test world, not a competitive server, to check rendered settings, physical click interaction, use-item/block animation, custom bindings, sword-only gating, focus, disconnect/rejoin, latency and actual client tick behavior. Capture logs and observations for Issues #4 and #5 at the next consolidated runtime milestone.

No proprietary client source, binaries, assets, native injector, or packet bypass has been committed.

## G3 coordinator hardening in the same PR

The existing shared ActionCoordinator and RotationCoordinator now attach a start-tick boundary to each request: no intent may be consumed/resolved **before its submission tick**. They also implement explicit owner-thread `prune(tick,generation)`, invoked automatically from `SessionServices.tickAdvance()`. This prevents unconsumed one-tick requests from filling the 256-entry action queue during long sessions, and prevents stale camera/output intents from accumulating. The idempotent session-reset and tick-bound single-consume rules remain unchanged.

`CoordinatorHygieneTests` validates future-start fencing, epoch rejection, expiration, wrong-thread rejection, and 1,800 owner ticks with unique unconsumed requests without queue growth. These are offline contract assertions, not an in-game packet or input-lifecycle test.
