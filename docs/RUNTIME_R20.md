# GhostC R2.0 — screen-aspect accurate, movable HUD editor

## Source-audited defect

R1.9 made four HUD elements draggable in the Forge F7 editor. However, it mapped widget positions directly onto the editor's **tall rectangular panel** (typically around 273×326 logical pixels), rather than onto an aspect-correct representation of the *Minecraft screen* (often 16:9 or 16:10). Widget sizes were also drawn in full game-pixel dimensions inside that much smaller panel. As a result, previews had the wrong aspect ratio, covered disproportionately large areas, and drag locations visually disagreed with in-game rendering.

## R2.0 implementation

- New host-independent `HudPreviewGeometry` fits the actual scaled Minecraft display aspect ratio inside the existing F7 HUD panel, preserving black letterbox margins instead of stretching the representation.
- Its `canvas` is bounded within the GUI panel, and `widget()` and `status()` map each original screen-size footprint into this canvas, using the same persisted normalized coordinates as the Forge HUD renderers. Preview sizes now reflect screen-relative sizes rather than their original game-pixel dimensions.
- `moveWidget()` and `moveStatus()` convert pointer movement and the original click/grab offset through **that same screen-aspect canvas**. The actual `GhostClickGui` uses the helper for both drawing, hit testing and dragging. Mouse events outside the displayed game viewport do not begin a drag.
- Status preview dimensions derive from the actual version/label and configured status scale; this replaces the fixed 124×22 preview size and follows changes to status scale/label settings.
- Compact/Wide theme support, F7/ESC behavior, independent Keystrokes/CPS/Target and Status anchors, all schema-1 profile fields and legacy migration are unchanged.
- Previews use short S/K/C/T labels only when there is sufficient pixel space; a legend identifies each element even when the aspect-correct preview is small.

## Verification

`HudPreviewTests` exercises a known 16:9 screen letterboxed in a square panel, 18,000 seeded scaled-screen/editor combinations, placement containment, selected widget drag updates, normalized clamping, status/feature independence, and malformed geometry rejection. Existing `HudPlacementTests` cover profile migration and atomic persistence. Direct source-level Forge wiring contracts require the real GUI to draw and interact using `HudPreviewGeometry`, rather than a disconnected policy class.

Java 17/21 Core CI, Linux/Windows JDK8 Minecraft Forge 1.8.9 build/reobfuscation/JAR inspection and fake-profile SHA-256 Prepare/Collect/Rollback remain required. Milestone version **2.0.0**, with 20 test classes and 13 runtime guides. No existing repository files may be deleted.

**Still unverified:** a licensed Minecraft 1.8.9 graphical process has not been launched by GitHub CI. The editor's real pixels, pointer feel, scaled-resolution changes, and gameplay performance require one consolidated local-world acceptance. The development JAR remains *unverified-ingame*. See `docs/RUNTIME_R10.md` for controlled Windows installation, evidence gathering and rollback; record real observations in Issues #2, #3, #5, #6.
