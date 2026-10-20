# GhostC R2.29 — bounded Unicode Target HUD name handling

## Source-audited defect

R2.28 parsed player names as Unicode codepoints but checked `Character.isSurrogate((char)cp)`. Casting a valid supplementary codepoint such as U+1D800 down to 16 bits falsely classified it as an unpaired surrogate. The existing 64-visible-character limit also allowed arbitrarily long scans of names composed entirely of Minecraft formatting pairs or malformed characters. The Forge Target HUD instantiated a new `ToIntFunction` on every draw.

## Scope

- Compare full codepoints against the surrogate interval; reject lone UTF-16 surrogates, preserve supplementary characters.
- Cap the input scan at **256 codepoints** independently from 64 printable codepoints. Existing `§`-pair handling and `Player #ID` fallback are unchanged.
- Reuse one Java8 font-measurement callback per Forge driver, querying `mc.fontRendererObj` each time rather than caching possibly stale font metrics.
- Preserve HUD dimensions, placement, health display, saved profiles, player selection, world fences and all existing module behavior.

## Verification boundary

The original 14,000 seeded variable-width-font cases and persisted health mode tests are retained. Added deterministic regressions for U+1D800, lone surrogate units, 50,000 formatting pairs with a trailing name, and the exact 256th input codepoint. Source wiring assertions check the bounded scan and one retained Forge callback. Required merge checks: Java 17/21 core/source regressions; Linux/Windows Forge JDK8 compilation, reobfuscation and JAR inventory; simulated Windows install/collect/rollback.

**No live Minecraft visual, FPS, mod-compatibility or 60-minute soak result is claimed.** G2/G4/G5 runtime milestones remain open.
