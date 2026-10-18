# GhostC R0.6 — Aim Assist and AutoClicker (still supported in R0.7)

**Implementation:** Two opt-in first-party Minecraft 1.8.9 features in R0.6; Block Hit and Hit Select were added in R0.7. Both are **disabled by default**, exposed under the F7 COMBAT category, saved by the existing profile store, and require a valid focused game world. Forge client-tick is the single consumer; Minecraft, network and render objects never enter pure Java policy classes.

## Aim Assist — `combat.aimassist`
- `range` 2–6 (default **4.5** blocks, measured using world snapshot feet positions)
- `fov` 5–90 (default **30°** combined yaw/pitch error)
- `degrees_per_tick` 0.5–15 (default **4** maximum total angular change per tick)
- `visible_only` default **true**, using line-of-sight captured in immutable snapshots
- `require_click` default **true**, only active while the vanilla attack binding is held

The pure `AimAssistPolicy` calculates angle/error for eligible living players, favors smallest combined angular error with deterministic distance/ID tie-breaks, handles ±180° wrap and produces a bounded suggestion. Only the Forge `ForgeCombatController` can apply a `CAMERA` rotation selected through the `RotationCoordinator` at the current session tick/generation. No outbound packet rewrite, movement prediction, bypass or hidden rotation is implemented. It aims toward an upper-body point estimated at 1.25 blocks above target feet; it does not guarantee an in-game hit.

## AutoClicker — `combat.clicker`
- `cps` 4–15 (default **10**)
- `entity_only` default **true**, only queues presses while the vanilla crosshair is on a living entity

The pure `FixedClickScheduler` uses `System.nanoTime()` and only emits at most one scheduled pulse per client tick while the user's normal attack binding is physically held. It intentionally does **not** click immediately on activation, catch up after lag, or synthesize randomized human timing. The Forge adapter submits an `ATTACK/OUTPUT` intent through the shared `ActionCoordinator` and, if that tick/generation's winner is the Clicker and game focus is valid, invokes vanilla `KeyBinding.onTick` with the bound attack key. It does not directly call player attack, bypass GUI focus, intercept packets or alter Minecraft's attack mechanics. The clicker may interact with Minecraft's normal held-attack behavior; this is **not yet runtime-verified**, especially with custom keybinds and Minecraft multiplayer behavior.

## Lifecycle and safety

- Both modules remain dormant outside a world, inside any GUI, or while Minecraft lacks foreground focus.
- Leaving/disconnecting resets click scheduler and invalidates session rotation/action requests; the host independently resets owner-thread services.
- Disabling either module removes its intent on the next end-of-client-tick.
- The two producers **never mutate Minecraft directly**. The single `ForgeCombatController` resolves and applies intent from the G3 services.
- Default values are deliberately conservative. No bypass logic, anticheat evasion, network hooks, targeting of non-player entities or proprietary donor code exists in this build.
- Respect Minecraft server rules, permissions and applicable fair-play policies; use the provided development build in a controlled/local environment for validation.

## Verification gates

GitHub Core CI compiles `--release 8` and tests on JDK17/21 including deterministic aim geometry, FOV/visibility/range, angular bounds, shuffled entity replay, scheduler timings/focus and module/profile roundtrip. Forge CI uses JDK8 and ForgeGradle 2.1 to compile, reobfuscate and inspect the resulting JAR.

**Critical unverified items:** actual Minecraft 1.8.9 GUI launch; camera feel at varying sensitivity, smooth mouse interaction, keybind changes, CPS cadence in-game, GUI/dropouts, interactions with vanilla click timing, session transitions and live rendering. Compilation does not establish efficacy against server anticheat, accurate CPS in multiplayer, or runtime stability. Keep Issues #4/#5 open until those are independently evaluated.

## Practical acceptance

In an authorized offline/integrated test world (with a second local test player or controlled server), activate Aim Assist and verify a smoothly bounded camera correction only while attack is held; vary FOV/range/speed and visible-only with cover. Activate AutoClicker, test hold/release/re-hold, custom attack key, no GUI clicks, entity-only suppression, and low/high CPS. Confirm no automatic aim/click after disconnect, server rejoin, or disabling; attach reproducible logs and observations to Issues #4 and #5 before marking runtime done.
