# GhostC R3.61 — correct vanilla projectile-use outcomes

**Stacked base:** CI-green R3.60 #194 at exact commit `d5c1207e25e5f17a13304df5d9377c7d546bbce6`. GitHub-only draft isolated from `main` and all independent HUD/visual branches.

## Source-backed fault

R3.56–R3.60 performed the actual original Minecraft 1.8.9 `PlayerControllerMP.sendUseItem(EntityPlayer,World,ItemStack)` call, but then interpreted the returned boolean as **"cast succeeded"**. A `false` return caused `policy.cancelPrepared()` and marked the locally invoked rod/snowball use as canceled.

Original vanilla 1.8.9's `sendUseItem` does **not** return a server dispatch/acceptance result. In non-spectator mode it syncs the selected item and enqueues its normal C08 use-item call **before** executing `ItemStack.useItemRightClick`; its boolean return compares the resulting local `ItemStack` reference/count. Original `ItemFishingRod.onItemRightClick` returns the same stack for a cast and normally does not decrement it. Therefore `sendUseItem() == false` can indicate an **invoked native rod action with no local stack mutation**, not rejection. Creative-mode or otherwise unchanged snowball stacks have the same classification issue. This is not proof of server receipt or spawned projectile.

Supporting original-game sources:
- `PlayerControllerMP.sendUseItem` (MCP 1.8.9, lines 453–484): https://mc-packet-reference.netlify.app/1.8.9/source/net/minecraft/client/multiplayer/playercontrollermp/
- `ItemFishingRod.onItemRightClick` (MCP 1.8.9, lines 36–60): https://mc-packet-reference.netlify.app/1.8.9/source/net/minecraft/item/itemfishingrod/

## Changed native behavior

- New pure `ProjectileNativeUseResult` class classifies `SPECTATOR_DENIED`, `NOT_INVOKED`, `SUBMITTED_UNCHANGED` and `SUBMITTED_LOCAL_CHANGE`. It never sends or intercepts packets, returns no player/target data and makes no server-success claim.
- Both `ForgeAutoRodController` and `ForgeAutoSnowballController` refuse SELECT or USE in native `PlayerControllerMP.isSpectator()` mode. Following *all* existing guards (fresh same-target UUID/ID, world/LOS/range, genuine physical input, exact selected stack and amount, shared projectile owner, ActionCoordinator), they call **only the original** `sendUseItem`. Once the method returns normally in non-spectator mode, they commit `Action.USE` and `ProjectileInventoryFence.nativeUsed` **regardless of the returned local-stack-change boolean**.
- Native output is recorded as an anonymous `DISPATCHED` *local-method invocation*. After safe RESTORE, a local quantity/ref mutation closes the pending lane as `LOCAL_CHANGE`, whereas a submitted but locally unchanged rod/snowball closes as `LOCAL_COMPLETE`. Preflight veto, exception or restored-but-unsent projectile still leads to cancel/recovery without replay. A method exception remains an unknown outcome, not evidence of submission or a hit.
- This is deliberately **not** a packet-level ACK pipeline; server delivery, collision, projectile spawn and damage require genuine Minecraft multiplayer acceptance and are not asserted by a returned Java boolean.
- R3.58 physical number-key/wheel/use overrides and R3.59 original-target identity, R3.60 source/projectile stack identity, WTap arbitration, conservative restoration and F6 Panic remain intact. Both modules remain default-OFF and F7 configured. No manual input is intercepted or simulated.

## Mandatory evidence

- `ProjectileNativeUseResultTests`: full spectator/no-call/non-spectator true/false truth table, 60,000 deterministic mixed classifications, *actual* `AutoRodPolicy` and `AutoSnowballPolicy` SELECT → USE → RESTORE plus `ProjectileInventoryFence`, and ledger receipt closure for unchanged rod, consumed last snowball and spectator veto.
- Java8 `NativeProjectileUseOutcomeTests` binds original Minecraft 1.8.9 `PlayerControllerMP.sendUseItem`, native `isSpectator` and `ItemFishingRod/ItemSnowball.onItemRightClick` class signatures. Attached to `forge189 check`.
- `tools/test_forge_wiring.py` prevents mistaking a false local return for failed submission and verifies native method is invoked only after spectator, target and stack gates; exact-head Core Java17+21 and Linux/Windows JDK8 Forge/reobf + downloaded Windows artifact acceptance rehearsal.
- **No original Minecraft gameplay or server-side projectile acceptance has been observed.** The actual method implementation cited above is external source evidence; the CI native probe validates linkage/signature, not live packet capture or in-game latency.

Keep in draft, do not auto-merge or force push, and do not install on the user's machine or alter visual branches.
