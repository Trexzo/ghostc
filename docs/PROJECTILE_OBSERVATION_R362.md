# GhostC R3.62 — delayed local projectile observation (read-only)

**Parent:** exact CI-green R3.61 PR #195 at `c3749aaa779a238f2a8f68ec29eea0eb237d336d`. Changes are an isolated GitHub-only draft; no `main`, parallel visual branches, installation or native Minecraft launch changed.

## What the game really exposes

The original Minecraft 1.8.9 native implementation exposes `EntityPlayer.fishEntity` as a client-visible fishing hook, plus `ItemStack.stackSize` in the local inventory. Both can be sampled after `PlayerControllerMP.sendUseItem` has returned. Neither is a server acknowledgement, authenticated projectile spawn, hit, damage or verified network delivery. The snowball stack may be decremented by the local item-use method before a remote response. In particular, **not observing a hook or decrement within the window does not mean the server rejected the action.**

Relevant original MCP 1.8.9 source:
- [EntityPlayer.fishEntity](https://mc-packet-reference.netlify.app/1.8.9/source/net/minecraft/entity/player/entityplayer/)
- [PlayerControllerMP.sendUseItem](https://mc-packet-reference.netlify.app/1.8.9/source/net/minecraft/client/multiplayer/playercontrollermp/)

## R3.62 implementation

- New pure Java8 owner-thread `ProjectileObservationWindow` holds a **bounded eight-client-tick** session-generation/tick permission, independent of hotbar ownership. Outcomes are `PENDING`, `LOCAL_HOOK`, `LOCAL_DECREASE`, `AMBIGUOUS_REPLACEMENT`, `EXPIRED` and `INVALIDATED`. The same-tick frame cannot claim observation, and each pending window terminates at most once.
- New `ForgeProjectileObservationSampler` reads **only** genuine native world/player identity, current-player `fishEntity` (alive), and the exact snowball source slot/stack reference/count. A depleted or vanished original snowball stack is **local inventory evidence**, while a replaced/upsized stack yields **ambiguous inventory state** (not success). Disconnected/recreated world or player revokes the observation. No item replacement, slot change, targeting, packet interception, anti-detection or native action is performed.
- AutoRod and AutoSnowball arm the sampler only **after their original vanilla `sendUseItem` returned and R3.61’s policy/inventory native-use receipt committed**. The sampler is called each standard client END tick **after** `host.tick()`, separate from the preexisting temporary SELECT → USE → RESTORE hotbar state. It may observe evidence even after the user’s original hotbar slot is fully restored or the optional feature is disabled. A subsequent genuine cast supersedes the older inconclusive window in its own lane; no automatic retry is added.
- World/character leave/switch and explicit disconnect discard observation references. GUI/focus toggles do not keep projectile hotbar ownership alive, while passive reads remain non-mutating. The original 1.8.9 client’s physical input/target/stack/WTap/arbitration guards remain unchanged.
- Optional diagnostics get **anonymous aggregate local-only counters**: `PROJECTILE_ROD_LOCAL_HOOK`, `PROJECTILE_SNOWBALL_LOCAL_DECREASE`, `PROJECTILE_OBSERVATION_AMBIGUOUS`, `PROJECTILE_OBSERVATION_EXPIRED`, `PROJECTILE_OBSERVATION_INVALIDATED`. No player name, UUID, item instance/slot, target, server, packet or timing/position is logged or exported. These counters must never be displayed as “confirmed hit,” “server accepted” or “packet ACK.”

## Required proof and limits

- `ProjectileObservationTests`: 80,000 seeded bounded observation runs with hook, depletion, replacement, timeout, world/session invalidation; owner-thread enforcement and genuine Rod/Snowball policy SELECT → USE → RESTORE completing independently of observation.
- Mandatory Java8 `NativeProjectileObservationTests`: original Minecraft 1.8.9 `EntityPlayer.fishEntity`, `EntityFishHook`, `ItemStack.stackSize`, native `World.getEntityByID`, `Entity.isDead` linkage. All through ForgeGradle `check`.
- Mandatory `tools/test_forge_wiring.py`: verifies read-only sampler, no packet/hotbar writes, correct END-tick/world lifecycle, both actual native controllers, eight-tick and one-shot outcomes.
- Require exact-head Core Java17/21 and Forge 1.8.9 Linux/Windows Java8 compile/reobfuscation/native probes plus downloaded Windows artifact rehearsal. **No real multiplayer projectile lifecycle, server ACK, client/server reconciliation correctness or PvP hit is proven by these headless tests.** A separate launched original Minecraft acceptance is still required.

Keep PR in Draft. No merge, force-push, local installation or visual-branch modifications.
