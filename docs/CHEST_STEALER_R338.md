# GhostC R3.38 — real Chest Stealer

**GitHub Draft stack**: built on WTap R3.37 head `ba2c5f5093a894fa10746477d7d49cf7ae8a18e8`. Does not modify the other chat's visual branch or `main`. The module appears under the existing UTILITY category in F7.

## What it actually does

The new, **default-disabled** `inventory.cheststealer` is wired into the *real Minecraft Forge 1.8.9* client tick. It acts **only** if the active GUI is `GuiChest`, the player's `openContainer` is the **same native `ContainerChest`**, the chest size is a standard 27 or 54 slots, and the window still belongs to the connected world/player.

The controller reads the live `ContainerChest.getLowerChestInventory()` source slots and sends **at most one** normal Minecraft `PlayerControllerMP.windowClick(windowId, sourceSlot, 0, 1, localPlayer)` (vanilla shift-click) per eligible transfer. It does not directly edit inventories, synthesize item stacks, send handmade C0E packets or touch player armor/hotbar slots directly.

### User settings

| Setting | Default | Range | Purpose |
|---|---|---|---|
| `open_delay_ticks` | 6 | 2–20 | Allow container contents to load after the GUI opens |
| `click_delay_ticks` | 4 | 2–20 | Minimum ticks between native shift-click attempts |
| `allow_custom_names` | false | false/true | By default refuse custom-named chest GUIs; opt-in is at user's own risk |

Additional always-on safeguards: controller only runs when the Minecraft window is active, the mouse buttons and Shift aren't physically held, the cursor carries no stack, the exact native chest container remains open and the player is alive. A manual mouse/Shift interaction resets the automated pending window, requiring a fresh open delay before resuming.

The controller checks whether the *entire* candidate source stack fits in the player's 36-slot main inventory, considering empty slots and compatible partial stacks, and refuses transfers that would overflow it. It samples slot contents immediately before requesting the native shift-click.

A pure Java `ChestStealerPolicy` provides deterministic owner-thread admission, session epoch fences, window-ID reset, a round-robin source-slot cursor, single in-flight source transaction, 2–20 tick pacing and an opening delay. The next transfer is considered **eligible only after a later tick observes a local change in the previously requested chest source slot**. If the source remains unchanged for 12 ticks, it blocks that chest window for the rest of its open session rather than blindly spamming transfer attempts. It deliberately does **not** claim a locally observed slot change proves server acknowledgement; vanilla handles inventory transaction confirmations.

No automatic chest closing is performed. An empty/unsupported chest is left open.

## Limitations and real acceptance

The safe default refuses custom-labeled menus, including potentially legitimate server chests. Even when `allow_custom_names` is turned on, the module cannot distinguish decorative server menu items from loot; use only where chest shift-clicking is expected and permitted.

Opening delay mitigates but **does not prove** that all chest contents have been received, since vanilla 1.8.9 menu contents can arrive after the screen opens. A server may reject a transaction or correct the client contents later. These are reasons for the strict one-at-a-time and no-blind-retry rules, not claims that server inventory synchronization is solved.

This module is **not** Inventory Manager, AutoArmor, intelligent best-gear selection, automatic sorting, flawless multiplayer looting, or a bypass of server rules. The original-game acceptance matrix must include 27/54-slot standard chests, full and half-full player inventories, NBT/enchantments, custom menu refusal, manual input, server transaction rejections and latency, closing/reopening the same window ID, world/Panic/disconnect, server slot corrections and simultaneous combat input.

## Automated verification

`ChestStealerTests` runs 32,000 deterministic pseudo-random chest ticks, pending-slot confirmation and failed-transfer blocking, source-order traversal, window reuse, epoch resets, invalid slot/clock arguments, GUI defaults and profile persistence. The existing `IntegratedModuleCatalogTests` runs these tests and verifies all modules—including Chest Stealer—are disabled by F6 Panic while their non-destructive settings survive restart.

Forge CI additionally compiles the actual `ForgeChestStealerController` against Minecraft Forge 1.8.9 and runs the Windows downloaded-JAR installation rehearsal. These are **not a real Minecraft game launch**. Until an authorized original-game test has verified native GUI data and server transaction behavior, keep this PR Draft and disable this module by default.
