# GhostC R2.47 — snapshot publication and Aim Assist target freshness

## Source-audited runtime race

In R2.46, the Forge world snapshot builder acquired player references into a bounded nearest-N admission buffer, then copied them to immutable `WorldSnapshot.Entity` records. The copy path refreshed health/coordinates but did not recheck **current-world membership by Java object reference**. If a candidate despawned or an entity ID was reused after admission, a stale record could enter the snapshot. A formerly visible player could also become invisible or move outside the active feature range between admission and publication without a second policy check.

Aim Assist subsequently selected an entity by snapshot ID and immediately wrote camera angles without validating that the selected player was still alive/visible/present and still at the coordinates that produced the suggested angles. A stale or newly invisible target therefore could cause a camera movement even though R2.46's crosshair action path was already fenced. No extra packets or network operations are involved.

## Change

1. **WorldSnapshot publication:** In the Forge admission visitor, recheck that the current world resolves the same exact `EntityPlayer` object, health remains finite and positive, and coordinates are finite. Recompute the *current* squared distance to the local player; recalculate `stillVisible = previouslyVisible && !p.isInvisible()`; and reapply the existing `SnapshotDemand.admit(stillVisible,currentDistanceSq)` range and visible/unfiltered policy before publishing. If a target becomes visible only *after* its original visibility ray, it stays conservatively hidden for this tick. Never claim a new LOS ray was made.
2. **Final Aim Assist admission:** Extend existing `LiveCombatTargetGate` with an optional invisibility/visible-only check while retaining the original five-argument crosshair API behavior. After `AimAssistPolicy.suggest`, the Forge combat bridge resolves the current `EntityPlayer` by the suggested ID, requires nonlocal/live/finite-health state and not-invisible when `aim.visible_only` is active, then compares current x/y/z **exactly** to the position from which the immutable suggestion's yaw/pitch were computed. If any check fails, no new camera intent is issued; previous Aim Assist intents were already cancelled before scheduling.
3. **Suggestion pose:** `AimAssistPolicy.Suggestion` now carries three private, immutable target coordinates and exposes `sameTargetPosition(x,y,z)`. Its public entity ID, desired yaw/pitch and angular error remain identical; selecting the best candidate and angular smoothing are unchanged.
4. No changes to physical clicks, combat C/S packets, ESP painter styling, HUD position/profile serialization, or default opt-in settings.

## Deterministic and source wiring coverage

- Existing `CombatTests` now checks that a suggestion accepts its exact original target position and refuses any horizontal/vertical movement and non-finite coordinates.
- Existing `LiveCombatTargetGateTests` adds **125,000 seeded visible-only liveness cases**, explicit same-world acceptance, newly invisible rejection, unrestricted visibility behavior and stale-reference protection, while retaining 160,000 crosshair cases and 10,000 simulated respawns from R2.46.
- Forge wiring tests enforce current-world object identity before snapshot publication, refreshed visibility/range admission, live entity resolution and visible-only gate **before** any camera intent, and the same-tick position comparison. Required classes already ship in the Forge JAR.
- Only merge after the *exact* final PR head passes Core Java17/21 and Linux/Windows JDK8 Forge reobfuscation, JAR integrity/provenance and Windows fake-profile acceptance/rollback. Independently verify merged source tree identity; do not claim post-merge Actions passed unless those runs are actually visible.

## Boundary

These checks target stale snapshot data and final camera-intent eligibility on the Forge client owner thread. They do **not** recheck LOS immediately before the camera write (it comes from the freshly created owner-tick snapshot), nor can a Java8 headless test prove that a Minecraft server behaves a particular way. The snapshot intentionally remains a copy of public numeric data, not a persistent raw Minecraft entity reference. Exact same-tick position comparison can suppress one Aim Assist update when another client-thread handler moves the entity between publication and use; the next tick recomputes a new suggestion.

Real Minecraft 1.8.9 start, live world transitions, visual fidelity, actual FPS, multi-mod interoperability and 60-minute soak remain unverified; G1/G2/G4/G5 runtime acceptance issues stay open.
