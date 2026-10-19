# GhostC R1.8 — reserved F7 shortcut and profile validation

## Source audit finding

The Forge 1.8.9 adapter consumes **F7** (LWJGL Keyboard keycode 65) to open GhostC's ClickGUI. The GUI previously refused to capture F7 as a module bind, but **ModuleRegistry.bind() and ProfileStore.load() still allowed it**. A manually edited or imported profile could assign F7 to a module, which would never toggle because the Forge entrypoint uses that input for its own GUI. This also left inconsistent safety between the physical input gate and the central module binding API.

## Implemented

- New `ReservedKeys.CLICK_GUI = 65` constant in the host-independent Java core. `ModuleRegistry.validateKey` now refuses F7 for both module registration and rebinding. F8 (66), including the pre-existing Status HUD default binding, remains valid and unique.
- `ProfileStore.load` already validates all staged keys before mutating settings/module states; it now uses the strengthened central validator to reject any persisted F7 assignment **before applying anything**. An invalid profile leaves current modules, settings and keybinds unchanged.
- `PhysicalInputGate` uses the same central constant to refuse F7 as a synthetic attack binding.
- While capturing a module keybind in the real Forge `GhostClickGui`, F7 now **closes the GUI without rebinding**, consistent with the displayed F7 close command. ESC still cancels key capture and Backspace/Delete still clears the binding.
- Pure Java `ReservedKeyTests` checks GUI action persistence, central registration/rebinding, owner input routing, profile preflight/atomicity for both status and optional module keys, 10,000 seeded key assignment attempts and physical input behavior. Forge source contract checks verify the real adapters use the shared policy.

## Verification and limitations

The same Java 17/21 headless matrix, Linux/Windows JDK8 Forge 1.8.9 compile/reobfuscation, classfile/JAR checks and checksum-gated fake-profile installation/rollback remain required. The versioned development artifact is **GhostC 1.8.0**. The repository inventory gate covers 18 test classes and R08–R18 guides.

This is **not** a real Minecraft GUI or live game input test. F7/F8 interception, actual window interaction and all gameplay/render functionality still require a consolidated authorized local Minecraft 1.8.9 session. The milestone does not introduce any bypass, packet interception or proprietary donor source.
