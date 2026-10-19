# GhostC R1.4 — exact world identity and fresh snapshot admission

## Source audit finding

In R1.3, the Forge end-client-tick adapter reconciled `worldPresent` from `theWorld != null && thePlayer != null`. A dimension change, world replacement or respawn that swaps Minecraft's world/player *object identities* without an intermediate absent tick could therefore leave the old `SessionServices.generation` active. Camera and input modules could carry old session ownership into the new world. Separately, if the local player pose was NaN/infinite or outside the valid pitch range, constructing a `WorldSnapshot` could throw during the main tick; if snapshot creation was skipped, a previous-tick snapshot could remain visible to consumers.

## R1.4 implementation

- New owner-thread `WorldIdentityTracker` observes the exact **world reference + local player reference**, without usernames, server addresses, locations or other private data. Its transitions are `ABSENT`, `ENTER`, `STABLE`, `SWITCH`, and `LEAVE`. A null world or player is never admitted.
- Forge end client tick now applies `LEAVE` or `SWITCH` by invalidating the prior `HostRuntime.worldPresence(false)` generation and clearing input/feature timers **before** allowing a new world. `SWITCH` then admits the new generation with `worldPresence(true)` even without a null frame. Stable observations avoid unnecessary session churn. Queued network disconnects reset the identity tracker only when no newer world+player is present.
- `SessionServices.tickAdvance()` invalidates the immutable target snapshot on **every active tick** before publishers run. A missed/failed producer no longer leaves a prior-frame target eligible for Aim Assist or HUD selection. Existing action/rotation TTL policy is independently preserved.
- `WorldSnapshot.validPlayerPose` validates finite local x/y/z, yaw and pitch, and pitch within ±90°. Forge checks it before snapshot publication; invalid local pose causes the snapshot to remain absent for that tick.
- `ForgeCombatController` rejects all synthetic camera, attack and use work when the current generation has no freshly published snapshot for the **exact current tick**. It resets/cancels owned timers and intents in that case. Vanilla physical key input is unaffected.
- Forge's entity copy skips duplicate entity IDs and invalid/nonfinite candidate positions as before; dead players are now ignored during enumeration.
- ESP verifies finite camera viewer coordinates and interpolated candidate/bounding-box geometry before submitting OpenGL vertices, so bad entity coordinates cannot directly produce NaN or infinity geometry.

## Verification

`WorldLifecycleTests` on JDK17/21 includes explicit world swaps without an absent frame, player recreation, partial availability, scheduled disconnect reset, owner-thread guards, malformed local poses, snapshot freshness across ticks, and **13,000 seeded world/player transition frames** checking model-to-host session generation parity. The current G3 coordinator semantics are also checked: its TTL behavior remains independent of snapshots.

`tools/test_forge_wiring.py` adds direct source checks for real Forge world identity observation, double-transition invalidation, fresh-tick combat gate, duplicate entity ID filtering and finite ESP geometry. These join the Core and Forge Linux/Windows CI gates. Version metadata is **1.4.0**.

GitHub Actions still compiles/reobfuscates the Java 8 Minecraft Forge 1.8.9 artifact on both Linux and Windows, performs full JAR CRC/Java-8 bytecode inventory checks and rehearses SHA-256-gated install, evidence collection and rollback on a **fake** Windows profile.

## Remaining acceptance limitations

This is not an actual Minecraft launch test. CI does not recreate Forge dimension-teleport timing, native rendering, Windows input events, Minecraft account authentication, server behavior or 60-minute FPS/soak conditions. The latest successful `ghostc-forge189-1.4.0.jar` remains labelled **unverified-ingame**. At the next consolidated authorized local Minecraft 1.8.9 test, record join → dimension transfer → respawn → reconnect events with the optional diagnostics counters and check no stale camera/click activity. See `docs/RUNTIME_R10.md` for safe installation/rollback; report actual evidence in Issues #2–6. Do not close runtime issues based on CI alone.
