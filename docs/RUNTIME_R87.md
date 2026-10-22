# GhostC R2.67 — optional Target HUD teammate exclusion

## Gameplay-facing issue

After the optional R2.66 Player ESP `ignore_teammates` was added, the Target HUD could still display the nearest ally as its card, even when the user's intent was to monitor another nearby player. Simply hiding an already-selected teammate would also make the HUD card disappear instead of choosing the next eligible non-teammate.

## Behavior

- Add `hud.target.ignore_teammates` Boolean setting, **false** by default, independent of `render.esp.ignore_teammates`. Old profiles, colors and visuals are untouched until the user opts in.
- **Nearest** mode: a new Java8 `TargetHudPolicy.select(...,IntPredicate)` evaluates the stable snapshot eligibility gates and uses the exact-world lookup to skip teammate candidate IDs, preserving nearest distance and stable entity-ID tie-breaking. A nearby teammate no longer masks an eligible non-teammate farther away.
- **Crosshair** mode: admits only the existing snapshot-eligible ID under the reticle, if not a teammate. It never falls back to a different player.
- The pure team membership comparison is reused from R2.66 `EspTeamPolicy`: both players must have the **same non-null scoreboard Team object**. Neither same-colored names, display labels nor missing teams are enough for a match.
- The Forge adapter holds one reusable render-thread predicate instead of allocating a new one each frame; only when enabled does it perform current-world ID and team lookups. The selected live target's membership is checked **again** immediately before painting to avoid drawing a newly joined teammate.
- This is a read-only HUD filter. ESP, combat logic, targeting/rotations, game packets and selected HUD card dimensions are unchanged.

## Verification

- `TargetHudTeamFilterTests`: teammate nearest masking regression, crosshair strictness, tie-break, null predicate, invalid range/mode, legacy parity, 30,000 seeded crowded snapshots against an independent oracle, persisted opt-in and reset-to-default.
- Forge wiring checks: one reusable predicate, exact live-world team lookup, separate opt-in fast path, second paint-time fence, source-order and existing text/health layout.
- Exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 build/reobfuscate/JAR verify plus fake-profile install/rollback, exact matching source trees before/after merge and post-merge CI.

Actual Minecraft 1.8.9 team behavior, UI visuals, FPS, compatibility and long-session stability are not in-game verified by CI.
