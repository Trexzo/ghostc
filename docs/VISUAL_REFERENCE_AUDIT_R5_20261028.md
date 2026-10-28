# GhostC R5 — visual reference audit and Slate quality bar

Review date: 2026-10-28. Target: Minecraft 1.8.9 Forge. Scope: visual/UI only; this is **not** a claim of competitor parity or of tested in-game appearance.

## First-party visual references (not code or asset sources)

- **Vape V4:** official quick-start and overlay documentation: https://docs.vape.gg/intro/quick-start and https://docs.vape.gg/features/overlays/ . Reference: simple category navigation, explicit module/settings affordances, independently positionable overlays, clean enabled/disabled states and no distracting surfaces. Its changelog also records a Central GUI mode and Text GUI improvements: https://www.vape.gg/changelog/v4 .
- **Slinky:** https://slinky.gg/ . Reference: near-black surfaces, understated compact toggles, tabs/profiles and high signal-to-noise settings. Remains a relevant Minecraft 1.8.9 reference. Its colors, graphics and assets must NOT be duplicated.
- **Rise NextGen:** https://beta.riseclient.com/ and https://riseclient.com/ . Reference: obvious search and category hierarchy, modular settings, clear descriptions and human-readable numeric units; its current public NextGen page advertises Minecraft 1.8.9–26.2 and visual controls. The original Rise 6 and new NextGen screenshots are not identical; avoid treating older images as current gameplay evidence.
- **Secondary check:** Drip (https://drip.gg/) for restraint and market positioning, not a code or rendering model.

## Decisions: original GhostC visual identity

- **Slate** = matte graphite base `#121519`, surface `#1B2026`, off-white text `#F1F4F6`, muted copy `#98A5AF`, ice-blue emphasis `#A7D3E0`; status danger remains a quiet rose. These are design directions consistent with current first-party tokens, not borrowed client assets.
- **Explicitly reject** Nebula, violet/purple-as-default, bloom/aurora, neon full-row fills, giant cards, fake blur, font shadow stacks or shaders added solely for decoration. Historical persisted `nebula` tokens may remain compatibility-readable, never user-selectable.
- **ClickGUI** = three-pane category/module/details workspace where supported; no cross-pane input overlap. Search stays above the module list; scrollbars track real row counts; settings retain full display values and clipped labels.
- **Enabled Module List** = thin, text-first, width-sort, right aligned (or left when anchored left), first-activation 180 ms motion, no background or stripes by default. Never show disabled/faulted modules.
- **HUD** = restrained target health presentation, compact CPS/Keystrokes, individually configurable scale and drag positions. The F7 editor's silhouettes must be labeled as previews, not live data. Preserve existing Classic and saved user customizations.

## What was already built in visual PR #164 as of this review

Slate Wide 740×480 themed workspace; `GuiSlateWorkspace` hit geometry; native Minecraft search input with Ctrl+F; independent module/settings scrolling; animated switches; restrained proportional scroll indicators; readable setting presentation; opt-in text-first Module List; modular Target/CPS/Keystrokes styles; F7 preview/presets/snap/collision handling.

## Small refinement in the companion stacked PR

- Show an explicit **ACTIVE / OFF / FAULT** status alongside the selected module's name, with a pixel-measured name budget so long labels cannot paint over status.
- Add low-alpha pointer-hover states for Slate settings rows using `GuiSlateWorkspace.settingIndex`, exactly matching existing native click-hit geometry. Keyboard focus remains distinct.
- Reuse existing keyboard-setting hints in the Slate footer. Suppress hints when a warning owns the shared footer; show scroll progress when settings overflow.

## Next material visual work, in priority order

1. **Actual Minecraft 1.8.9 screenshots** of ClickGUI, F7 editor, in-game Module List, Target HUD and Keystrokes at common GUI scales. Don't represent web mockups as project screenshots.
2. **Typography**: assess readability with the actual Minecraft font at 854×480 and 1920×1080, plus GUI scales 1–3. Only adopt a custom font renderer if it works natively with no GL state leakage, clipping errors, or unacceptable frame cost.
3. **Interactions**: confirm slider drag, wheel, scrollbar thumb, search clear, Ctrl+F, keyboard capture/cancel, fault reset and category changes on real game input. Pure geometry tests are not sufficient.
4. **Overlays**: evaluate anchor/scale combinations at 0.75×, 1.00× and 1.75×, corners and HUD collisions at 640×360/854×480/1920×1080. Check Unicode/suffix measurements, potion/scoreboard overlap and module enable/disable transitions.
5. **Performance**: compare idle/GUI/HUD frame-time and GL state against the previous build in Minecraft; keep no per-frame font loading, texture reallocation, network effects or unnecessary shaders.

### Merge/release boundaries

Do not alter combat timing, packets, rotations, target selection, inventory arbitration or module execution. This visual refinement is intentionally **stacked on** PR #164, not merged into `main` or the combat integration branch. Compilation/CI can verify type safety and geometry; a live Minecraft session is still needed to prove aesthetics, responsiveness and FPS.


## Follow-up implementation — ArrayList and navigation polish

The same isolated stacked PR now includes:

- Slate ArrayList **shadow-free text** for a cleaner single-pixel Minecraft font; Classic and Minimal keep their original shadowed text renderer.
- A single font measurement per fitted ArrayList candidate shared with paint, instead of measuring the same display name again in the Forge loop.
- A bounded, allocation-free `ModuleListTextGeometry` easing/anchor calculation; it respects right/left and custom positions and never places glyphs outside the scaled viewport.
- Skips near-zero-alpha text frames because vanilla Minecraft may coerce a nearly transparent font color to fully opaque. Preserves the 180 ms entry ease; disabled/faulted modules are still removed from the visible registry immediately.
- Resets ArrayList entry motion when the world/HUD resets, the Module List is disabled/faulted, or Slate is changed to a legacy style. Re-enabling Slate cannot inherit a stale previous-session animation.
- Slate ClickGUI navigation shows a small, right-aligned enabled-module count per category (and total under All); the count derives from actual ModuleRegistry.State.ENABLED, excludes faulted/disabled modules, and never edits settings or module state. Zero counts are invisible.
- Two new pure-Java regressions: 40k randomized text-placement viewports/anchors/transitions, plus 10k randomized enabled-count toggles. Tests are included in Core Java17/21 CI and Forge JDK8 still compiles the real painter.

**Evidence limit:** passing compile/geometry tests is not a substitute for screenshots and frame-time from original Minecraft 1.8.9. No gameplay input arbitration, module combat logic, packet hooks or shader resources were modified in this pass.


## R5 continued — quiet Keystrokes and CPS (2026-10-28)

The Slate-native 1.8.9 overlay now expresses physical key presses via a restrained dark graphite panel rather than flooding the whole 25×17 / 83×17 key with bright ice-blue. Each pressed key retains a 1px ice-blue bottom rule and off-white label, whereas the unpressed keys show a quieter muted label. Top hairline and small bottom accent are drawn using original Minecraft `Gui.drawRect` calls, without effects, bloom or textures.

Slate Keystrokes and CPS render native Minecraft `drawString` (no doubled shadow), consistent with the updated Slate ArrayList and Target HUD. **Classic and Minimal remain on their previous painter branches**; the physical key source is still vanilla `GameSettings.getKeyDisplayString`, not hardcoded WASD or artificial input. Existing per-HUD scaling, viewport admission, profile preference and GUI ownership are unchanged.

`SlateHudKeyStyle` is an immutable zero-allocation color policy with a 50k-repeat regression. The regression requires readable RGB contrast for both idle and pressed key labels and confirms the pressed surface never equals a bright full-card accent. The Forge source wiring test asserts that the game-side renderer actually uses the palette and shadow-free Slate paths.

This is native-source implementation with offline tests only. Original in-game screenshot, actual font legibility at Minecraft GUI scales 1–3, color blending, and rendering overhead still require acceptance inside Minecraft 1.8.9 before release.


## F7 workspace polish — named overlap feedback and tab hit zones

- The F7 preview now measures Status, Keys, CPS, Target HUD and ArrayList **once per GUI frame** and reuses exactly those footprints for both paint and overlap classification. Each selected item gets a precise warning (for example, `List overlaps CPS +1`), not an unexplained generic warning. All colliding outlines remain visible; no widgets get moved automatically.
- `HudEditorConflicts.mask` is a pure Java, allocation-free five-bit collision classifier using the existing half-open `HudOverlapPolicy`. It excludes the selected widget, supports multiple peers, and never writes saved coordinates.
- Slate tab hover uses the same `HudEditorControls.tabAt` geometry as mouse selection. A one-pixel ice-blue underline indicates the selected tab, while the legacy themes keep their existing appearance.
- A new 40,000-case seeded regression checks both overlap bitmasks against an independent oracle and verifies every warning names its actual selected item. Forge wiring checks enforce the native implementation and the shared hit region.
- Screenshots, GUI-scale pixel placement and FPS in original Minecraft 1.8.9 remain outstanding release gates. Tests prove source wiring and pure layout behavior, not the final on-screen appearance.


## R5 palette parity — diagnostics overlay

The optional diagnostics HUD was one remaining lavender/purple renderer under a fresh Slate UI. It now resolves a readonly `DiagnosticsHudStyle` from the active `UiState.Theme` and uses the Slate graphite background, ice-blue title and off-white text. Only the three paint colors change: compact/full row ordering, performance sampling, feature admission, overlay size and visibility are unchanged.

The Indigo and Graphite profile paths retain all three original diagnostics colors, including their legacy lavender title. `DiagnosticsHudStyleTests` verifies that legacy pixel values are untouched, Slate values are the same shared palette as other HUDs, and 50,000 repeated theme lookups return the same immutable instances. The native Forge wiring test confirms `drawDiagnostics` reads the active UI theme rather than a hardcoded purple token.


## R5 control-affordance pass — Slate settings pane

Slate's existing numeric setting bar now paints a **4×7 pixel slider thumb** over its neutral track; the thumb is always contained within the exact native drag lane, moves monotonically with the actual saved fraction and becomes brighter on hover/keyboard focus. This is a visual cue only — no changes to slider value calculation, cursor capture, server/game state, or user settings.

Choice settings have a tiny 5-pixel downward chevron in the right gutter to communicate that a row cycles when clicked. The value is fitted into a reduced, geometry-verified text budget, preserving the full left label gap and preventing arrow/value collisions even on narrow Slate workspaces. This is original pixel art using Minecraft `Gui.drawRect`, without textures, assets, icons, or third-party UI code.

`GuiSlateControlGeometry` is pure Java and allocation-free. The Core Java 17/21 pipeline now runs `GuiSlateControlGeometryTests`, covering 40,000 seeded screen layouts, slider extremes and 21 monotonic samples, label/choice gutters, viewport containment, and nonfinite/out-of-range fractions. The Forge wiring test confirms the real Minecraft painter uses these geometry methods. Existing Classic, Mint, Indigo and compact controls remain on their original render/input paths.

**Native acceptance remains outstanding:** open original Minecraft 1.8.9 at GUI scales 1/2/3, verify the thumb, choice indicator, setting label truncation, pointer dragging, keyboard focus, and F7 search/capture do not collide. Compare visual hierarchy against the referenced Vape/Slinky/Rise interfaces without copying their artwork.


## R5 long-label discoverability — pointer-only Slate tooltips

Long module names in the Slate list and human-readable setting names were previously trimmed with an ellipsis and had no hover reveal. The Forge painter now checks the **same visible module/setting row hit regions** used by mouse input; if and only if the original label exceeds its inline Minecraft font budget, a compact graphite tooltip displays a longer, safely fitted version beside the pointer. The affordance is read-only: it never changes a module, setting, search focus, key capture, scroll state or HUD anchor.

`GuiSlateTooltipLayout` independently clamps the tooltip to the actual content panel, caps the text width to 260 pixels, reverses vertical placement near the bottom and reserves the final 20 pixels for warnings and keyboard hints. The box is drawn with simple graphite and hairline ice-blue pixels; no purple, fonts, assets, textures or shader glow. This intentionally shows a longer fit, not an unsupported guarantee that arbitrary hundreds-of-character names will fit.

The Core CI now runs `GuiSlateTooltipLayoutTests` with 50,000 seeded panel/pointer/text combinations; the Forge source-contract checks assert the native row hit tests, tooltip layout call and footer-safe render path. Original Minecraft UI-scale screenshot and font testing remain required before native visual acceptance.


## R5 responsive Minecraft GUI-scale acceptance matrix

A critical visual limitation surfaced while reviewing the actual Slate render path: **Minecraft 1.8.9 Auto GUI Scale does not mean one UI pixel per physical monitor pixel.** Its ScaledResolution increases the factor until the next step would fall below 320x240 in physical resolution. Thus a 1920x1080 Auto viewport generally resolves to **480x270 GUI pixels** (factor four). The full GhostC Slate three-column workspace is intentionally gated behind at least 580x340 panel pixels and therefore uses the safe fallback at that setting. At 1280x720 Auto, the scaled viewport is around **427x240** (factor three), even too short for the existing safe interactive threshold; the resize/reduce-GUI-scale notice is intentional.

There is no claim that the fallback is the intended full Slate interface. A new read-only **REDUCE GUI SCALE** hint is rendered in the top chrome only when the full Slate workspace is unavailable, the Modules page is active, and the native Minecraft font confirms it fits between the HUD navigation and layout/theme buttons. Narrower headers omit the hint rather than draw overlapping text. This does not change saved UI layout, Minecraft options, or hitboxes.

`GuiSlateScaleMatrixTests` now models the actual Minecraft 1.8.9 minimum viewport factor rules, uses the real theme-aware `GuiLayout.calculate(screenW,screenH,layout,theme)` overload, and covers 11 common monitor resolutions, requested GUI scales Auto/1–4, and 25,000 seeded random physical viewports. For every full Slate workspace it checks search/clear disjointness, module and setting row hit gaps, slider/choice containment, tooltip footer exclusion, and content boundaries. Fallback/undersized cases verify safe admission. This is **simulated geometry acceptance**, not original Minecraft screenshot certification; real F7/ClickGUI render and font legibility still need testing at GUI Scale 2/3 as a milestone.

The optional Slate diagnostics panel now follows the same crisp, shadow-free Minecraft font treatment as other Slate HUD components. Indigo and Graphite diagnostics preserve the previous shadowed text route. The Forge source wiring test enforces both paint branches.


## R5 responsive Slate compact renderer — consistent styling under Auto GUI Scale

The original single-column fallback UI (used when Minecraft's scaled viewport cannot admit the full Slate three-pane workspace) now has a Slate-specific visual branch. Its sidebar uses the same graphite/ice tokens, hover regions and live enabled-category counts as the wide view. Module rows use understated selection and hover surfaces, narrow ice accent marks, and restrained ON/OFF/RESET state chips rather than full-bright filled buttons.

Compact setting rows now show human-readable `GuiSettingPresentation` labels, a genuine 27×13 Slate boolean switch, a slim slider track and bounded 4px handle, and an arrow for cycleable choices. A tested `GuiSlateCompactMetrics` helper reserves non-overlapping text budgets between module labels, keybind control columns, numeric values, sliders and choice arrows. Truncated module/setting labels have the same non-interactive Slate hover tooltip as the full workspace, with no tooltip above native toggle or setting-control zones.

The source retains the existing `GuiHitZones.moduleIndex` and `GuiHitZones.settingIndex` for pointer ownership and keeps all `UiActions` calls and settings serialization unchanged. Legacy INDIGO and GRAPHITE/MINT paint branches keep their original solid buttons and values. The new `GuiSlateCompactMetricsTests` validates 50,000 deterministic random fallback layouts, control gaps, and a representative 480×270 scaled-GUI case. It is registered in Core Java 17/21 CI, alongside updated Forge source-wiring checks.

**Scope and limits:** visual implementation plus deterministic layout tests only. Authentic original Minecraft 1.8.9 screenshots at GUI Scale Auto, 2 and 3, and interactive mouse/keyboard acceptance, remain prerequisites to calling this released or visually certified.


## R5 visual reference pass — actual source imagery (28 October 2026)

This iteration explicitly compared **real interface references**, not speculative purple/Nebula themes. Reference links are for design study only; GhostC copies no proprietary artwork, icons, text descriptions, fonts or code.

1. **Vape V4 — official Quick Start screenshot:** https://docs.vape.gg/intro/quick-start , showing an intentionally small dark navigation column, a separate module list, and restrained affordances. Takeaway: distinct navigation and control ownership; don't overload the list with colorful rectangles.
2. **Slinky — official module settings imagery:** https://slinky.gg/ , showing dark, low-noise setting controls and consistent vertical rhythm. Takeaway: favor legibility, consistent control alignment and subtle surfaces. Do **not** copy its orange accent or proprietary elements.
3. **Rise 6 — visual reference screenshot:** https://vantage.rip/rise/rise_gui.png , showing a category/search sidebar, module title and quieter secondary text within each row. Takeaway: establish two-level information hierarchy without inventing content absent from GhostC's registry.

### Source-backed implementation

GhostC's `GhostModule` interface exposes `displayName()` and `category()` and its `ModuleRegistry.Entry` exposes the **current** bound key. It has **no description field**. The Slate 29-pixel module row now draws its original title at `y+3`, with a small muted second line at `y+15` showing the truthful category plus bound key (if any); the 27-pixel interaction footprint and native 29-pixel row stride remain unchanged. The selected module's settings title has a matching muted category breadcrumb at `detailsY+34` above the existing divider. The switch, fault/reset state, selection and scroll behavior are untouched.

The new pure-Java `GuiSlateModuleCaption` removes hostile/control-like key names, caps shortcut display at 12 ASCII characters, preserves an empty/unbound key as category-only, and uses Minecraft font fitting in Forge rather than guessed fixed-width strings. Its `GuiSlateModuleCaptionTests` suite validates all categories and 40,000 deterministic generated key strings against an independent font-width budget. Forge wiring assertions require the actual new row and breadcrumb paint calls.

### What still needs visual acceptance

Capture both **full Slate** and **compact Slate** in original Minecraft 1.8.9 at GUI Scale 2, 3 and Auto. At minimum, check: long title shortening, category/keybind secondary baseline, STATUS/FAULT distinction, live toggles, two-column gutters, search focus, F7 editor and overall black/graphite/ice-blue contrast. Do not call the screenshots finished until they exist; CI compilation alone is not a pixel-perfect visual review.


### Reference-led search empty state (same 28 October pass)

The Rise 6 screenshot at https://vantage.rip/rise/rise_gui.png demonstrates that search and module discovery should have clear, legible information hierarchy instead of a dead blank panel. On the Slate three-pane Modules page, a zero-result filtered view now draws one restrained 156×56-or-smaller card centered **within the actual visible module-row viewport**. It says **NO RESULTS** and either **CHANGE SEARCH** or **CHANGE CATEGORY**, based on whether a search string exists. The details panel provides a corresponding **NOTHING TO CONFIGURE** notice. This is not a new clickable action or module and does not change filtering, selection, keybind capture or scroll state.

A separate `GuiSlateEmptyStateLayout` helper computes card placement without referencing Forge, LWJGL, font assets or game runtime. `GuiSlateEmptyStateLayoutTests` checks 40,000 seeded scaled viewports for horizontal containment, row-region containment, icon-free text padding and stable height; the Forge wiring suite verifies the actual `filtered.isEmpty()` paint branch. The card uses GhostC's own restrained graphite/ice colors, not copied Rise assets. Native screenshot acceptance remains outstanding.
