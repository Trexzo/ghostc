# GhostC R3.63 — projectile inventory correction provenance and combat handoff

**Stack base:** exact CI-green R3.62 PR #196 commit `d8d9f9c1ccb958a9c06ea773afff9f1972a0a832`. This branch remains GitHub-only, unmerged, draft, and independent of parallel UI/HUD work.

## Specific R3.62 weakness fixed

R3.62's passive post-use snowball observer classified a selected hotbar slot becoming `null` as `LOCAL_DECREASE` unconditionally, even if the original stack held 64 snowballs. It also classified a same-object stack decrement without checking `ItemStack.getItem()`, `getItemDamage()`, the amount removed or the original vanilla `sendUseItem` local-mutation result. This could misleadingly attribute a server inventory correction or another replacement to a specific projectile use.

## R3.63 runtime behavior

New pure `ProjectileInventoryReconciliation.classify` classifies **CLIENT-LOCAL INVENTORY EVIDENCE**, never a confirmed server result. A single snowball depletion observation now requires all of:
- the exact original `ItemStack` instance in the same selected slot, unchanged `Item` instance and item damage/metadata;
- an exactly **one-count** reduction, or a truly empty slot only when the original stack had **one** snowball;
- the already-recorded vanilla native `sendUseItem` result reporting **local stack mutation**; a later decrement after a locally unchanged use is ambiguous, not attributed to the use.

Replaced stacks, changed item identity, changed metadata, a disappearing 2–64 stack, a server refill, an unexpected multi-count correction or delayed decrement following unchanged native use yield existing `Signal.INVENTORY_REPLACED` and the **anonymous ambiguous** diagnostic. Unchanged local inventory remains `Signal.NONE` and expires normally. The existing eight-tick, single-shot session-fenced R3.62 observer is unchanged.

The native sampler now captures the original `Item` identity, damage, count, and R3.61's `localChanged` receipt before handing it to pure reconciliation. These references are **not serialized, logged or exported** and are erased on terminal outcomes, world/player switch, or explicit reset. `LOCAL_DECREASE` still does **not** imply server acceptance, actual projectile spawn, hit or damage.

## Combat cooperation / native handoff

The existing Forge adapter still suppresses optional `ForgeCombatController` attacks/camera on **every** AutoRod/AutoSnowball-owned SELECT, USE and RESTORE tick. Following `combat.reset()`, the original `PhysicalInputGate` requires an actual physical attack **release and fresh press** before AutoClicker synthetic attack pulses may resume; there is no buffered attack or catch-up burst on the restored hotbar. R3.63 adds a regression specifically covering a held attack throughout a rod transaction and target-loss snowball restoration. **No new combat delay is added.** Vanilla manual mouse input is not intercepted, delayed or disabled.

## Mandatory verification and genuine-game acceptance boundaries

- `ProjectileReconciliationTests`: 80,000 deterministic independent stack/correction cases; single decrement, final snowball, multi-count disappear/correction, changed item and damage, creative/unchanged use, later decrease with no native local mutation, same-instance count changes; plus actual Rod/Snowball policy handoff and `PhysicalInputGate`/`FixedClickScheduler` release-required safety.
- Existing `NativeProjectileObservationTests` extended to verify genuine Minecraft 1.8.9 `ItemStack.getItem` and `getItemDamage` signatures under Java8 Forge. `tools/test_forge_wiring.py` now checks the native local-change receipt is plumbed through the exact passive sampler, the reader remains write-free, and optional combat does not run on an owned restoration tick.
- Exact-head Core Java17/21 and Linux/Windows Forge Java8 native-build/reobfuscation and downloaded-artifact rehearsal are required; this does not launch Minecraft.

**Original Minecraft acceptance is still REQUIRED and NOT PERFORMED here.** On a separately authorized launched Minecraft 1.8.9 client with this exact candidate artifact, test:
1. Rod unchanged-stack native cast and delayed hook observation (no duplicated cast, restoration correct).
2. Survival snowball stack of at least 2: exactly one local decrement, original slot restored; no extra optional attack while the transaction is owned.
3. Last snowball: slot becomes empty, original slot restores; no stale item reference reused.
4. Creative snowball and canceled use: unchanged local result does not become a false confirmed hit/decrement.
5. Server inventory reset/refill/removal, changed stack identity, count/multicount correction: only **ambiguous** observation; user slot never overwritten.
6. Rapid manual number-key/wheel/right-click, target switch/out-of-range, GUI/focus change and world swap: no stale throw, no wrong-world observation and no held-input synthetic click after restoration until actual physical release.
7. If an original Minecraft runtime acceptance artifact is produced, preserve exact Git commit and mod-JAR SHA-256 alongside a redacted pass/fail record; never treat Linux/Windows headless CI, method linkage or local counters as live-server evidence.

No packet interception, new item use, retry loop, added combat delay, target prediction, native mouse/key spoofing or persistent tracking was added. The prior eight-tick observation timer is **read-only**; it never owns the hotbar.
