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


## R5 compact search unification — Vape V4 / Slinky / Rise reference pass

Reference evidence: the official Vape V4 [Quick Start search/navigation screenshot](https://docs.vape.gg/intro/quick-start) separates module discovery from toggles and settings; Slinky's [official UI references](https://slinky.gg/) favor subdued controls and clear active focus; Rise's [GUI screenshot](https://vantage.rip/rise/rise_gui.png) prioritizes search and readable text. GhostC's implementation is original: no borrowed image assets, shaders, fonts or source code.

**Actual compact Slate implementation:** when Minecraft GUI Scale prevents the full three-pane interface, the previous vanilla-looking search textbox is replaced with a graphite 21px custom surface around the native 1.8.9 `GuiTextField`. A small, original pixel-art magnifier sits *inside* the left gutter; the real textbox has a matching inset and a shorter width, while the far-right 14x15 clear target is separated from the text caret by 7px or more. An empty unfocused search displays **Search modules**; keyboard focus turns the border ice-blue and pointer hover gets a subtle graphite outline. A nonempty search displays an independently clickable **X** which clears the native search, resets filtering/scroll, restores caret focus and never toggles a module. Any surface click focuses the real text field, preserving caret placement when clicked directly and falling back to end-of-text for padding/icon clicks.

The new `GuiSlateCompactSearchGeometry` is the source of truth for drawing, textbox instantiation, clear hit testing and full surface focus. `GuiSlateCompactSearchGeometryTests` runs 40,000 deterministic random viewport configurations, verifying exclusive clear boundaries, caret/clear disjointness, icon gutter, panel containment and separation from the module list. Forge source wiring assertions prevent the input/render split from silently regressing. Other themes and the existing wide Slate search retain their original pathways. Genuine original Minecraft 1.8.9 visual-scale and pointer acceptance remain pending.


### Matching module hierarchy at compact GUI scales

Compact Slate module rows now use exactly the same **primary title + muted actual category/keybind** information structure as the full Slate workspace, anchored at `ry+3` and `ry+15` inside the existing 25px painted row. They reuse the tested `GuiSlateModuleCaption` sanitizer and the compact label pixel-width budget. This follows the two-tier type hierarchy visible in Rise rather than the older one-line or neon-box module UI; there is no fake descriptive copy. Legacy Indigo/Mint rows keep their previous single-line `ry+8` paint path. Input row hitboxes, toggles, keybind capture and active states are unchanged.


## R5 Target HUD visual pass — source-based Vape / Slinky / Rise references (28 October)

**Reference policy:** use public documentation for composition, hierarchy and restraint, not copied assets, shaders, fonts or source code.

- **Vape V4** — https://docs.vape.gg/features/overlays/ explicitly describes `Target Info` as a compact HUD overlay for target health, potion usage and similar context. We retain GhostC's truthful live name, health and distance labels in a 160px card, without implying unsupported potion/state telemetry.
- **Slinky** — https://slinky.gg/ and https://t.me/s/SlinkyUpdates describe the client and configurable mod overlays, including padding, text/detail colors and subtle overlay surfaces. We keep Slate's existing matte graphite surface, muted secondary text and limited ice-blue accents; we do not add heavier rounded GPU shadows or invent additional settings.
- **Rise NextGen** — https://beta.riseclient.com/ features a Target HUD in its duel footage. We carry over the principle of readable opponent identity and low-noise health emphasis, not its brand artwork or combat mechanics.

### Actual visual implementation

`ForgeFeatureDriver.drawSlateTarget` now keeps its existing 160×43 (HP on) / 160×29 (HP off) card and true Minecraft name/health/distance data, while adding a faint horizontal separator below the text row, a matte five-pixel HP track with a light upper highlight, a slim active-end marker and **three quiet quarter-position ticks**. The ticks are not colored damage thresholds; they are neutral scale guides at 25%, 50% and 75%, while health itself remains the existing ice-blue fill. The arrangement reads as one restrained layered card rather than several brightly-colored stripes. There are no new player/head textures, icons, or gradient shaders.

All track calculations and quarter marker positions use the allocation-free pure Java `TargetHudSlateBarGeometry`. `fits()` admits the name baseline, divider, five-pixel track and y+33 distance baseline together, so small synthetic cards are rejected rather than half-painted. `filled()` clamps nonfinite/out-of-range health fractions, although the live Target HUD also retains `TargetHudMotion`'s own bounded smoothing. `TargetHudSlateBarGeometryTests` exercises **50,000 seeded widths/origins/heights/fractions**, zero/full/NaN and true 160px insets. Core CI runs this suite on Java 17/21; Forge source wiring asserts real `drawSlateTarget` use, and JAR verification requires the class.

**Isolation:** Classic, Glass and Minimal Target HUD painters remain unchanged; target acquisition, world/entity identity checks, live health/invisibility/range admission, team filtering, animation activation, save format, drag anchors and scaling remain unchanged. No combat module or parallel development branch has been edited. Genuine Minecraft 1.8.9 visual screenshots at GUI scales Auto/2/3 are still required for pixel-level acceptance.


### Slate RANGE readout — two-column type hierarchy

The original Slate Target HUD used a single right-aligned distance string in the bottom row. In the reference-led pass this is now a low-contrast **RANGE** caption at the left margin and a measured live distance value aligned to the right; the values are produced only from the existing `TargetHudDistanceText.format` live position calculation. `TargetHudSlateReadoutGeometry` reserves an 8px inter-column gap and validates the true font-measured right-side text width before paint. If the value cannot fit, it is omitted rather than colliding with the label; no distance is fabricated.

The layout applies to both 160×43 health-visible and 160×29 health-hidden Slate cards, using the pre-existing y+33 and y+18 baselines. It adds no data collection, target selection changes, combat state, GL shader, or new persisted settings. `TargetHudSlateReadoutGeometryTests` now checks 50,000 deterministic widths/label sizes/origins plus both existing card heights. Core CI and the actual Forge source/required-artifact checks enforce this arrangement. Classic/Glass/Minimal preserve their previous single-line distance rendering.


## R5 ArrayList typography and moving per-module surfaces — 28 October

### Real client references, reviewed for this batch

- **Vape V4 Text GUI:** https://www.vape.gg/changelog/v4 (4.17 aesthetic rework and suffix options) and https://docs.vape.gg/features/overlays/ (Text GUI is the HUD list of enabled modules). **Design application:** every element remains a genuine enabled module name with legible alignment; no invented suffixes or telemetry.
- **Slinky Mod overlay:** https://docs.slinky.gg/modules/visual/mod-overlay/ documents independent X/Y padding, text color, details color, background rounding, module category filters and conditionals. **Design application:** measured text padding, restrained optional per-label backplates and low-noise accents; do not copy its orange color or add unsupported conditionals.
- **Rise:** the previously reviewed screenshot https://vantage.rip/rise/rise_gui.png informs the clean type hierarchy shared with GhostC's other Slate surfaces. Do not reproduce proprietary visual assets, animations or module descriptions.

### Implemented in the native 1.8.9 Forge ArrayList painter

Prior to this pass, Slate's optional background rendered *every* enabled-module row using the width of the longest module name, forming one rectangular slab even for short names. It also placed the optional side stripe at a fixed row edge while the text slid in. The new pure-Java, allocation-free `ModuleListSlateRowGeometry` measures each label from the existing cached `ModuleListLayout.Row.textWidth`, uses 6px symmetric padding, and positions its background relative to `ModuleListTextGeometry.x`'s live label position. Thus the optional translucent graphite backplate follows the same restrained horizontal entry as the text. Each row has its own width; its optional accent is a short, centered **2px ice-blue dash**, not a full-height color stripe. A faint bottom hairline ties the overlay to the Slate Target HUD and keystroke controls. No extra font measuring, new gameplay data, gradients or shader state is introduced.

The primary module labels are still **crisp and shadow-free** in Slate, while the Classic and Minimal HUD painter branches remain pixel-identical to the earlier version. Disabled or faulted modules are excluded by the unchanged `ModuleListLayout`, and `ModuleListMotion` retains its existing bounded entry animation and eviction behavior. Existing settings still govern background and accent visibility; there are **no new user options**, saved profile changes or sorting/placement semantics.

`ModuleListSlateRowGeometryTests` runs 50,000 deterministic random real-layout viewports, checking per-label width, on-screen containment across 21 animation phases, label-inside-surface containment, short rail placement, left/right/custom anchoring and deliberately invalid geometry. Its CI marker is required on Java 17/21, the Forge source-wiring suite asserts real use by the painter, and the reobfuscated JAR verifier requires the new class. This is source/geometry and build validation only: **genuine original Minecraft 1.8.9 screenshots** and legibility checks at GUI Scale 2/3/Auto are still required before visual release certification.


## R5 F7 Slate HUD editor — user-owned source references (29 October 2026)

### Inspected references and provenance

The user's ChatGPT Library contains `Vape_vs_Slinky_Deep_Source_Comparison_2026-10-12.md`, an earlier structural comparison of official Vape documentation and the user's recovered Slinky source bundle. The original `Slinky-SourceBuild-And-WindowsBundle.zip`, `VapeV4.21-latest.zip`, and `Rise 6.9.5.zip` were **not directly retrievable as Library ZIP attachments** in this review. Instead, the following **user-owned GitHub repositories** were inspected directly; they are recovered/community code, not guaranteed to match signed vendor releases:

- `Trexzo/OpenSlinky` — `SlinkyClient/src/main/java/dev/slinky/client/gui/GuiPresentation.java`, `GuiWidgets.java`, and `settings/ModuleSettingsLayout.java`: explicit font-width-aware text alignment, hover-aware widget highlights, spatial separation of settings sections and controls, and independently measured text areas.
- `Trexzo/VapeV4.21` — `src/main/java/gg/vape/module/none/TextGuiSettingsFrame.java`: HUD editor/overlay component with persisted positioning, right-side anchoring and configuration controls. No geometry, private assets, or implementation copied.
- The saved Rise visual research from `Inklistrad text(10).txt` and previously reviewed UI image remains a secondary composition reference: clear hierarchy, restrained text-first overlays and enough space around settings.

### Implemented original GhostC visual decisions

`HudEditorSlateCanvasGeometry` now drives a **responsive thirds grid** rather than a dense fixed 30px matrix in Slate, with quieter center axes inside the actual letterboxed preview. The Indigo/Graphite editor path still draws its old 30px grid. This follows Slinky's emphasis on measured composition rather than a fixed-size ornament.

The selected preview is now indicated with short **ice-blue corner brackets** that scale down to very small Minecraft GUI preview rectangles; a different hovered widget gets a muted outline. Topmost hover resolution deliberately follows the actual native click order (`Module List -> Target -> CPS -> Keys -> Status`) without introducing new input regions or changing selection. The selected-item header shows its **real normalized anchor** as `Status X 10% Y 20%` style metadata, and the scale readout remains at the right. This draws on Vape's explicit overlay placement controls without implementing proprietary gestures.

The new `HudEditorSlateCanvasGeometryTests` suite checks **50,000 seeded letterboxed viewport combinations**, guide containment, centered corner bounds, actual preview hover priority, safe handling of tiny GUI scales and finite normalized position strings. Core Java CI executes the suite; Forge source checks require the new visual methods and legacy-theme guard; Forge artifact verification requires `HudEditorSlateCanvasGeometry.class`.

**Safety and acceptance:** This is read-only editor presentation. Existing drag/nudge/snap, original screenshot preview footprints, click regions, profile persistence, HUD widgets and combat features remain untouched. Source/CI passing is not pixel-level proof: original Minecraft 1.8.9 captures at GUI Scale Auto, 2 and 3 remain an explicit visual quality gate.


### Slinky-derived *principle*, original F7 tab typography

The recovered `Trexzo/OpenSlinky/SlinkyClient/src/main/java/dev/slinky/client/gui/GuiPresentation.java` measures actual font advance and centers glyphs within controls; `GuiWidgets.java` separates hovered/selected control visuals from text and geometry. GhostC now follows that general ergonomic idea with independently implemented `HudEditorSlateTabTypography`: each F7 tab fits its own actual Minecraft `FontRenderer` text budget, centers the measured glyph run in the same `HudEditorControls.tabAt` click region, and draws a short centered ice-blue active underline. Hovered tabs use a quiet graphite surface. **Only Slate is changed**; Indigo and Graphite retain their previous 17px row and left-aligned text. The separate `HudEditorSlateTabTypographyTests` covers 50,000 deterministic parent widths and selected tabs, checking centering, underline containment and tiny viewport cases. CI and Forge artifact verifier enforce it. No proprietary control layouts, colors, fonts or art are copied.


## R5 Slate measured typography — owned Slinky/Vape source references (29 October 2026)

**Reference origin:** The ChatGPT Library comparison `Vape_vs_Slinky_Deep_Source_Comparison_2026-10-12.md` and earlier saved visual audits; the directly accessible user-owned `Trexzo/OpenSlinky` and `Trexzo/VapeV4.21` repositories. These recovered/community projects do not establish official release or binary parity. Original archived vendor ZIPs were not separately available for this turn.

### Findings used for this batch

- `Trexzo/OpenSlinky/SlinkyClient/src/main/java/dev/slinky/client/render/font/CanvasFonts.java` exposes an isolated font engine that measures text and manages its own GPU atlas and font lifecycles. `FontServices.java` derives glyph bounds and cursor advances from actual metrics, while `GameFontProvider.java` demonstrates that the in-game Minecraft glyph resources can be used instead of licensing third-party font files. These indicate why a single flat 9-pixel text size is an incomplete typography system, but we do **not** copy their engine or assets.
- `Trexzo/VapeV4.21/src/main/java/gg/vape/module/none/TextGuiSettingsFrame.java` exposes separate `smoothFont`, `scale`, `shadow`, and `animations` settings for Text GUI. These demonstrate the importance of explicit control over type presentation; they do **not** guarantee how proprietary official Vape renders.
- GhostC already has measured `HudTextFit.fit` based on the current Minecraft font and a scoped `RenderStateGuard`. This is a reliable base to introduce a true type hierarchy while retaining Java 8 and 1.8.9 compatibility.

### Implemented

Added original, allocation-light `GuiSlateTypeScale` that bounds actual measured glyph advances in **post-scale output pixels**, with `PRIMARY=1.125` and `SECONDARY=0.875`. It accepts the active Minecraft `FontRenderer.getStringWidth` measurement, uses bounded Unicode-aware fitting, and rejects invalid/nonfinite scales. Source-independent `GuiSlateTypeScaleTests` exercise 60,000 deterministic variable-width font/sample/budget cases, supplementary codepoints, Minecraft formatting markers and row-baseline admission.

The Forge `GhostClickGui.drawSlateType` method renders these strings with a matrix push/translate/scale and guaranteed pop. It does not mutate global font assets, texture filtering, OpenGL shader state, Minecraft profile values or input hitboxes. The scale is currently **only an internal Slate design token**, not a newly persisted setting.

- **Wide Slate**: module name uses the larger title at `yy+2`, real category/keybind secondary line uses the smaller text at `yy+16`, and the selected module's details heading/breadcrumb share the hierarchy at `detailsY+21` / `+34`. Widths are fitted after scaling, leaving the state-switch/status regions untouched.
- **Compact Slate**: equivalent title at `ry+2` and metadata at `ry+14` remain inside the original 25px module row and use the original clickable bounds. Legacy Indigo/Graphite rows retain their original one-line rendering.
- **Slate global headers**: the GhostC wordmark and F7 HUD editor heading gain the same primary scale. The selected anchor readout is horizontally positioned with the **scaled actual measured title width**, preventing text overlap.
- Long-name tooltips compare against the actual *scaled* fitted title, so clip discovery matches painted text.

This is a genuine paint-path hierarchy improvement but **not** yet a custom antialiased font renderer. Fractional scaling of bitmap glyphs may cause visual softness depending on Minecraft GUI scale; screenshots and FPS measurements at GUI Scale Auto/2/3 are required before declaring it superior to the reference clients. If fractional rendering is visibly worse, retain the measured layout contracts and replace only the scoped paint backend after native evidence.


### Slate Target HUD shares the new primary/secondary font system

The native `ForgeFeatureDriver` now renders the **same actual live player name** at the primary scale (1.125×), measuring and re-fitting it against the original 160px card and the health-text column before painting. For the 9px Minecraft font, its y+6 baseline occupies 11 logical pixels, clearing the existing Slate y+18 name/health separator. The same live HP value and live-distance formatter remain unchanged; their labels are rendered at the smaller secondary scale (0.875×) with explicitly scaled measured widths. `TargetHudSlateReadoutGeometry` still owns right alignment and the 8px title/value separation; its budget is converted to *source* font pixels before asking `TargetHudDistanceText.format` to fit a value.

The paint-only `drawSlateHudText` method uses scoped GL matrix push/pop with no imported fonts, permanent GL filtering changes, player/target acquisition changes or persisted settings. Classic, Glass and Minimal skins remain untouched. This is proportional bitmap text, not a new antialiasing renderer; in-game screenshot and frame-time acceptance are still needed.


## R5 staged smooth-font backend — directly referenced source, opt-in (29 October 2026)

**Source references inspected:** the user's recovered/community `Trexzo/OpenSlinky` project `SlinkyClient/src/main/java/dev/slinky/client/render/font/BundledGuiFonts.java`, `CanvasFonts.java`, `GameFontProvider.java`; and `Trexzo/VapeV4.21` `src/main/java/gg/vape/ui/click/frame/impl/TextGuiOverlayComponent.java` plus `TextGuiSettingsFrame.java`. Slinky's code shows separate GUI text faces (Inter Medium/Semibold), a glyph atlas and explicit resource/texture cleanup. The Vape recovery shows a separate `SmoothFontRenderer` and a smooth-font setting. Neither is guaranteed to match an official signed vendor release. The implementation below is original and imports **no external or reference font files**, atlas, renderer classes, code or assets.

### Implemented and safety boundary

`GuiSlateSmoothRaster` is an independent Java 8 / AWT CPU raster with the JRE logical `Dialog` face, antialiased glyph edges, real `FontMetrics` width/ascent, Unicode-safe `HudTextFit` clipping and hard **512×48** maximum surface. Its packed alpha-only glyph pixels are tinted by the existing Slate color policy, not copied source assets. The separate Forge `SlateSmoothFontRenderer` uploads these glyphs as temporary white/alpha GL textures, reuses a bounded **96-entry GPU texture cache** plus a separate bounded **192-entry AWT text-metrics/fit cache** to avoid per-frame raster/FontMetrics allocations, restores GL texture/color/enable/pixel-store states around upload/painting, and releases its textures when the ClickGUI closes. It does not read combat state or write profiles. It is **off by default** and requires explicit JVM property:

`-Dghostc.slate.smoothFontExperimental=true`

When the flag is absent, the existing Minecraft text rendering executes unchanged; when enabled, the original Slate `drawSlateType` paint sites (wide/compact module name and metadata, selected module detail, GhostC header and F7 heading) may use the smooth experimental backend **only if** the glyph raster fits the available pixel width and vertical line height. On raster/texture failure or insufficient space, the original bounded Minecraft glyph path remains the fallback. The experimental font face is a platform/JRE logical font, so its appearance may vary between Windows and Linux and is not yet a guaranteed visual identity.

**Tests:** `GuiSlateSmoothRasterTests` exercises 25,000 deterministic font-fit inputs and hundreds of actual antialiased CPU rasterizations using Java headless mode, validating codepoint caps, measured width, packed pixel count and non-empty glyph ink. Core CI requires its pass marker; Forge wiring requires an explicitly opt-in property, bounded texture cache, scoped GL state, actual native paint invocation and cleanup. The JAR verifier requires both new classes.

**Release acceptance:** This is an experimental **draft-branch** feature, not a production switch. It is NOT proof of screenshot aesthetics, GPU lifetime correctness across unusual Minecraft render-state interactions, equal font appearance across machines, or stable FPS. Before default activation: obtain in-game 1.8.9 screenshots (Auto/2/3 GUI scale), measure creation/eviction rates and frame time, inspect GL state before and after GUI closure and Minecraft resource reload, and select a consistently licensed face if a cross-platform font is desired. Never ship a vendor's font files or claim pixel-identical Vape/Slinky rendering.


**Antialias coverage safeguard:** The experimental painter temporarily disables the fixed-function `GL_ALPHA_TEST` threshold while drawing the white/alpha texture, preserving low-alpha edge pixels produced by AWT. The surrounding `GL_ENABLE_BIT` attribute scope restores Minecraft's previous alpha-test state immediately. This is a paint-only, opt-in behavior with a Forge source-contract assertion; it does not enable the smooth-font trial by default.


### R5 experimental font ink-bounds corrective pass (29 October)

**Issue identified through actual raster metrics:** The first experimental Java2D raster allocated a texture with `FontMetrics.getHeight()+2`, which includes ascent/descent/leading whitespace not present in the painted letters. In one local headless Java 21 sample, the 10px title font reserved 16px and the 8px secondary font 14px, exceeding GhostC's deliberately fixed 12–14px module-title and 11–12px metadata budgets. This made the smooth renderer silently fall back to Minecraft despite having useful visible glyphs.

**Fix:** `GuiSlateSmoothRaster.raster` now scans each ARGB glyph row for nontransparent pixels, crops only **fully transparent rows**, and retains a one-row antialias guard above and below the actual glyph envelope. It preserves the original horizontal font advance and text budget (no horizontal squeezing or new hitboxes). Whitespace-only strings return a single transparent row. JRE logical fonts vary by platform, so these local bounds are not guarantees for Windows.

**Additional GL safety:** The Forge paint path forces `GL_MODULATE` within its existing `GL_TEXTURE_BIT` state scope, ensuring the Slate accent/text tint multiplies the glyph's white-alpha texture even when another renderer left the texture environment in REPLACE mode. An invalid cached GL texture triggers invalidation of all stale identities (not deletions from a possibly new context); GUI-close cleanup is best-effort and cannot interrupt the vanilla close/fallback path after a GL failure. Source/CI tests now require the crop logic, explicit tint modulation and resilient disposal.

**Remaining acceptance:** Confirm native 1.8.9 (Auto/2/3) actual glyph legibility, baseline and descender clarity, no GL texture-state contamination, bounded GPU texture growth, and frame-time cost with the experimental flag explicitly enabled. **Production stays on Minecraft's original font and the PR stays draft.**


### R5 native trial instrumentation and consistent baseline (30 October)

The previous glyph-ink crop was measurable but painted every cropped run at the same top edge. This could visually move lowercase descenders and all-capital captions up or down relative to one another. `GuiSlateSmoothRaster.Run` now preserves the original AWT baseline as `baselineFromTop`; `GuiSlateSmoothPlacement` aligns all rows to `heightBudget - 3` and refuses an out-of-bounds glyph rather than clipping the row. Its offset may be negative for punctuation below the baseline. The Forge paint path persists baseline metadata in the bounded texture cache and passes the resulting offset to the texture quad. The fallback still renders unchanged Minecraft font if a run cannot fit.

New `GuiSlateSmoothTrialMetrics` offers aggregate-only counts of draw attempts, actual successful smooth draws, fallback, fit hits, rasterizations, texture uploads/evictions, errors, cache peaks and average per-attempt CPU draw time. It does not capture player names, module names, text or personal inputs. It is enabled only when the existing experimental smooth-font JVM flag **and** `-Dghostc.slate.fontDiagnostics=true` are both supplied, and prints one summary when F7 closes. Ordinary users receive no extra logs or activated font behavior.

Core Java 17/21 CI now runs 50,000 deterministic baseline tests and 50,000 aggregate metric tests in addition to the existing 25,000 raster probes; Forge wiring and JAR verification require both helper classes. The [native A/B acceptance guide](SLATE_SMOOTH_FONT_NATIVE_ACCEPTANCE_R5.md) describes how to capture genuine Minecraft 1.8.9 F2 screenshots at Auto, 2 and 3 GUI scales, how to interpret the privacy-safe trial line, and what must pass before enabling a custom font by default. **No actual in-game screenshots or FPS claims have been made yet.**


## R5 Slate F7 Module List live miniature (30 October 2026)

**Design evidence:** The saved user's Vape/Slinky comparison and the Slinky [Mod overlay reference](https://docs.slinky.gg/modules/visual/mod-overlay/) both emphasize an actual enabled-module list with per-label text widths, category visibility and individually controlled accent/background presentation. GhostC's previous `HudPreviewArt.paint(...MODULE_LIST...)` was always a fabricated three-line schematic, no matter how many modules were actually enabled. It was therefore a poor visual representation in the F7 HUD editor, even though the editor already had access to the actual measured `ModuleListLayout.Frame`.

**Implemented:** First-party `ModuleListSlateMiniatureGeometry.paint()` now paints **one compact miniature bar per genuinely enabled row** from the already cached `listFootprint()` frame. Each bar's width is proportional to the active Minecraft font measurement, and its right/left alignment follows the same actual module-list position or saved custom-side anchor as the native Slate overlay. Matte surfaces and short side rails are painted only when the existing Module List `background` and `accent` settings are enabled. Every miniature rectangle is strictly clipped to the original F7 widget bounds; it neither creates synthetic names nor reads combat state. Generic 3-line schematics remain for empty rows, legacy GUI themes or non-Slate Module List appearances. No actual module-list gameplay renderer, input hitbox, profile, widget anchor, scale or sort order was changed.

The native editor calls this renderer only for `ui.theme()==SLATE` and `moduleListAppearance()==slate`. `ModuleListSlateMiniatureGeometryTests` checks 50,000 seeded real enabled-only ModuleRegistry/layout combinations across letterboxed viewport sizes, alignments and background/accent states. Both Core CI and the Forge source/JAR verifier require the new behavior. **Native 1.8.9 F2 screenshots are still required** before judging exact visual parity or making the experimental smooth font the default.


## R5 Slate zero-settings presentation (30 October 2026)

**Visual quality issue:** A genuine selected module with no configurable settings left most of the configuration column entirely blank after the `SETTINGS` heading. Some real utility/render modules expose zero setting rows, and an empty area could be mistaken for unloaded or broken settings. The original source-bound approach follows the reference clients' disciplined grouping and restrained secondary labels; no text, images or artwork from Vape/Slinky/Rise was copied.

**Implemented:** `GuiSlateSettingsEmptyState` is a pure-Java, non-interactive viewport geometry with a minimum 110×47px admission, maximum 72px panel height, muted graphite background, restrained Slate accent rail, divider, two fitted lines (`NO EXTRA SETTINGS` / `TOGGLE OR SET A KEY ABOVE`). It appears only when the **real selected module has zero settings**, separately in the wide Slate workspace and the compact Slate layout. The message explicitly points to the existing native toggle and keybind row rather than inventing controls. If a compact viewport cannot fit the text without intruding on the footer, it paints nothing. It never edits module settings, registry membership, keyboard or pointer hit zones, focus, profile serialization or other appearance themes.

**Verification:** `GuiSlateSettingsEmptyStateTests` runs 50,000 deterministic randomized wide/compact candidate sizes and offsets, asserting no clipping, non-overlapping text baselines, and full containment inside the already reserved settings viewport. Both Java 17/21 Core CI and Forge 1.8.9 source/JAR contracts include the addition. This is a paint-only improvement; native screenshots remain unverified.


### 30 Oct follow-up: actual-font-width safe zero-settings labels

Source review found a real narrow-viewport overflow in the new empty settings panel. Its admitted minimum width is 110px, but the two full text lines could exceed its horizontal margins when drawn with the current Minecraft font. Pixel containment tests originally verified only the rectangle, not the text.

`GuiSlateSettingsEmptyState.copy(frame, fontWidth)` now chooses **complete, truthful wording** using actual `FontRenderer.getStringWidth` metrics: wide surfaces retain `NO EXTRA SETTINGS` and `TOGGLE OR SET A KEY ABOVE`, while compact ones select shorter alternatives such as `NO SETTINGS` and `TOGGLE / KEY`. If neither full caption fits, the entire decorative panel is skipped. It never uses character-count approximations or half-word ellipses, and it does not change toggle/keybind input geometry or setting data.

The existing 50,000 seeded geometry tests now also vary simulated font advance and verify every admitted caption's pixel budget, full short/long copy, and font failure handling. Forge source contracts verify both layouts call the measured-copy helper. Native Minecraft visual acceptance remains outstanding.

### 30 October — selected-module title discoverability

Long real module names can be trimmed by the selected detail heading even if the module-row tooltip is available. Slate now reveals the registered full name on hovering its **existing** wide or compact heading only when the exact native-painted heading was shortened. The pure Java `GuiSlateDetailTitleTooltip` enforces the pre-existing text rectangle, including the wide status-chip gutter and compact keybind separation. Fault-only compact headers do not produce misleading name hints. This is a visual, non-interactive refinement: no input actions, settings or profiles change; legacy themes remain untouched. 50,000 deterministic hover/overflow checks are registered in Core CI, plus Forge wiring and packaged-JAR contracts. True Minecraft 1.8.9 screenshots/FPS acceptance still remain outstanding.

### 31 October — truthful Slate Target HUD miniature in F7

The original static F7 Target HUD pictogram displayed a filled ~70% HP bar in every profile, even when health was disabled. Following Slinky's subdued HUD visual hierarchy and GhostC's existing Slate 160×43 (health-on) / 160×29 (health-off) composition, the editor now uses a pure-Java, clipped `TargetHudSlateMiniatureGeometry` **only** for the Slate Target skin under the Slate F7 theme. The miniature renders a non-numeric target identity bar, quiet top accent, **neutral empty HP track only when health display is enabled**, and distance bars only when the real distance setting is enabled. It never invents an opponent, HP fraction or distance, and never reads combat data. Other theme/skin thumbnails, drag bounds, anchors, settings, click zones, module state, gameplay rendering and profiles remain unchanged. 50,000 randomized small/large footprint and setting-combination checks validate clipped output, exact role counts, absent fictional fill, plus Forge source wiring and reobfuscated JAR inclusion. This is source-level acceptance only: real Minecraft 1.8.9 screenshots/FPS comparisons remain required.
