# GhostC R3.59 — same-target, next-tick projectile use

**Source:** isolated GitHub draft stacked on CI-green R3.58 #192, exact parent commit `3568d6ddbca383fb3ac73b0fd2e724a414586c4e`. No changes to `main`, independent visual PRs or connected PC.

## The specific gap in R3.57–R3.58

AutoRod and AutoSnowball already checked native crosshair, current-world target object, health, team, visibility and range before a SELECT or USE step. However, each check previously accepted **any currently eligible player**. If the crosshair moved from opponent A to a different valid opponent B between selecting the item and using it on the next END tick, the controller could throw at B despite the attack press having selected A. If A moved out of range or was unloaded, target admission generally prevented use, but no explicit session/target or timing proof was retained.

## Runtime change (both projectile modules)

- New pure owner-thread `ProjectileTargetFence` captures **only in process** the original `EntityPlayer.getUniqueID()`, `getEntityId()`, session generation and selected tick; it is cleared on restoration, cancellation, module reset and world change. No UUID, name or target ID is exported, persisted or logged.
- A successful native SELECT commits the corresponding existing policy first and then records the exact native target. A second unconsumed SELECT is refused.
- The next USE stage must return to **that same UUID and native ID**, on **exactly the next client END tick** in the same session generation, and must pass a fresh native current-world object lookup, visible/live/healthy opponent, teammate exclusion and configured range. Target switch, disappearance, range loss, timeout, or changed world causes **no native `sendUseItem`**. The existing policy transitions to RESTORE on target invalidity, or `cancelPrepared()` and a subsequent safe RESTORE on a switched-but-valid opponent.
- The fence is **single use**: a denied or approved USE cannot be attempted a second time, even in the same tick. There is no delayed cast queue, target acquisition bypass, guessed impact, packet injection, movement change, camera rotation or extra combat automation. AutoRod's original `fishEntity` guard, AutoSnowball's native item eligibility and the R3.58 human-input/WTap/projectile-owner arbiter remain unchanged.

This is intentionally conservative: rapid mouse movement or target changes may cancel a cast. It is safer than using a rod/snowball at a different player from the originally selected one. It does **not** perform aim prediction or autonomous target switching.

## Tests and limits

- `ProjectileTargetFenceTests`: direct boundary/owner-thread checks; wrong UUID, changed native entity ID, bad world epoch, out of range/invisible/dead native admission, same tick, one-tick late, duplicate USE, duplicate selection; explicit genuine `AutoRodPolicy` and `AutoSnowballPolicy` SELECT → denied USE → safe RESTORE integration; **80,000 deterministic mixed target/timing samples**.
- Mandatory ForgeGradle `nativeProjectileTargetFenceProbe`: actual Minecraft 1.8.9 Java8 `EntityPlayer.getUniqueID`, `Entity.getEntityId`, `World.getEntityByID` class-linkage and fence check. The native controllers also compile/reobfuscate under ForgeGradle.
- `tools/test_forge_wiring.py` verifies original-target recording, target-match gate, fresh crosshair/world/range checks and mandatory tests cannot silently be disconnected.
- Exact-head Core Java17/21 and Forge Linux/Windows Java8 CI, downloaded Windows artifact acceptance rehearsal required. **No real original Minecraft gameplay was launched; source and headless JVM tests cannot prove server projectile spawn, hit detection, or PvP advantage.**

Keep PR in Draft until original-game acceptance. No local install, merge or visual changes.
