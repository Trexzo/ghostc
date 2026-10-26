# GhostC R3.32 — competitive combat controls (Draft, not gameplay-certified)

This milestone follows the source comparison with recovered Slinky and Vape V4.21 reference code. It adds **first-party implementations**, not copied proprietary code. The approved baseline is GhostC 3.0.31 at `0b5e7a0bb7ea3553ee374596c0b50b3dd6812d2e`.

## Real gameplay features added

**AutoClicker — three independent modes under F7 → Combat**
- `pattern=fixed` (default) retains original `cps=10` deterministic `FixedClickScheduler` and all old gates unchanged.
- `pattern=balanced`: sampled CPS in user-selected `min_cps` / `max_cps`, triangular density favoring the middle.
- `pattern=range`: uniformly sampled CPS across those bounds.
- `min_cps=8` and `max_cps=12` defaults, individually editable within 4–15. The Forge owner sorts crossed slider values instead of quarantining a module on temporary UI crossover.
- Pure Java `PatternClickScheduler` has bounded memory, no background thread, per-owner seeded state, no extra press on arm/release/rearm, no catch-up queue after low FPS, no more than one pulse per game tick, explicit mode/interval reset, and opt-in-only behavior.
- The same original `PhysicalInputGate`, `ActionCoordinator`, vanilla `KeyBinding.onTick`, target/focus/use conditions and world lifecycle cancellation remain mandatory. These modes do **not** implement actual independent hardware mouse-down/up events, fake human input or anti-cheat bypass.

**Aim Assist — adjustable axis participation and target point**
- `horizontal_fraction=1.0` and `vertical_fraction=1.0` defaults; each slider ranges 0.1–1.0.
- `target_height=1.25` is now adjustable 0.4–1.7 blocks above target feet.
- Original angular target sorting/FOV, visible-only and `require_click` gates remain. The axis fractions scale candidate yaw/pitch contributions before the original combined per-tick cap, without exceeding target angles; neither silent-aim packets nor target prediction are introduced.
- The original overload delegates to 1.0/1.0 and produces the same arithmetic and output.
- Strict same-frame current-World player identity, target position and local eye/camera pose readback still gate the actual camera write; no tolerance is silently introduced.

## Regression and distribution gates
- New `CombatDepthTests` runs two 10,000-click deterministic independent-seed replays, verifies next-deadline bounds, signed clock extremes, ownership, hold/focus-like disable/reset, mode edits, 1-pulse-only, Aim geometry and both new settings' persistence across an actual profile close/reload.
- CI explicitly runs new tests on JDK 17/21 with `--release 8`; Forge CI compiles/reobfuscates the entire 1.8.9 adapter on Linux/Windows JDK8 and validates the distributed artifact.
- Forge version / `mcmod.info` / on-screen label all become 3.0.32 in this Draft.
- No main mutation or release occurs until CI is green and gameplay acceptance is separately observed.

## Still missing vs Vape/Slinky
- Real Windows Forge 1.8.9 gameplay/focus/CPS/aim-feel/GUI acceptance. Timing-policy tests are **not** real Minecraft measurements.
- Independent target persistence/locking, multipoint prediction, manual hit selection, richer sword-block states.
- Velocity, Bridge Assist, inventory utilities and bounded live packet interception/backtrack.
- Comparable FPS/performance benchmarks and user-experience polish. Do not claim detection/bypass superiority.

## Concrete native acceptance checklist
In a privately controlled Minecraft 1.8.9 world: import an older 3.0.31 profile and check old fixed-mode results, change patterns and CPS bounds with F7, test holding/releasing attack under focus and inventory transitions, test entity-only suppression, switch world while an interval is armed, compare target yaw/pitch with independent axis sliders, test different target heights, and measure actual output CPS vs selected bounds at varied FPS. Record output traces, screen capture, frame times, and log evidence; mark a failure red rather than relaxing the tests.
