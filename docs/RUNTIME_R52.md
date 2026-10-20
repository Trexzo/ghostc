# GhostC R2.32 — full-widget Keystrokes and Target HUD viewport admission

## Audited issue

R2.31 introduced font-safe Keystrokes and clipped CPS labels, but the Keystrokes renderer could still issue Minecraft draw calls across 83 scaled pixels when the entire screen was narrower than 83. The Target HUD could similarly paint its fixed 160px rectangle or 29/43px height outside a tiny screen. R1.9 `HudPositions.pixelX/Y` deliberately clamps the anchor to zero when a widget is wider than the screen; this alone does **not** constrain draw calls.

## Change

- Introduce host-independent Java8 `HudWidgetViewport.place` as a complete-footprint admission and coordinate policy. The function uses the same `HudFootprints.width/height` as the R2.1 HUD editor and existing R1.9 normalized `HudPositions` anchors.
- It returns no drawable frame when the scaled width/height is smaller than the widget, rather than painting a partially visible card. Full-size placement is bit-for-bit anchored through the previous `pixelX` and `pixelY` APIs.
- Forge Keystrokes and Target HUD both use the shared frame. Target HUD checks admission before doing world snapshot selection and entity lookup, avoiding work when its card cannot be displayed.
- Dynamic settings are honored: Keystrokes uses 37, 57 or 77px depending on jump/sneak rows; Target HUD uses 29px with health hidden or 43px with health enabled. Original 83px/160px widths, font, colors, gl guards and saved positions are retained.
- Existing R2.31 CPS clipping remains in place for viewports narrower than its original 103px card. No profile migration or changed module default.

## Verification

A Java8 regression suite of 50,000 deterministic randomized viewport/anchor/settings scenes checks full containment, exact legacy normalized coordinates, widget-editor dimension parity and small-viewport admission, together with exact-size and invalid-input edge cases. Forge source checks require both widgets to use the policy, suppress unfit cards and keep the R1.9 anchoring bridge; the JAR inventory includes the compiled policy.

Required before merge: Java 17/21 Core CI, Linux and Windows JDK8 Forge 1.8.9 build and reobfuscation, ZIP/JAR verification and simulated Windows install/collect/rollback, all on the exact PR head.

**Runtime boundary:** Actual Minecraft 1.8.9 panel placement, font rendering, multi-mod interactions and a 60-minute gameplay soak have not been observed. CI is not in-game acceptance. Keep G1/G2/G4/G5 gates open.
