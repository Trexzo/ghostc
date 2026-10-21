# GhostC R2.44 — independent camera and outbound rotation intents

## Source-audited G3 correctness defect

`RotationCoordinator` deliberately declares two arbitration scopes: `CAMERA` (view rotation) and `OUTBOUND` (transport-facing rotation intent). Yet its `LinkedHashMap<String,Request>` was keyed only by the owner module ID. A module publishing one camera and one outbound request would silently overwrite the first request with the second, even when both requests were valid in the same tick and generation. This violates the advertised independent scope contract and can cause a valid camera or outbound intent to disappear before arbitration.

Unlike `ActionCoordinator`, `RotationCoordinator` had no pending request limit. Distinct caller IDs in a long-running session could grow the map without a capacity gate until the next prune. These are core service contract issues, independent of whether a Forge feature currently uses both channels.

## R2.44 changes

- Encode a unique stable map identity from `(owner, scope)`. The existing `ModuleRegistry.validateId` owner identifier restriction and fixed Java enum scope prevent collisions.
- A repeat request for the same owner **and same scope** replaces that scope's previous request. The other scope from the same owner is left untouched. Existing priority arbitration, session generation, inclusive start/exclusive expiry, and deterministic tie-breaking by ordinal remain unchanged.
- `cancel(owner)` revokes *both* `CAMERA` and `OUTBOUND` requests; `clear`, `prune`, and `shutdown` already work over all map entries and remain unchanged.
- Bound the request map at **256** simultaneous owner-scope entries, analogous to ActionCoordinator's existing limit. Creating a 257th different entry fails closed with an explicit exception. Replacing an existing (owner, scope) at full capacity is still allowed. Requests do not accumulate forever when expired via normal owner tick pruning.
- No new Minecraft hooks, packet injection, camera writes, gameplay logic, persisted setting keys, or user-facing GUI controls.

## Regressions and verification

Extended `CoordinatorHygieneTests` validates:
- A single owner can have two simultaneous and independently selected CAMERA and OUTBOUND intentions.
- Replacing its camera intention does not disturb the outbound intention; pruning a shorter camera expiry leaves longer outbound live; cancel(owner) removes both.
- Filling 128 owners × 2 scopes reaches 256; the next distinct request is rejected without mutation; replacing an existing request is permitted; cancellation frees exactly two slots and reuse works.
- A **4,500-tick** replay repeatedly publishes paired intents across 40 owners, changes session generation, prunes old requests and confirms both arbitration scopes remain available and bounded.
- Static source regression ensures the map keys include scope, cancellation handles both scopes, and the 256 capacity guard is present.

Require green **exact PR-head** Java 17/21 Core tests and Linux/Windows JDK8 Forge build, JAR integrity/provenance and simulated Windows acceptance/rollback. Compare the merged `main` tree against the green PR head; check separate `main` CI if GitHub Actions schedules it. GitHub-connected merges may not always trigger post-merge CI, so do **not** claim it succeeded unless actual runs are visible.

## Boundary

Deterministic service correctness is not proof that any Minecraft adapter consumes `OUTBOUND` rotation or that camera changes work in live gameplay. No actual Minecraft launch, measured FPS, visual parity, compatibility or 60-minute soak has been verified. G1/G2/G4/G5 live gates remain open.
