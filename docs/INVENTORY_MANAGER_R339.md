# GhostC R3.39 — Inventory Manager / Safe Hotbar Sort

**Stacked Draft feature** based on R3.38 Chest Stealer PR #171 commit `85d5b414c36a62a4eed9a5cf0223ede3bd66191c`; original GhostC main and independently maintained visual #164 remain untouched.

## Real Minecraft 1.8.9 behavior

The `inventory.manager` module appears in F7 **UTILITY**, and is **disabled by default**. Its Forge controller operates only while a genuine survival `GuiInventory` is visible and the same `ContainerPlayer` is held by `EntityPlayerSP.inventoryContainer` and `openContainer` (window ID 0). It refuses creative mode, disconnected/world-null state, inactive application, dead player, a cursor-held stack and physical mouse/Shift interaction.

The first phase of Inventory Manager sorts **the hotbar only**, one vanilla click at a time:

| Hotbar slot | Desired contents |
|---|---|
| 1 (index 0) | Best available sword |
| 2 (index 1) | Best available pickaxe |
| 3 (index 2) | Best available axe |
| 4 (index 3) | Best available shovel |

**No inventory is dropped, discarded, deleted or rearranged other than the selected two slots of a vanilla swap.** There is no AutoArmor, trash cleaner, best-armor solver, consumables organization or server menu interaction in this milestone. Full inventory management is a broader future goal; this is specifically an actual **equipment hotbar sorter**.

The controller scans ContainerPlayer slots **9–44** (27 main inventory + 9 hotbar). It ranks swords by vanilla attack damage and Sharpness, and tools by material harvest level, nominal mining efficiency, Efficiency enchantment and small durability tie-breaker. Categories are distinct; reserved destination hotbar slots 1–4 are not raided as sources for unrelated categories, and occupied slots containing **any other item** (blocks, food, potions or unknown modded items) are always protected from being replaced.

It invokes `PlayerControllerMP.windowClick(windowId, sourceContainerSlot, targetHotbarIndex, 2, localPlayer)` — Minecraft's **standard hotbar swap** mode. No direct inventory mutation or handcrafted packet sending.

### F7 user controls

- `open_delay_ticks`: **6**, range 2–20, to avoid operating at the instant the inventory screen opens.
- `click_delay_ticks`: **5**, range 2–20, minimum time between native swaps.
- `upgrade_existing`: **false** by default. False means only **empty** designated hotbar slots may be filled. If true, a designated hotbar slot already containing the **correct category** may be replaced by a strictly better matching item; an unrelated item in that slot remains protected even with the option enabled.

One swap must show a **local change in BOTH source and destination slots** before another swap may be attempted. If both changes are not observed within 12 client ticks, the entire currently open inventory session is blocked. Observing local changes is **not proof of a server ACK**: Minecraft itself manages transaction packets and possible server corrections. Unexpected GUI, user input, F6 Panic, world change, disconnect or module fault clears the controller's pending state. The module does not close the inventory automatically.

## Independent verification and limitations

- `InventoryManagerPolicy`: owner-thread deterministic slot/category/quality admission, bounded GUI warmup, pacing, source+destination observation, 12-tick fail-closed timeout, world generation and Panic resets.
- `ForgeInventoryManagerController`: actual Forge 1.8.9 player inventory, item ranking and vanilla mode-2 inventory click bridge.
- `InventoryManagerTests`: **32,000 deterministic randomized inventory ticks** plus explicit native 9–44 container mapping, sword/tool destination ordering, unknown-item protection, no oscillating upgrades, reserved hotbar protection, failed-transaction denial, world resets, owner thread and persisted GUI settings.
- `IntegratedModuleCatalogTests`: verifies Inventory Manager can enable/disable with all combat, movement, inventory and HUD modules; F6 Panic disables it alongside everything else and persists settings without automatically reactivating sorting.

Automated Core Java17/21 and Forge 1.8.9 Linux/Windows JDK8 compile/reobfuscate plus safe artifact rehearsal are needed. **Native real-game acceptance has NOT yet occurred:** validate actual 1.8.9 single-player and authorized multiplayer inventory click mode2, player inventory layout, server corrections/latency, enchanted and NBT-modified gear, manual hotbar click overlap, common server GUIs, F6 Panic, unusual modded tools, and interactions with Chest Stealer and WTap.

This feature is a conservative initial functional inventory sorter, not complete Vape/Slinky Inventory Manager parity.
