# GhostC R3.44 — Module Interaction Diagnostics

**Stacked on R3.43 module state machine (Draft #176), base head `81ab441e0feebcc7f1b4d09ee5e44c340a49237f`.** The independently owned visual/HUD branch and `main` are unchanged.

## Purpose and actual in-game behavior

R3.43 grants disjoint, session-scoped native action lanes: combat, Bridge Assist Sneak, Velocity, WTap/AutoSprint sprint state, and one inventory-click owner. R3.44 makes **those exact decisions inspectable**, without claiming successful native action execution or server acknowledgements.

Enable the existing **Runtime Diagnostics** module under F7 to see a compact two-line on-screen status:

- `Grants: GAMEPLAY | sprint WTAP | inventory NONE`, or `PLAYER_ARMOR`, `PLAYER_SORT`, `CHEST_TRANSFER` when appropriate.
- `Held: AutoSprint (WTap owns sprint)` or `Held: Inventory Manager (AutoArmor priority)`, else a reason such as window inactive, GUI open, or no live world.

This is a fixed-vocabulary overlay, not an extra general-purpose HUD theme. It uses a separate guarded draw call at the bottom-left and remains completely absent unless the existing Runtime Diagnostics module is explicitly enabled. It does **not** add names, coordinates, server IP, session identifiers or packet contents to on-screen debug text.

## Specific reason codes

The `ModuleInteractionStateMachine.Frame.denial(Resource)` method is the single authoritative source for deny/allow decisions, eliminating duplicate UI heuristics:

- `NONE`: the state machine granted permission, **not proof that a controller clicked or sent a packet**
- `DISABLED`: module not enabled
- `WORLD_UNAVAILABLE`: no valid world or session context
- `WINDOW_INACTIVE`: OS/Minecraft window not active
- `WRONG_SCREEN`: a module was enabled but a GUI other than its designated native screen owns focus
- `WTAP_RESERVED`: WTap temporarily owns sprint while AutoSprint waits
- `ARMOR_PRIORITY`: AutoArmor owns an open player inventory, while Inventory Manager is enabled but held back

Velocity and combat may still operate together in the gameplay phase; WTap suppresses only AutoSprint, and the actual underlying physical input/packet/entity checks remain independent.

## Anonymous, bounded counters

When Runtime Diagnostics is enabled, `ForgeEventAdapter` records **one counter increment per matching client tick**, not per network packet, target, player or underlying native action:

- `count.module_wtap_preempted_sprint`
- `count.module_armor_preempted_sorter`
- `count.module_armor_grant`, `count.module_sorter_grant`, `count.module_chest_grant`
- `count.module_gameplay_gui_denied`, `count.module_gameplay_window_denied`

The existing on-demand/disconnect diagnostics exporter saves these aggregates in the existing `diagnostics-last.properties` file as anonymous numbers. They saturate at `Long.MAX_VALUE`; no dynamic strings or event logs are persisted.

**Backup compatibility:** The schema remains `schema=1` and `scope=counter-only-no-identity`. Backups from before R3.44 are still recognized if *only* the new counters are missing; the old required counters, data type rules, 64KB limit, symlink protection, and rejection of unrecognized keys remain strict. Newly generated reports include all counters.

## Verification

`ModuleInteractionDiagnosticsTests` runs 32,000 deterministic mixed-GUI/world/foreground/module combinations, checks eight resource reason codes per frame, validates grant/reason agreement and short anonymous strings, and verifies that pre-R3.44 anonymous schema-1 reports rotate into backups without losing integrity. Included with the existing `ModuleInteractionTests` (715,866 assertions) and module catalog suite on Core Java 17/21.

Forge 1.8.9 Linux/Windows JDK8 CI verifies the actual native overlay wiring and compiles the R3.42 Minecraft/Netty packet probe. **CI does not launch Minecraft.** A real authorized 1.8.9 game run remains required to establish HUD placement, actual action timing, FPS, human-input precedence, meaningful reason frequencies and server-side outcomes.

The next stage is **controller-confirmed action telemetry** (attempt/success/timeout) for each individual inventory controller and packet hook, explicitly distinguished from grants and denials.
