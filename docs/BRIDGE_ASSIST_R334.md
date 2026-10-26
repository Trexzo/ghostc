# GhostC R3.34 — opt-in Bridge Assist for Minecraft 1.8.9

**Draft stacked branch:** `feature/ghostc-r3.34-bridge-assist` is based on exact R3.33 combat head `f0b2ff6988e5a63fabc18356bf9c2dd21349ed7b`. Do not merge this before its parent (#166 / #163), and do not overwrite the separate visual #164 or Panic #165 branch. `main` is intentionally unchanged.

## Player-visible feature

New **Bridge Assist** module under **MOVEMENT** in F7 ClickGUI, **disabled by default**, with the following persisted settings:

| Setting | Default | Range/behavior |
|---|---|---|
| `lookahead` | 0.75 blocks | 0.35–1.20 blocks, projected in the normalized forward/strafe movement direction |
| `release_delay_ticks` | 2 | 0–8 client ticks, retained on solid ground after a detected edge |
| `require_blocks` | true | While on, requires a positive ItemBlock stack in the held hotbar slot |
| `allow_sideways` | false | Allows pure lateral/strafe bridging. Forward + diagonal input works without it |

### Native implementation

At the end of a focused Minecraft client tick, the Forge adapter consults normal 1.8.9 collision bounding boxes at present and projected foot positions. Only when the player is grounded, on supported terrain, moving in a permitted direction, not flying/riding/in liquid, and an unsupported projected area is detected, the controller *conditionally* holds the normal Sneak `KeyBinding` state. No block is placed and no network packet is modified.

The controller reads the **physical** keyboard or mouse button for the user's active Sneak keybind separately from `KeyBinding.isKeyDown()`, which may contain the synthetic press. It never overrides an actively held human Sneak input and never releases another mod's pre-existing key state. Every synthetic press is owned by exactly one Forge controller; disabling the module, losing focus, opening a GUI, exiting/changing world, a module failure or explicit bridge reset releases owned input (unless the same physical key is held, in which case native input retains authority). Keybind changes discard ownership of the old key before assessing the new one.

All 1.8.9 calls happen on the client owner thread. The pure-Java `BridgeAssistPolicy` is deterministic, bounded to an 8-tick maximum release delay and keyed to the current session generation. It cannot schedule delayed inputs or cause packet/client tick catch-up.

### Scope and known limitations

- Collision lookahead is intentionally conservative and may need tuning for slabs, fences, ladders, staircase bridging, unusual blocks, server lag and mods replacing vanilla Sneak. This implementation is **not** a full SafeWalk, auto-Scaffold, block placer or protection against falling.
- It responds to native collision probes, not real-time server-authoritative floor state, and does not claim reliable online bridging until actual Minecraft playtest evidence exists.
- There is one owner-thread collision lookahead probe per eligible movement tick; a low/high FPS measurement still needs an original Minecraft run.
- No packet interception, reflective loader, anticheat bypass, self-destruct, evidence removal or native injector has been added.

## Regression coverage

`BridgeAssistTests` includes 24,000 randomized edge/manual/ground/forward/debounce oracle decisions plus focus/reset, monotonic session epochs, first-edge activation, zero/max delay, mode default preservation, thread fencing, typed UI and sealed profile roundtrip. It is run by the existing `CombatMoreTests` Core CI entrypoint, without modifying either shared GitHub Actions workflow.

Forge CI must compile and reobfuscate for Java 8 on Linux and Windows, inspect the JAR, and pass the downloaded Windows artifact installation rehearsal. These validate packaging, **not** actual runtime behavior.

## Required native game acceptance

In a private/authorized Minecraft 1.8.9 test world, record keybinding state while approaching a one-block edge with blocks in hand. Test forward, diagonal, pure lateral opt-in, backing off, no blocks, custom Sneak binding (keyboard/mouse), physical Sneak held before/during/after synthetic ownership, falling from slabs or ledges, swimming/flying, player respawn/world change, chat/F7/F6 Panic, disconnected server, 20/60/240+ FPS and low TPS. Observe no stuck Sneak after disable and no unwanted key release. Attach real trace evidence, not just green CI.
