# GhostC R4 visual track — optional live Module List HUD

An independent visual feature developed alongside the separate combat-timing/Aim Assist work.

## Available in Forge 1.8.9
- **Module List** in ClickGUI, disabled by default for old-profile compatibility.
- Only *enabled* modules appear; disabled/faulted modules and Module List itself are excluded.
- Optional inclusion of RENDER category modules (defaults off so HUD cards don't flood the list).
- Four screen corners, width/alphabetical sort, a bounded 1–24 row limit.
- Background and accent toggles, theme-aware accent color (Graphite/Indigo).
- Font-measured text fitting and viewport clipping, including tiny GUI scales.
- Uses existing profile persistence, settings UI, render guard and per-feature fault isolation.
- No injection, packet interception, input rewriting or combat changes.

## Verification
The pure Java ModuleListLayoutTests cover module lifecycle visibility, render filtering,
sort, row cap, all corner anchors, truncated/malicious labels and 1,600 seeded viewport
variants. Core CI runs it on Java 17/21 targeting Java 8; Forge CI compiles Java 8 and
checks bundled classes. CI cannot certify native OpenGL appearance, real in-game FPS,
or conflicts with other installed Minecraft mods. Those require native acceptance.

## Coordination
Do not automatically merge this branch while the concurrent R4 combat PR edits HostRuntime.
The only shared core file this change needs is HostRuntime, for independent settings and
module registration; preserve both feature sets when rebasing.

## Additional Target HUD visual variants

- The original Classic and Glass paint paths and profile defaults remain unchanged.
- Two opt-in skins: **Minimal** (graphite surface, narrow HP bar, reduced visual noise)
  and **Nebula** (violet/cyan twin accents, detailed frame and elevated HP bar).
- The original 160x43 full Target HUD and 160x29 compact Target HUD geometry is retained.
- Player/health/distance information comes from the existing live target gates.
- Independent pure Java geometry checks and profile roundtrip tests are included;
  shaders or injector changes are deliberately excluded.

## Nebula R4 redesign
Nebula now has a distinct painter and restrained aurora composition with a four-sided frame,
low-contrast inset, two-tone health fill and a small energy marker rather than full-width neon stripes.
The live target identity is verified before drawing; health interpolation cannot reuse the previous target.
The `hud.target.motion` toggle defaults to true but only affects the opt-in Nebula skin.
Classic, Glass, and Minimal retain their original painter and defaults. The Forge reset clears Nebula
animation state on world/session transition. Pixel appearance still requires Minecraft visual acceptance.

## Matching Module List appearance
The in-game Module List now exposes Classic (unchanged), Minimal, and Nebula appearances,
plus opt-in compact row spacing. Nebula uses the same violet/cyan hierarchy as the
Target HUD and a one-pixel sheen rather than expensive glow. All palette values come
from immutable, shared `HudThemeTokens`; no shader, native code, packet or combat changes.

## R4 HUD Editor snap and alignment guides
- F7 HUD drag now magnetically aligns to viewport edges, centers and adjacent HUD widgets
  within five *preview pixels*, with visible cyan alignment guides.
- Hold either Alt key while dragging for precise free movement.
- Pure geometry and bounded snapping; unaffected HUD anchors and combat remain unchanged.
- Keyboard 1/10-pixel nudge and reset-to-default are unchanged, and a drag persists only
  once on release if its serialized normalized anchor actually changed.
- Pointer-return restores original normalized coordinates exactly, even during snapping.
- Tested with seeded extreme aspect ratios and 26,000 randomized snap cases.

## R4 Keystrokes and CPS visual cohesion
Keystrokes and CPS now each expose `appearance` (Classic/Minimal/Nebula) in the
existing ClickGUI. Both default to Classic and retain the original pixel colors
unless the user explicitly chooses a new style. Minimal uses subdued graphite
panels; Nebula reuses the immutable aurora tokens. The Minecraft-scaled geometry,
text fitting, key-state readouts, physical CPS collection and drawing budgets are
unchanged. Profile roundtrips and original visual constants are regression tested.

## R4 Nebula ClickGUI theme
F7 ClickGUI now has three persisted UI themes: existing Indigo and Graphite/Mint,
plus a full Nebula chrome theme. The theme button cycles Indigo -> Mint -> Nebula.
The Nebula surface includes a restrained inset border, a split aurora line,
selection rails, coordinated module/setting backgrounds and matching sliders.
No hit zones, capture modes, settings logic or module input behaviors changed.
The old two themes retain their exact original base colors and keep Indigo as default.
Theme persistence uses the same validated atomic schema-1 UI settings file;
unknown themes remain invalid. Tests cover persistence, palette parity and cycling.

## F7 Module List positioning
The Module List now has its own fifth F7 HUD editor tab (List). Drag or keyboard
nudge the widget to select an explicitly persisted `custom` positioning option;
old profiles and their four corner presets continue to render unchanged. The
custom anchor uses the existing schema-1 `hud.modulelist.x/y` keys and normalized
bounds, and dynamically sized frames follow the actual enabled-module labels.
Reset restores the original top-right preset; snapping, Alt bypass and one-save-on-
release remain shared with the other HUD widgets. Core regressions cover legacy
profile compatibility, custom bounds, anchor rejection and dynamic layout.

## HUD editor collision visibility
The F7 preview now outlines collisions involving the selected HUD widget with a
restrained warning border and contextual footer. It uses read-only half-open
pixel geometry and never automatically relocates widgets, rewrites settings or
interferes with native Minecraft input. The overlap policy has deterministic
24k-variant regression coverage, including touching-edge noncollisions.

## R4 editor previews — readable silhouettes instead of placeholder tiles
The F7 editor now paints miniature, bounded, representative shapes for Status,
Keystrokes (WASD), CPS, Target HUD (HP bar) and Module List (enabled rows),
colored by the user's actual style choice (Classic, Minimal or Nebula).
These are deliberately **schematic**, not actual combat/health data; they use
the existing authoritative hitboxes and no shaders, textures or new inputs.
`HudPreviewArt` has a callback-style, allocation-free painter with integer
fraction geometry and 30,000 seeded clipping/bounds test cases. One reusable
sink in Forge avoids making per-frame scene graphs or lambdas.

Also, F7 caches the font-measured Module List frame **once per GUI frame**
and invalidates it on input; repeated preview width/height and collision/snap
queries no longer re-sort all enabled modules within a single paint.
This only optimizes the F7 editor. It does not assert improvements to
Minecraft gameplay FPS or change the independent runtime render loop.

## Independent HUD scales (R4)
Per-widget `scale` settings are now available for Keystrokes, CPS, Target HUD
and Module List (0.75x–1.75x, original 1.00x default). They are typed profile
settings and need no UI settings schema migration. All four use the same
bounded visible footprint math as the F7 editor and saved normalized anchors.

For non-unit scales, Forge wraps only the corresponding HUD painter in a
push/translate/scale/pop modelview scope, restoring the GL matrix in `finally`.
The old 1.00x rendering code path remains the default. The Target HUD performs
full scaled-viewport admission before live target selection, and the Module
List computes text layout in a scale-adjusted virtual viewport. Keystrokes and
Target skip completely if their full scaled rectangle cannot fit; scaled CPS
also uses bounded full-card admission. Existing Status `scale` stays unchanged.
A headless policy regression validates legacy 1.00x equality and seeded
24,000-frame scaled bounds plus persisted independent settings.
Real Minecraft FPS and visual acceptance still require a game launch.

### F7 scale keyboard shortcuts
In the existing HUD editor, select a Status/Keys/CPS/Target/List tab and use
`[` and `]` to resize it in 0.05x increments, Shift+`[` or Shift+`]` for
0.25x increments, and `0` to restore its original 1.00x scale. The editor
shows the selected live widget's scale value. These actions use existing
typed/validated `UiActions` mutations and profile persistence, not new
render-only transforms. Drag capture is finalized before changing a widget's
actual footprint; unchanged endpoint presses do not write profiles.

## F7 quick HUD layout presets
The F7 HUD editor has three explicit, opt-in one-click presets in its sidebar,
also accessible with `1`, `2`, `3` or the corresponding numpad keys:
**Default** restores all five exact original normalized HUD anchors and the
original Module List `top_right` corner mode; **PvP** puts Keystrokes bottom-left,
CPS above and the Target HUD centrally below, with Module List custom top-right;
**Stream** separates key/CPS previews and keeps the Target HUD low and central.
The presets are position-only: they never change module enablement, combat,
theme, scale, font, transparency or other HUD preferences.

A preset reports true only if normalized anchor bytes changed; repeated
selection does not rewrite `ui.properties`. Module List mode uses the existing
validated typed setting gateway and persists independently. Sidebar choices
are shown only where enough height exists to avoid colliding with filters.
A headless 20k-case replay checks bounds, idempotence, unchanged visual
preferences, and exact profile/UI restart behavior, including restoring legacy
defaults. Existing drag, snapping, scale and reset controls remain available.

### Preset clearance and low-allocation rendering
The Stream layout places CPS in the lower-right rather than beside the centered
Target HUD. At default scales, independent collision tests verify all five HUD
footprints are non-overlapping for PvP/Stream at 640×360, 1280×720 and
1920×1080. The sidebar caches preset enumeration and uses static normalized
coordinate tables, avoiding temporary per-frame preset anchor objects.

## R5 visual redesign — Rise/Vape/Slinky benchmark, phase 1
The Nebula **Wide** Modules screen now draws an original three-part workspace:
category rail, a persistent full-height module list, and a dedicated setting
pane. A lighter graphite material replaces the previous boxed violet rows.
Modules have aligned compact switches and selection accents; configuration
gets a title, separated keybind control, independently scrolling full-height
settings, and low-noise numeric sliders/switches.

`GuiNebulaWorkspace` is the single source of truth for the new drawn
row/slider/keybind/scroll hitzones, with 50k seeded geometry tests for
viewport containment and exclusive input ownership. Unsupported viewport
sizes and the original Indigo/Mint/Compact layouts still use their previous
native rendering/hitbox paths. No font assets, copying of other clients'
textures, combat changes or external injector required.

## R5 visual redesign phase 2 — text-first enabled Module List
For the **Nebula** in-game Module List appearance, use right-aligned module
labels on the right or left-aligned labels on the left, subtly animated on
first enable. Row backgrounds are now almost transparent when enabled, rather
than opaque stacked cards. The 2px side marker is restrained, optional, and
follows the actual anchor side. Existing Classic/Minimal render paths remain
unchanged. Label animation uses a stable module ID and a short 180ms time-based
cubic ease, so changes in FPS do not alter transition duration. Disabled and
faulted modules disappear immediately; the motion cache prunes them in the
same frame, never falsely advertising a disabled feature. New seeded 20k
lifecycle checks assert live-only membership, transition bounds, and fade
color preservation. Width sorting, Unicode sanitizing and profile positions
continue through the original `ModuleListLayout`.

### R5 interaction and target card refinements
Nebula module rows now have a restrained hover highlight without altering
selection or toggle hit zones. Faulted modules show an explicit RESET label
whose clickable hit zone covers the same painted control. The new click
routing still calls the existing `UiActions.toggle` fault-recovery gateway
(one click resets to DISABLED, a second click enables).

The opt-in Nebula Target HUD now uses one deep translucent surface, small
vertical accent and a restrained name + HP readout with one animated health
bar. Target selection, team eligibility, combat data, health interpolation,
optional distance and its 160x43/160x29 bounds remain on their proven paths.
Classic/Glass/Minimal target skins are unchanged.
