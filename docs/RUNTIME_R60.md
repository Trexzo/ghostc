# GhostC R2.40 — Unicode-safe, bounded ClickGUI labels

## Source-audited defect

In R2.39, `GhostClickGui.trim` measured the entire label and repeatedly removed **one UTF-16 code unit** with `substring(0,length()-1)` until the font width fit. For labels containing supplementary-plane Unicode (emoji and some international characters), that loop can cut the high and low surrogate pair in half and draw an invalid label. It also executes unbounded repeated font-width calls for an excessively long string every rendered ClickGUI frame. The shared R2.31 Keystrokes/CPS fitter already handles scalar-safe truncation; the ClickGUI was not using it.

A second bug was confirmed in the shared `HudTextFit.fit` sanitizer: it bounds the scan at 128 source codepoints and 64 visible codepoints, but previously returned the **bare 64-scalar prefix** if it fit the pixel budget. That silently hid further source content without the `...` marker. This could misrepresent a long module/setting label.

## R2.40 changes

- `GhostClickGui.trim` now delegates to `HudTextFit.fit(text,Math.max(0,maxPixels),guiFontWidths)`; the callback measures with Minecraft's **active** `fontRendererObj.getStringWidth` and is allocated just once per GUI instance.
- Removes the old per-character `substring` loop. Surrogate pairs stay intact, format/control sequences are stripped consistently with the existing Key/CPS HUD policy, and labels are truncated with a visible `...` whenever the active pixel budget permits it.
- Updates `HudTextFit.clean` to remember whether its input source was truncated by the 128-source / 64-visible codepoint cap. `fit` then uses an ellipsis even when the sanitized 64-scalar prefix would otherwise fit the budget. Ordinary short labels still return unchanged; tiny budgets still suppress labels safely.
- Existing keybinds, GUI hit areas, selected modules, saved profiles, HUD positions, textures, fonts and module defaults remain unchanged. Using the existing shared fitter changes the visual truncation suffix from the GUI's previous `..` to an explicit `...` (where available), and strips formatting codes.

## Verification

- Existing `HudTextFitTests` retain 18,000 varied font/string scenarios and 15,000 CPS cases, plus more than 5,000 newly seeded long labels containing non-BMP Unicode. Test the actual font budget, codepoint bound and absence of unpaired UTF-16 surrogates.
- Exact boundaries: 64 visible scalars unchanged; 65 scalars (including an emoji) visibly ellipsized even with a generous pixel budget; 10,000-character input requires no more than 70 callback font measurements.
- Forge source regression verifies GUI reuses the shared fitter and cached Minecraft callback, that the legacy `substring` loop is gone, and that the sanitizer explicitly tracks truncation.
- Require exact PR-head Core Java17/21 and Linux/Windows JDK8 Forge reobfuscation, verified mod JAR/provenance and Windows fake-profile acceptance/rollback; independently verify the post-merge `main` commit.

**Runtime scope:** Headless tests can establish Unicode/pixel-width logic but not rendering parity on the real Minecraft font for every custom resource pack, ClickGUI screenshot fidelity, launch success, FPS or 60-minute stability. These in-game G1/G2/G4/G5 acceptance gates remain open.
