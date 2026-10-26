# GhostC R3.37 — WTap / Sprint Reset

**Genuinely wired Forge 1.8.9 module** under F7 → COMBAT, disabled by default and based on R3.36 Velocity Draft PR #169. Original visual workstream, main and prior stacked PRs are untouched.

## Native trigger and behavior

WTap is driven by `net.minecraftforge.event.entity.player.AttackEntityEvent`, delivered to the existing Forge event bus listener after Minecraft performs the local attack path. This is not a simple mouse-click counter: the event requires the attacker's exact native local player identity and a **live current-world target entity object**, which must be a living entity with health and not dead.

If the local player was sprinting, moving forward on solid ground, healthy enough to sprint, not using an item/sneaking/flying/riding/swimming/on a ladder/blinded or colliding with a wall, the WTap controller sends a normal `EntityPlayerSP.setSprinting(false)` to reset sprinting once. The owner-thread `WTapPolicy` then holds sprint off for `reset_ticks` (1–3, default 1) and calls `setSprinting(true)` when the delay elapses **only while the same native movement eligibility remains satisfied**.

If Auto Sprint (another GhostC module) sets sprinting true before the WTap delay expires, the WTap tick runs after Auto Sprint's tick and maintains the hold. Once WTap completes, vanilla movement and optional Auto Sprint remain authoritative. No attack packet, entity hit, velocity, click cadence, mouse bind or server command is changed by WTap. The client cannot guarantee that a server accepted a specific attack or applied extra sprint-hit knockback without real in-game evidence.

WTap cancels any pending reengage if the module is disabled or faulted, the player releases forward, ground eligibility is lost, any GUI opens, focus leaves Minecraft, F6 Panic is invoked, or the world/connection/player identity changes. It rejects repeated events in one client tick, never queues long delayed tasks, never writes attack packets and never suppresses manual attacks. Resetting a pending WTap action does not force sprint back on; a player can reenable sprint manually.

## Regression and limits

`WTapTests` runs 26,000 randomized state-machine timing decisions plus duplicate-attack, 1–3 tick delays, eligibility changes, owner-thread protection, cross-world epoch reset, overflow guards and profile roundtrip. It is invoked through `IntegratedModuleCatalogTests`, which also tests that F6 Panic persists disabled Velocity/WTap and preserves both modules' numerical settings.

Forge CI compiles/reobfuscates the actual native `ForgeWTapController` against Java 8. **This does not prove Minecraft 1.8.9 AttackEntityEvent is delivered at the desired point for every combat situation.** In-game acceptance must measure actual attack, sprint transition, restoration tick, Auto Sprint interaction, simultaneous Block Hit/AutoClicker, high CPS, custom keybinds, focus loss and target world identity on authorized servers. No claims about anticheat bypass, exploit-free extra knockback, FPS, or Vape/Slinky parity until measurements exist.
