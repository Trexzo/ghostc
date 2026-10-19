# GhostC R1.1 — ClickGUI viewport reliability and runtime callback evidence

R1.1 is a **reliability and observability** milestone. It does not claim a successful real Minecraft launch. R1.0's safe Windows install/collect/rollback transaction is retained in `docs/RUNTIME_R10.md`.

## ClickGUI fixes and behavior

The former Forge GUI recalculated the outer window geometry on each render but did **not** update the `GuiTextField` object when scaled resolution or layout changed. Search text could appear in one place while its mouse hitbox remained at its old coordinates. It also enforced a minimum preview width of 120 logical pixels even when the editor's panel had less space.

- `src/main/java/dev/ghostfusion/ui/GuiLayout.java` now calculates screen-bounded, immutable layout geometry for **both WIDE and COMPACT** styles, including search field, module list and HUD preview rectangles. This pure Java model has no Minecraft dependency.
- `GhostClickGui.rebuild()` now uses this model and recreates the search widget only when its position/width changes. It preserves the entered query and keyboard focus during resizing or theme/layout changes.
- List rows use only whole rows that fit the allocated area, and the HUD preview never deliberately extends past its parent panel. On extremely small viewports some editor functions are necessarily unavailable, rather than forcing offscreen hitboxes.
- **F7** now closes the ClickGUI as well as opening it. **ESC** remains supported; F7 cannot be accidentally assigned as a module keybind.
- There are no new source/assets from Vape, Slinky, Onyx or other clients.

## Native Forge callback evidence (opt-in export)

R1.1 extends `RuntimeDiagnostics.Counter` with:
`GUI_OPEN`, `GUI_FRAME`, `GUI_CLOSE`, `OVERLAY_FRAME`, `WORLD_RENDER_FRAME`, `KEY_CALLBACK`, and `MOUSE_CALLBACK`.

Counters are incremented only when the matching Forge/GhostClickGui callback runs on the client thread. The `hud.diagnostics` module, still **disabled by default**, shows selected totals in-game alongside tick/session and existing action counters. Enabling it also permits writing an aggregate-only `diagnostics-last.properties` on disconnect/close. No user names, server addresses, coordinates, chat, packet contents or raw logs are copied by the diagnostic export.

**Important:** these counters distinguish events received from events never received but are not pass/fail assertions by themselves. An OVERLAY_FRAME count says only that the Forge overlay event was delivered, not that pixels rendered correctly. GUI_FRAME says the GUI draw callback executed, not that every interaction succeeded. A real live-world test is still required.

## Test evidence

`ViewportRuntimeTests` exercises seeded and grid-sampled screen dimensions, wide/compact geometry containment, minimum/maximum viewport edge cases, search hitbox movement, normalized HUD positions, rejection of invalid viewports, and diagnostic export/persistence. The Core workflow runs it on Java 17 and 21 with Java 8 bytecode, in addition to all prior suites.

Forge 1.8.9 CI compiles/reobfuscates the 1.1.0 JAR with JDK8 on Linux and Windows, validates its internal classes and metadata and reruns the **safe** Windows Prepare/Collect/Rollback rehearsal against a fake profile. The Windows test now derives the version from `forge189/build.gradle` instead of hardcoding the old 1.0.0 JAR name.

## Single real-game acceptance milestone (still pending)

Using a licensed Minecraft 1.8.9 Forge 11.15.1.2318 installation in an authorized local world:

1. Install the passing **1.1.0** GitHub Actions JAR using the SHA-256-gated R1.0 `Prepare` transaction. Shut Minecraft down first. If necessary, use `Rollback` to restore only prior GhostC-owned JARs.
2. Verify a clean game launch, GhostC 1.1.0 mod listing and F7 GUI opening. Test F7/ESC close.
3. Resize the game window or switch Minecraft GUI scale, edit/search modules with the mouse and keyboard, and switch WIDE/COMPACT while search retains its text/focus.
4. Verify no GUI panels escape the available viewport at representative scales (including small window and large GUI scale). Check HUD drag/restart persistence.
5. Enable `hud.diagnostics`, visit an authorized world, open the GUI, use a few keys/mouse clicks and observe `GUI_OPEN`, `GUI_FRAME`, `OVERLAY_FRAME`, `WORLD_RENDER_FRAME`, key/mouse counters growing when expected.
6. Exercise render/ESP/focus/reconnect and controlled combat modules at one meaningful milestone; collect redacted `latest.log`, in-game screenshots and counter-only report plus FPS/soak evidence.
7. Attach observed results to GitHub Issues #2, #3, #4, #5, #6. **Do not close these just because CI passed.**

This artifact remains labelled **unverified in-game**. It is neither a launcher, an injector nor a source of undocumented game assets.
