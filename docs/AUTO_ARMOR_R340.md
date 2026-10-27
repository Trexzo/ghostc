# GhostC R3.40 — native AutoArmor / armor upgrades

**Draft stacked milestone**, based on certified Inventory Manager R3.39 PR #172 head `2ae022156dca5e1cdaa94d10166d8c734884b83d`. It does not alter `main` or the other chat's independent HUD/GUI branch.

## Real user-visible feature

New `inventory.autoarmor` under F7 → **UTILITY**, **disabled by default**. It works **only** in the player's actual survival `GuiInventory`, on the original `ContainerPlayer` (window 0). It never places/drops armor directly, never injects raw packets, never opens inventory automatically, and never acts in `GuiChest` or a server menu.

Slots match original Minecraft 1.8.9 `ContainerPlayer`:
- Armor destination slots **5 helmet, 6 chestplate, 7 leggings, 8 boots**.
- Storage item source slots **9–44** (27 main inventory and 9 hotbar slots).

### Default behavior

Only **empty** armor slots are eligible to receive armor. For each armor category, the highest-scored `ItemArmor` from slots 9–44 is transferred using real `PlayerControllerMP.windowClick(0, sourceSlot, 0, 1, player)` (vanilla quick-move). This still relies on Minecraft's own server inventory synchronization.

`upgrade_existing` is **false by default**. If explicitly enabled, a strictly better piece may replace an existing **matching** armor piece, but only when storage contains at least one fully empty slot:

1. First transfer the existing piece from its armor slot (5–8) into inventory using a vanilla quick-move. Never use a drop action, shift-throw or C0E packet constructed by GhostC.
2. The owner-thread policy must observe the armor destination empty, the original armor present in storage (increase in matching item count), and the originally selected candidate unchanged in its exact source slot.
3. After its configured pacing interval, equip that **same, exact candidate** via a second normal vanilla quick-move. If it moved, became unrecognized, or another item occupied the armor destination, refuse to click.
4. Final equip must be observed: equipped armor equals the requested candidate and the candidate source has changed. Otherwise do not schedule any more inventory changes.

A transfer whose necessary local inventory changes are absent for **12 ticks** blocks the entire currently open inventory session; it cannot be retried or rearmed without closing/resuming the eligible GUI, changing world, disabling the module, or resetting due to a manual interaction. A local inventory change **is not a server ACK**: server corrections and timing require actual gameplay testing.

### GUI settings

| Setting | Default | Range |
|---|---|---|
| `open_delay_ticks` | 6 | 2–20 |
| `click_delay_ticks` | 5 | 2–20 |
| `upgrade_existing` | false | false / true |

Ranking is generic defensive strength: vanilla `ItemArmor.damageReduceAmount` dominates, followed by Protection and narrower armor enchantments, with only a small durability tie-break. This is **not** a magical per-opponent best-set solver. Existing unusual helmet items such as pumpkins are **OTHER**, never empty, and are not displaced. Other inventory items are never selected as armor candidates.

### Interactions and safety

The Forge adapter gives AutoArmor exclusive priority over Inventory Manager when both modules are enabled: on each tick, the hotbar sorter resets and **does not execute**. Chest Stealer operates only in the separate `GuiChest`; no overlapping inventory click owners.

Armor processing requires a live player, `Display.isActive()`, the exact player `ContainerPlayer`, no dragged cursor item, non-Creative mode, and no physical mouse buttons, Shift/Ctrl or 1–9 hotbar key held. Manual interference resets the owner policy. F6 Panic, GUI closure, disable, world transitions and disconnect reset pending owner work; they cannot roll back an item movement already sent to Minecraft. All feature callbacks remain on the client owner thread.

## Verification vs gameplay evidence

- Pure Java `AutoArmorPolicy` and owner-thread `AutoArmorTests`: **40,000 deterministic randomized frames**, four gear categories, stronger gear ranking, no-space veto, occupied pumpkin protection, two-stage observed removal/equip, no blind retries, timeouts, world/Panic generation fences, malformed inputs and profile persistence.
- Real Forge 1.8.9 `ForgeAutoArmorController` maps slots and executes vanilla quick-moves through the normal native `PlayerControllerMP`; CI must compile/reobfuscate it on Linux and Windows JDK8.
- `IntegratedModuleCatalogTests` verifies module registration, live UI settings and F6 global disable state/persistence with prior combat, movement, HUD, Chest Stealer and Inventory Manager modules.

**CI does not launch Minecraft.** A true authorized native 1.8.9 acceptance must exercise empty-armor equipping; all four slots; occupied upgrades with and without storage capacity; unusual helmet items, NBT/enchantments/durability; failed/rejected inventory transactions, server corrections and latency; manual number-key/mouse/Shift actions; simultaneous Inventory Manager enabled; GUI closure while old armor is temporarily stored; and F6 Panic/disconnect. These are mandatory before treating this as a safe gameplay release.
