# GhostC R2.30 — viewport-safe Runtime Diagnostics HUD

## Source-audited rendering defect

The optional Forge diagnostics panel was always 286x180 scaled pixels, with 14 static text baselines ranging from y+17 to y+160, even on screens smaller than the panel. Text was drawn untrimmed, so large numeric counters and long mod/version labels could overrun the right side. This limited the usefulness of the R2.17–R2.19 FPS/snapshot profiler when accepting Minecraft at small GUI scales.

## Implementation

- New host-independent Java8 `DiagnosticsHudLayout` calculates a panel inside the current scaled viewport, preserving the original 286x180 size, top-right anchor, 11px row stride and all 14 rows in normal windows.
- A panel that cannot fit a heading and a single complete line is suppressed, not drawn partially beyond the bottom.
- If vertical space is tight, the visible rows are selected from a stable 14-index priority order: FPS, heap, snapshot timing/volume/output/class, ESP counters, then lifecycle/GUI/input/action counters.
- All text, including the heading, is clipped using the active Minecraft font's `trimStringToWidth` for the remaining measured panel width. The original normal-width text and background colors remain unchanged.
- For alternate font heights, row origin and stride increase to prevent label overlap. HUD diagnostics collection, numeric report schema, privacy, combat/input, module defaults and persisted profile settings are unchanged.

## Verification and limitations

New `DiagnosticsHudLayoutTests` includes normal-size parity, a 180px-high window, 240x120 compact view, tiny-window suppression, extreme font heights, invalid row rejection, and 30,000 seeded variable viewports/font sizes checking complete visible baselines, unique prioritized rows, original full-size ordering and bounded backgrounds. Python Forge-wiring checks ensure layout selection and trimming are actually used. JAR inventory and Windows owner acceptance checklist include the new policy.

The R2.30 candidate must pass Java 17/21 core CI plus Linux/Windows JDK8 Forge compilation/reobfuscation, JAR verification and Windows simulated installation/collection/rollback before merge.

**Boundary:** Neither the revised HUD appearance nor full client stability is proven in Minecraft yet. FPS improvements are not claimed. G1/G2/G4/G5 acceptance, licensed Forge launch, other-mod compatibility and 60-minute real-game soak remain open.
