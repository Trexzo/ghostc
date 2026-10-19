# GhostC R2.1 — accessible HUD selection and dynamic render footprints

## Problem confirmed in source

R2.0 corrected the HUD preview aspect ratio but reduced some elements, particularly CPS, to only a few pixels high in the letterboxed preview. Direct mouse dragging alone was therefore difficult. The editor also assumed the tallest Keystrokes and Target layouts even when their optional rows/health bar were disabled, so the apparent preview rectangle did not match the live rendered footprint.

## Implementation

- **Explicit selection controls:** four equal-width tabs for Status, Keys, CPS and Target above the preview. They share exact drawing and click geometry through `HudEditorControls.tabLeft/tabWidth/tabAt`. Selecting a tab works even if the corresponding scaled HUD rectangle is too small to grab reliably. Direct mouse dragging still selects the clicked widget with reverse paint-order hit testing.
- **Keyboard movement:** while on the HUD page, the arrow keys move the selected widget by one **actual scaled Minecraft pixel**. Holding Shift moves by ten pixels. The `R` key restores only the selected widget to its default anchor; all other widget anchors remain unchanged. These edits persist immediately through the existing UI preference writer. F7/ESC continue to close the screen.
- **Dynamic dimensions:** new `HudFootprints` defines Keystrokes as 37px plus 20px for each optional jump/sneak row, CPS at 103×17 and Target as 160×29/43 depending on the health-bar setting. The *real* Forge renderers and F7 previews consume the same sizes, rather than assuming maximum-height boxes.
- **Shared preview drag:** `HudPreviewGeometry` accepts explicit actual widget dimensions for rendering and drag conversion. Previous signatures remain supported for existing callers/tests.
- **Backward compatibility:** stored `ui.properties` remains schema 1, with the existing Status and widget anchors preserved. New selector state is not persisted; only positions are. No new external assets, packet manipulation, or game-network interception.

## Test and acceptance

New `HudEditorControlsTests` checks exact tab segment boundaries (including odd widths), all four selections, reset isolation, 24,000 seeded real-screen nudge cases with per-widget state isolation, extreme integer inputs and every optional widget footprint combination. Existing `HudPreviewTests`, `HudPlacementTests` and all other regressions remain required. Direct source contracts verify the actual Forge GUI selection, keyboard handling and shared live rendering footprints.

Merge gates remain Java 17/21 headless Core CI and Linux/Windows JDK8 Minecraft Forge 1.8.9 compile/reobfuscation, JAR integrity plus fake-profile Windows checksum install/collect/rollback. Development version is **2.1.0**; repository inventory requires **21** Java regression classes and **14** guides R08–R21.

**Runtime acceptance:** these tests cannot substitute for a real Minecraft GUI. The exact appearance of small CPS previews, tab/arrow interactions, dynamic layout changes, FPS and gameplay remain **unverified in-game** pending one consolidated authorized local Minecraft 1.8.9 run. See `docs/RUNTIME_R10.md` for safe Windows installation/rollback and report observed evidence in GitHub Issues #2, #3, #5, #6.
