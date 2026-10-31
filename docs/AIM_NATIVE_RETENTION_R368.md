# GhostC R3.68 — exact native Aim Assist retention across player replacement

**Stacked parent:** exact CI-green R3.67 draft PR #201 at `ebc3e658055a93db0d0e44095d819ad571f1593c`. This is a GitHub-only unmerged combat branch; no main, local Minecraft installation, HUD, ClickGUI or ArrayList work was modified.

## Actual gap in verified source

In the original native Aim Assist path, `AimTargetSwitchPolicy` retained a numeric entity ID + UUID for the **committed** camera target and for the **candidate** being switched to. `AimTargetLock.sameIdentity` likewise treated an equal UUID as evidence that a hard-locked player was still the same target. However, the live Minecraft `EntityPlayer` may be re-instantiated while retaining an identical UUID and entity ID. The older pure policies would allow its fresh native object to inherit the previous instance's target-switch dwell or hard-lock preference, even though the instantaneous `LiveCombatTargetGate` correctly checked that the CURRENT object was in the world.

R3.67 fixed the separate **TriggerBot-to-Aim camera handback**; R3.68 strengthens normal Aim target retention when Aim Assist never yielded.

## Runtime fix

- `AimTargetSwitchPolicy` now offers `considerNative` / `appliedNative`, which include the exact original Minecraft `EntityPlayer` and `World` object references alongside UUID and numeric entity ID. Native candidate continuity and committed-target equality require **all four fields**, not UUID alone. A replaced object, even with equal UUID and ID, must earn an entirely fresh configured positive `switch_delay_ticks` before a camera write. A candidate losing a CAMERA request cannot commit, and its prepared reference is cleared.
- The old pure-core `consider` / `applied` signatures and their tests remain supported without claiming that UUID-only calls have verified native identity. In particular, `switch_delay_ticks=0` deliberately retains the existing immediate-switch behavior. An ordinary same-object candidate may still retain the existing camera target with no artificial delay. An A→B→A proposal while A is still **the actually committed** owner is safely allowed to continue A without converting uncommitted B into a new owner.
- `AimTargetLock` adds `sameNativeIdentity` / `appliedNative`: the hard-lock preferred-ID hint is valid only if the current exact native object and world still match the originally committed camera rotation. On mismatch, the Forge controller clears the hint; new targets still go through every existing native range/FOV/visibility, pose, and rotation-owner guard. Hard lock retains its prior immediate native switch behavior when no positive delay is requested.
- The actual `ForgeCombatController` uses these native methods at retained-target lookup, target-switch admission, and **after** winning `RotationCoordinator.Scope.CAMERA` output. Both transient native references are cleared by existing world/focus/Panic/disabled/manual-camera/TriggerBot-yield reset paths; no UUID or game object is exported or persisted.
- `AimCameraReturnFence` R3.67 and `AimManualInputPolicy` continue independently: manual mouse input and TriggerBot camera yield take precedence; the new exact-identity fence does not override those policies.

No new UI settings, default-enabled modules, input simulation, packet mutation, reach changes, rotations beyond existing Aim Assist, target automation, retry or installation. All gameplay actions remain gated by original Minecraft local session and the real snapshot + current-world pose tests.

## Required verification

- `AimNativeRetentionTests`: **80,000 deterministic native object/world/UUID/ID target-switch episodes**, including same-UUID respawn, rapid A→B→A, world replacement, exact dwell deadline and real-owner camera commit, plus explicit native hard lock and manual mouse handback integration, world/Panic resets, rejected rotation and owner-thread failures.
- Forge Java8 `NativeAimRetentionTests`: links the actual original Minecraft 1.8.9 `Entity.getEntityId/getUniqueID`, `World.getEntityByID`, `MovingObjectPosition.entityHit`, and camera yaw/pitch fields to the native Aim switch/lock pure policies. This runs under mandatory `forge189 check`; it does NOT launch Minecraft or validate a server.
- Python Forge source wiring requires the correct **exact-object/world** native methods, current-world target check, manual-input precedence and ordering from proposal through a successful camera rotation, plus test inclusion.
- Require exact-head Core Java17/21 + Forge Linux/Windows JDK8 compilation, reobfuscation, native tests and downloaded Windows-artifact prepare/rollback rehearsal before calling this CI verified.

## Genuine original Minecraft runtime acceptance (NOT performed in headless CI)

In a separately authorized Forge 1.8.9 client, verify: switching between two real nearby players with positive vs zero switch delay; same-UUID respawn or re-instantiation; same-ID replacement, manual mouse turns, F7 toggles, world disconnect/focus loss, TriggerBot crosshair handback and Aim target lock preference. Record exact candidate commit and SHA-256 of mod JAR and independent gameplay pass/fail evidence. Do not call native KeyBinding/camera class linkage a real multiplayer test or a successful PvP hit.
