# R3.46: Native Minecraft 1.8.9 tandem acceptance probe

Stacked on R3.45 native action outcomes **PR #178**, parent `e0838a8370d3cdfe149452150639920973f4a0ec`. No change to `main` or the independent visual PR.

## Why this test exists

R3.43–R3.45 implement per-resource action grants, coexistence and locally observed native outcomes. Their core Java randomized tests cover hundreds of thousands of policy decisions but do not instantiate a vanilla Minecraft inventory container. The R3.42 native Netty probe exercises real packet classes, but not the chest/source layout or their coexistence with other module controllers.

R3.46 adds **one mandatory ForgeGradle Java 8 `nativeTandemProbe`** to `check`, so both Linux and Windows Forge builds fail if the native combined boundary test fails. It runs in a sandboxed test JVM under the actual Minecraft 1.8.9 Forge dependency classpath.

## Native acceptance assertions

- Actual vanilla `ContainerChest` construction for **27-slot and 54-slot** chest inventories, backed by separate `InventoryBasic` player and source inventories.
- Exact chest source first, player main-storage indices +9, and 9 hotbar indices 0–8 after that. This directly tests the slot ordering assumed by the Chest Stealer implementation.
- Real vanilla `ContainerChest.transferStackInSlot` on a five-item diamond stack: destination storage gains exactly five, original chest source clears. With every player storage slot full, a later vanilla transfer cannot move the item and leaves its source intact.
- One enabled gameplay state with combat, Bridge Assist, Velocity and sprint active at the same time; `WTapPolicy` native-attack admission then gives WTap temporary exclusive sprint ownership without suppressing the other gameplay domains.
- One actual Netty `EmbeddedChannel` with Minecraft 1.8.9 `S12PacketEntityVelocity` and `S27PacketExplosion`: local player IDs are scaled, unrelated player IDs pass through, every packet reaches the vanilla handler once.
- Transition to player inventory: AutoArmor alone gets inventory ownership, all gameplay grants stop and the packet hook is detached. Pending WTap resets become cancelled rather than falsely completed.
- Transition to chest: only Chest Stealer gets the click lane; a safe open delay, one source attempt, pending-source observation and no blind second click are exercised.
- Unknown GUI, lost foreground focus, and F6/world reset revoke all action grants, pending telemetry and native Netty handlers.

## Honest test boundary

This is **not** a launched Minecraft client, not a multi-player server acceptance, not a mouse/keyboard automation on a rendered GUI, and not proof of authoritative server inventory transaction acknowledgement or successful PvP damage. Direct `ContainerChest.transferStackInSlot` tests vanilla container logic, **not** the real remote-server packet exchange. The player inventory backing object used by the probe is a test-only `InventoryBasic` populated with ordinary vanilla items; it verifies slot mapping and transfers, not a complete EntityPlayer implementation.

Real original Minecraft 1.8.9 acceptance still needs: GUI opening/closing with actual `GuiChest` and `GuiInventory`, AutoArmor upgrade/repair, mouse hotbar and key conflict checks, manual input precedence, network lag/corrections, S12/S27 behavior with server validation, F6 Panic while both combat and inventory modules are configured, and state/telemetry readout placement/FPS. No auto merge until those are validated.

## Source ownership

R3.46 only adds `forge189/src/test/java/dev/ghostfusion/forge189/NativeTandemAcceptanceTests.java`, one `forge189/build.gradle` test registration, and this document. It does not overwrite the independently maintained appearance/HUD PR #164, earlier modules, or the user's `main` branch.
