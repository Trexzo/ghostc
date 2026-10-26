# GhostC R3.33 — Block Hit and Hit Select mode depth

**Stacked Draft, not a release.** Base: combat R3.32 branch at `9bd4aa0e4c932a604dcdc008cdee815192830e1b`. Keep the independently active visual PR #164 and Panic PR #165 untouched. CI proves source behavior and Forge compilation, **not actual original Minecraft 1.8.9 gameplay**.

## Block Hit — `combat.blockhit`

The original `interval` mode and its conservative first-held-press timing remain the default. Additional selectable, opt-in modes:

| Mode | Trigger | Exact behavior |
|---|---|---|
| `interval` | Time while attack is held | Unchanged periodic sword-use intent, with extra-attack precedence |
| `post_attack` | A confirmed **queued extra AutoClicker pulse** | Arm a sword-use intent for the next eligible client tick, never the same tick as the extra attack |
| `on_damage` | A **rising local player's `hurtTime` edge** | React to an actual observed local damage indication, no predictive packet manipulation |

Event-triggered modes require a held attack binding, a sword, no native use-item hold, and a valid target if `require_target` is enabled. They preserve the `interval_ticks` slider as a minimum delay between emitted use intents. Pending triggers expire after three client ticks, cannot replay after a release, GUI/focus loss, world change or module reset, and cannot spend a use pulse on the same tick as an extra attack. The controller still queues actions through GhostC's shared ActionCoordinator and invokes normal vanilla `KeyBinding.onTick`, **not packet injection**. `post_attack` deliberately does not claim to observe all manual mouse attacks.

## Hit Select — `combat.hitselect`

`normal` is the original default. `ready` admits extra clicker pulses only if the target's `hurtTime == 0` (as well as other existing gates). `trade` requires local `hurtTime >= trade_min_self_hurt` (1–10) and target `hurtTime <= max_hurt_time` (0–10). Every mode obeys the existing `min_gap_ticks`, optional target-presence setting, current-world identity gates, and owner-thread action lifecycle.

**Scope limit:** Hit Select controls **extra AutoClicker pulses only**. It cannot cancel Minecraft's ordinary manual attack without a separately reviewed native attack boundary. Do not describe this as full manual Hit Select parity with Vape/Slinky.

## Regression and integration

- `CombatMoreTests` still executes all existing original-policy assertions and invokes `CombatModesTests` under the existing Java17/Java21 Core CI workflow. No CI workflow YAML changes.
- Deterministic traces cover attack and local-damage event provenance, interval cooldown, rising-edge detection, no same-tick double attack/use, three-tick expiry, release/GUI/world resets, invalid inputs, owner-thread fencing and persisted GUI mode/threshold settings.
- Forge 1.8.9 Linux and Windows JDK8 build/reobfuscate plus downloaded artifact rehearsal remain mandatory.
- Branch dependencies: do **not** merge stacked PR R3.33 before R3.32; retain full manual integration of visual #164's `HostRuntime.java` and Core workflow changes. Panic #165 is independently owned.

## Original Minecraft acceptance still needed

On an authorized controlled Windows Minecraft 1.8.9 instance, measure actual emitted extra attack/use pulses at 20 TPS, confirm F7 settings roundtrip and live effects, test target hurtTime and local hurtTime during PvP, test sword switching, manual right-use priority, chat/F7 focus loss, reconnect, low FPS, and confirm that default profiles still behave like the R0.7 cadence. Never claim anticheat bypass, undetectability, measured performance, or full competitor parity based on an offline suite.
