# GhostC R2.66 — optional Player ESP teammate suppression

## User-facing feature

Player ESP now offers the typed setting `render.esp.ignore_teammates`, default **false**. Enable it in F7 > Render > Player ESP to hide ESP visuals for players on the same **non-null scoreboard team** as the local player. Other players and everyone without an assigned team continue to render if they meet the normal range, world identity, health, geometry and visibility gates.

No teammate suppression occurs by default, including when a user loads an older profile. This is a *render-only* option, not a targeting, packet or combat change.

## Deterministic correctness and frame safety

- `EspTeamPolicy.exclude` accepts Minecraft scoreboard Team objects through its pure Java8 Object identity comparison. Two players are teammates only when their non-null team objects are identical. A missing team, same-looking display label, team color or player name is insufficient to hide anyone.
- The Forge ESP admission loop evaluates the setting and team membership **after range rejection, before the nearest-N cutoff and LOS rays**. Excluded players consume no nearest slot and do not trigger a potentially costly visibility ray.
- ESP performs a second membership check using live local and target teams after the existing exact-world-object-identity fence and before GL vertex emission. A player joining the local team between collection and painting can no longer retain a stale overlay.
- With the option off, short-circuit evaluation skips both scoreboard team reads and no other ESP style, 80-player budget, RGB, line width, transparency, health bar or overlay geometry changes. Existing Target HUD and combat targeting have unchanged policies.

## Verification

- Java8 `EspTeamPolicyTests`: null teams, reference-distinct teams with equal-looking names, team changes between candidate admission and draw, 200,000 seeded team identity permutations, typed-setting profile save/reload and R2.65 per-setting reset.
- Forge source-wiring asserts option registration, admission before budget and LOS, final live membership before draw, no-team logic, and JAR class presence.
- Require exact PR-head Core Java17/21, Forge Linux/Windows JDK8 compile/reobfuscation, JAR provenance and fake-profile install/rollback, identical merged tree, and post-merge exact main CI.

Actual Minecraft 1.8.9 visual output, scoreboard/mod interoperability, FPS and extended uptime remain unverified until native in-game acceptance.
