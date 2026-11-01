# GhostC R3.73 — native-identity proof for uncertain pulse recovery

Parent: green R3.72 draft PR #210, commit `e7a9c41e4c113da57960e2c4f072118c10416ef0`. GitHub combat-only, no main merge, visual changes, installation or release.

## Observed gap
R3.72 survived ordinary controller resets, Panic and module toggles. However, `SessionServices.generation` increments on WorldIdentityTracker LEAVE and ENTER, even if a transient null frame is followed by the exact same native world/player instances. R3.72 accepted the increased number alone and cleared uncertain ATTACK/USE. This could replay a potentially emitted native pulse in an unchanged Minecraft context.

## Fix
Every Forge native ATTACK/USE pulse now enters through `NativePulseSettlement.admitNative(lane,generation,tickWorld,player)`. After first native pinning, a numerically larger generation *alone* cannot clear an uncertain lane. Recovery requires both an advanced generation and a different live native world or player object. A same-epoch object replacement is rejected until WorldIdentityTracker has transitioned the session. Weak references avoid holding an abandoned world in memory. Stale generations are rejected; an in-flight pulse across a new generation is treated as uncertain until verified identity change. The two lanes remain independent. Pure legacy `admit` calls cannot bypass a pinned uncertain state.

## Verification
`NativePulseRecoveryTests` exercises real HostRuntime, PanicController, explicit re-enable, FeatureFaultGuard exception quarantine/re-enable, GUI/focus transitions, transient leave/reenter of identical native objects, genuine new native identity, stale-epoch denial and 90,000 seeded admission/failure traces. Required Forge Java8 native KeyBinding probe checks actual Minecraft input queue plus the SessionServices epoch and identity fence. Source wiring requires all three native call sites. Require exact-head Core Java17/21, Forge Windows/Linux Java8, Windows downloaded artifact acceptance.

Headless only; no launched Minecraft, real mouse timing, server effect or ACK verified.
