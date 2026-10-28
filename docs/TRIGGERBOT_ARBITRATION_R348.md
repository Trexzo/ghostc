# GhostC R3.48 — TriggerBot and exclusive synthetic attack ownership

**Scope:** stacked exclusively on R3.47 PR #180, starting at `977d2cecaa53ae0061f2019d031df0d9b8bd367a`. Draft. The independent HUD/ClickGUI work in PR #164 and every parent branch remain untouched.

## Functional module
- New `combat.triggerbot` is opt-in, **disabled by default**, under COMBAT in the real F7 settings/profile registry.
- A native Minecraft 1.8.9 crosshair hit must be an actual living **player** in the current world, not a nonplayer living mob or stale entity ID. Same object identity is checked before acquisition and again immediately before dispatch.
- Range `3.0` (2.0–4.5), `acquire_ticks=2` (1–10), `cooldown_ticks=4` (2–20), `require_sword=true`, `ignore_teammates=true` by default.
- Requires native visibility, non-invisibility, valid health, focus and GAME scene, no other open GUI, non-held attack key, non-held use key and not already using an item. Invalid/unbound/GUI-shortcut/conflicting attack bindings cannot synthesize clicks.
- Same-team filtering uses the client's present scoreboard team object (when known); it does not claim broader social/friend/team detection beyond what Minecraft exposes.
- Owner-thread `TriggerBotPolicy`: IDLE -> ACQUIRING -> READY -> COOLDOWN. Changing target UUID, changing acquisition setting, losing focus, physical attack hold, world swap, Panic, or disconnect loses acquisition; successful native pulse commits cooldown. A rejected Hit Select / arbitration candidate does not count as a dispatched click.
- No clicks on initial target acquisition, no delayed click queue, no catch-up bursts, and no raw packet manipulation. Dispatch is one `KeyBinding.onTick` through the existing Forge combat owner.

## Explicit cross-module ownership
1. A physical held attack still drives the existing AutoClicker; TriggerBot is denied while the physical button is held. AutoClicker intent priority 20; TriggerBot 10.
2. Both sources propose only to the **existing** session-fenced `ActionCoordinator.Type.ATTACK / Phase.OUTPUT`. At most one winner can be consumed per game tick, including unexpected simultaneous requests.
3. The shared Hit Select policy filters only the **chosen winner**, once per tick, rather than consuming cooldown in two separate controllers. The source is revalidated at the native dispatch boundary.
4. Aim Assist may still move the camera, but TriggerBot refuses that tick after an Aim Assist camera write, avoiding an immediate click from pre-rotation renderer crosshair data.
5. Block Hit remains gated by genuine physical attack hold, valid sword and its own existing action owner. WTap still observes actual Forge AttackEntityEvent, not a speculative TriggerBot intent.
6. Velocity, Bridge Assist, AutoSprint, inventory ownership and all render/HUD modules remain independent; TriggerBot participates in the existing R3.43 gameplay combat grant and fails closed in inventory/GUI/focus transitions.

## Test and evidence
- `TriggerBotTests`: target acquisition, cooldown exact boundaries, same-tick duplicate refusal, UUID swaps, world resets, settings edits, loss of target/hold, owner-thread refusal; plus 50,000 deterministic dual-producer arbitration frames.
- `IntegratedModuleCatalogTests`: registered real settings, disabled-by-default behavior, GUI changes, saved profile roundtrip and reversible F6 Panic, with TriggerBotTests integrated into the Core suite.
- Forge Linux/Windows JDK8 compilation and Core Java17/21 CI required on exact R3.48 head. These prove source/build/native-boundary tests, **not actual launched-game combat, hit registration, anti-cheat behavior or multiplayer server outcome**.
- A later authorized original Minecraft 1.8.9 acceptance pass must inspect mouse hold/release precedence, render/tick crosshair freshness, GUI and F6 resets, target swaps, actual emitted pulses and native WTap/BlockHit interplay. Keep Draft until verified.

No force push, auto-merge, direct main updates, client launcher changes or visual PR modifications.
