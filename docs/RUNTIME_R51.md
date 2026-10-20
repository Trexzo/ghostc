# GhostC R2.31 — font-fitted Keystrokes and CPS labels

## Source-audited display defects

The Keystrokes overlay shortened remapped key names by repeatedly removing the last Java UTF-16 `char`; an unusual Unicode key name could lose half a surrogate pair or still exceed a tiny cell. It also rendered unsanitized formatting/control text. The CPS HUD used an unchanged 103px background but rendered `L n | R n CPS` without measuring the text, allowing long numeric counters to extend outside the card. Its background could also exceed a Minecraft scaled screen smaller than the widget, and left/right counts were sampled from separate `System.nanoTime()` calls.

## R2.31 implementation

- Add Minecraft-independent Java8 `HudTextFit`. It accepts the current font-width callback, bounds input scanning to 128 Unicode codepoints and output to 64, removes control and Minecraft formatting markers, rejects lone surrogate code units, preserves genuine supplementary Unicode characters, and only truncates on codepoint boundaries with `...`. Display labels that already fit are kept exactly.
- Forge Keystrokes cells use the shared font fitter; the 25px / 83px cell widths, fixed rows, positioning, pressed colors and physical keybinding mapping remain unchanged.
- CPS retains the original `L n | R n CPS` or `L n CPS` when it fits. For longer figures the policy tries shorter exact-count forms before visibly truncated text. Preserve right-side toggle semantics.
- Forge clamps the original 103x17 CPS rectangle to the scaled viewport, omitting it if insufficient room for a complete glyph line. It captures both CPS counters at the same monotonic time. Normal-screen anchor, colors and saved HUD positions do not change.
- No changes to targeting, combat/packet code, module defaults, profiles, updater, telemetry schema or original artwork.

## Source / CI verification

`HudTextFitTests` adds deterministic null, formatting, control, unpaired/supplementary-surrogate, out-of-range-font, full-width legacy-label, compact high-CPS and tiny-width cases, plus **18,000 seeded mixed-glyph labels** and **15,000 randomized CPS readouts**. Forge wiring asserts the new fit APIs, bounded CPS card and same-time count sample; compiled JAR verification requires the policy class. The standard Java 17/21 core, Linux and Windows JDK8 Forge compilation/reobfuscation, safe archive and simulated Windows installation/collection/rollback gates must pass on the **exact PR head** before merging.

**Acceptance boundary:** source tests cannot establish real onscreen Unicode glyph appearance, integrated screen-scale behavior, other-mod compatibility, measured FPS or a stable 60-minute licensed Minecraft 1.8.9 session. Maintain G1/G2/G4/G5 runtime acceptance tasks.
