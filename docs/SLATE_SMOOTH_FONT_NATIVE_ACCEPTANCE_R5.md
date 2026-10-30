# GhostC R5 Slate — native font visual acceptance (Minecraft 1.8.9)

**Status: Draft / opt-in experiment only.** This document prepares evidence collection; source-level CI is not equivalent to a native Minecraft screenshot or an FPS measurement.

## Two runs, same environment and UI configuration

Build the exact draft PR #181 commit with the project's usual Forge 1.8.9/JDK8 workflow. Keep the same game resolution, OS/JRE, modules enabled, HUD positions and settings in both runs. Do not merge this draft to run the comparison.

1. **Control (default):** Launch with no smooth-font JVM flags. GhostC paints Slate with Minecraft's original bitmap font, including its existing 1.125×/0.875× text hierarchy.
2. **Experimental:** Add both JVM properties to the **Java process launching Minecraft** (not to an in-game command or GhostC text field):
   `-Dghostc.slate.smoothFontExperimental=true -Dghostc.slate.fontDiagnostics=true`
   This affects the Slate ClickGUI's headings and module captions only; Target HUD uses the existing Minecraft font and is a useful untouched reference. Leave the flags absent for normal play.

### Native screenshots (F2 in-game)

For each run, set Minecraft's GUI Scale to **Auto**, **2**, and **3** in turn. Open F7, select Modules, then select the HUD editor. Use the exact same viewport and content in the paired screenshots. Use Minecraft's ordinary F2 screenshot path or the built-in screenshot UI. Do not crop the screenshots before reviewing alignment and clipping.

Frame capture checklist:

- Wide Slate layout: category/sidebar captions, medium-length enabled modules, a very long name, category/keybind subline, selected module details, keybind/state chip, long-label tooltip.
- Compact Slate layout (especially Auto on a smaller resolution): primary name vs secondary category line, 25px row boundaries, hover/selected state, module toggle area and search results.
- F7 HUD editor: heading, tabs, position readout, dynamic widget previews. Also compare Target HUD to ensure the experimental font flag did not change its paint path.
- Inspect `g`, `j`, `p`, `q`, `y`, uppercase `GHOSTC`, underscore and punctuation. **All words in the same row must share a baseline**, without descenders clipping into adjacent rows.

## Aggregate trial report

Close the **F7 ClickGUI** after an experimental pass. The same Minecraft/Java console may print one line with prefix:

`GHOSTC_SLATE_FONT_TRIAL attempts=<N> painted=<N> fallback=<N> fitHits=<N> rasters=<N> uploads=<N> evictions=<N> errors=<N> peakTextures=<N> peakLayouts=<N> avgDrawMicros=<N>`

This line is emitted only if both experimental properties are present. It contains **only aggregate counts**, not module titles, player names, keybindings or text from the UI. Its average is CPU-side time per attempted smooth-font call; it is **not** a measured whole-game FPS value, GPU frame time, or proof that native visuals look good.

Acceptance thresholds/observations:

- `errors=0`, no client crashes, stable zoom and category changes.
- `painted>0`. If `painted=0`, the smooth font is not being exercised and any visual comparison is inconclusive.
- `peakTextures<=96`, `peakLayouts<=192` by construction.
- Repeating a stable UI should increase `fitHits` much faster than `rasters` or `uploads`. Excessive uploads indicate cache churn.
- Close and reopen F7 several times. No missing or colored glyphs, no persistent GL tint or unrelated texture corruption, no stale entries after closing.
- Use the game's F3 FPS and frame-time graph as a rough directional comparison only; record several stable samples in both runs at the same viewport. Native acceptance should also include input response, screenshot inspection and GPU-state/resource-reload behavior.

## Decision rule

Leave the smooth font **disabled by default** unless paired screenshots clearly improve readability and spacing, diagnostic counts show actual paint use, no lifecycle state leak occurs, and native rendering performance is acceptable. A fallback to vanilla is a safe behavior, not evidence that the smooth font works. Retain this visual-only PR as draft; the parallel combat branch remains untouched.

## 30 October — pre-upload geometry and repeat-fallback optimization

The experimental font now checks actual raster width and baseline-safe vertical placement **before** uploading a texture. A bounded 96-entry, geometry-only negative cache remembers non-fitting glyph runs: a repeat at the same small row dimensions falls back without another Java2D rasterization, while a larger budget can retry normally. Closing the GUI and invalidating the OpenGL texture context clear the denial cache. No text is logged, and the default font path is unchanged. Core randomized admission checks and Forge source contracts cover the ordering; native frame-time and screenshot acceptance still remain outstanding.
