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
