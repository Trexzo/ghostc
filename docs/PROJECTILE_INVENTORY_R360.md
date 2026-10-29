# GhostC R3.60 — projectile inventory integrity

**Parent:** exact CI-green R3.59 #193, commit `0e95735ab4add236e8fb250faae28fbc85bea021`. Changes remain on an isolated GitHub-only draft, not `main`, local installation or visual branches.

## Source failure mode

Before R3.60, AutoRod and AutoSnowball ensured the item *type* was a fishing rod/snowball at SELECT and USE but did not bind the specific native `ItemStack` instance or quantity. An inventory update could replace the rod/snowball with another of the same type, alter count or damage, or change the original hotbar slot during the staged transaction. Even after R3.58’s manual wheel/key input priority and R3.59's exact target fence, this could make the later use or restore act upon a different item from the one that was originally accepted.

## R3.60 runtime behavior

Both native Forge controllers now use a pure owner-thread, session-local `ProjectileInventoryFence`:
- **After native SELECT:** retain, in memory only, the original hotbar slot, its precise `ItemStack` reference/item reference/count/damage (or the fact the slot is empty), the selected projectile slot and its exact stack/item reference/count/damage, world generation and selection tick.
- **Before USE:** accept only that same original slot and projectile slot, exact same ItemStack and Item identities and unchanged quantity/damage on BOTH stacks, on the exact following client END tick and the same generation. An equivalent new fishing rod/snowball, an edited original sword slot, a relocated item, premature consumption or missing stack is **not** authorization. USE permission is one-shot even on denial and still requires R3.59 original target identity and fresh native visibility/range checks. There is no automated retry.
- **After genuine vanilla `PlayerControllerMP.sendUseItem` success:** record a local consumed-use receipt in the fence. The native method is still the single item-use dispatch; no packets, key state, or movement are modified.
- **At RESTORE or cancel:** restore the prior hotbar selection only if the original source slot still contains the identical stack/item/count/damage and the selected projectile slot is still the same object. After a *successful* native throw, allow the projectile slot to be `null` (the last snowball was consumed); this is expected and does **not** prevent restoration. An unrelated replacement in the projectile slot, changed source slot, manual wheel/key/use input, changed world/player or stale epoch forbids overwriting the player's chosen slot.
- **F6 Panic / GUI / focus / module disable / world swap:** prior reset semantics are preserved, now also clearing in-memory stack identity references. No item data, player names, UUIDs or stack references are serialized or logged.

There is no additional module or setting; AutoRod and AutoSnowball keep their conservative default OFF, independent F7 controls, WTap, shared action arbiter and original-native multiplayer behavior. If a mod swaps ItemStack objects even without changing displayed inventory, R3.60 will conservatively veto that use; original launched Minecraft acceptance will determine if this is too strict in particular real clients/servers.

## Verification

- `ProjectileInventoryFenceTests`: original/selected stack replacement with equivalent item, quantity and damage edits, source-slot changes, wrong epoch/tick, duplicate and invalid native USE, same-world conditional restore, complete last snowball depletion, absent initial source stack, unapproved native success, owner-thread violations. Includes staged genuine `AutoRodPolicy` and `AutoSnowballPolicy` SELECT → vetoed USE / accepted USE → RESTORE integration plus **60,000 deterministic independently verified inventory mutation frames**.
- `NativeProjectileInventoryFenceTests`: mandatory Java8 Forge 1.8.9 linkage for original `ItemStack.stackSize`, `getItem`, `getItemDamage`, `ItemFishingRod` and `ItemSnowball`; attached to `check` on both Linux/Windows.
- Mandatory `tools/test_forge_wiring.py` asserts source and projectile identity binding, checked-before-use order, bounded permission, safe restoration and that the probe is enabled.
- Exact-head Core Java17/21, Forge 1.8.9 Linux/Windows Java8 build/reobfuscation/native probes and downloaded Windows artifact acceptance rehearsal must all pass. **Real launched Minecraft gameplay and server acceptance remain unverified.**

No merge, no force-push, no installation or changes to parallel visual PRs.
