# GhostC R2.43 — finite and constant-range numeric slider correctness

## Source-audited defects

R2.42 used `(number.get()-min)/(max-min)` to draw ClickGUI number sliders and `min+(max-min)*fraction` to preview changed values. Two legal numeric configurations are not supported by those formulas:

1. **Constant settings:** `Setting.Number` permits `min==max`, but the displayed fraction divides zero by zero, producing `NaN`. A fixed setting should show an empty, non-editable track and retain its value.
2. **Extremely wide, finite ranges:** Java double subtraction can overflow even with legal endpoints, e.g. `[-Double.MAX_VALUE,+Double.MAX_VALUE]`. The preview calculation reaches infinity and `Setting.Number.parse` rejects an otherwise legal finite intermediate value. Drawn fill percentage is similarly corrupted.
3. **Non-finite default:** the `Setting.Number` constructor checked finite min/max and `initial<min || initial>max`, but **NaN satisfies neither comparison**. Thus `new Setting.Number(...,Double.NaN,min,max)` could create an invalid value despite the parser rejecting NaN.

## Implementation

- New pure Java8 `NumericSliderScale.value(min,max,fraction)` validates finite endpoints, an ordered range and finite input. Fractions outside [0,1] clamp to exact endpoints. For ordinary finite spans, interpolation keeps the existing `min+(max-min)*fraction` behavior; if the endpoint subtraction overflows, it uses the convex combination `(1-f)*min+f*max` to preserve finite values.
- `NumericSliderScale.fraction(current,min,max)` returns 0 for a constant range, exact 0/1 at endpoints, the existing normal formula for representable spans, and a magnitude-normalized inverse for otherwise overflowing spans. Every returned fraction stays finite and within [0,1].
- The Forge `GhostClickGui` fill bar and `UiActions.previewFraction` use the **same** scale policy, preventing diverging display and setting changes. The drag capture, stored profiles, one-save-on-release and all UI hit zones remain unchanged.
- `Setting.Number` now rejects non-finite initial values, including NaN and positive/negative infinity. `min==max` remains supported for fixed numeric settings.
- No gameplay, networking, modules, default settings, service ownership, or persistence file formats change.

## Deterministic evidence

- New `NumericSliderScaleTests` covers exact and midpoint values across `[-Double.MAX_VALUE,+Double.MAX_VALUE]`, zero-width numeric ranges, ordinary 0.25–0.75 ranges, outside-track clamping and illegal NaN/infinity constructors and fractions.
- Actual `ModuleRegistry` + `UiActions` tests verify a huge finite range is draggable in both directions, its fill roundtrips, and a constant range does not create unnecessary profile writes.
- **140,000 seeded extreme-range scenarios**, checking finite and bounded mapping/inverse/reconstruction and exact endpoints. An additional **100,000 slider steps** check that moving the pointer right never decreases a numeric value.
- Forge source wiring verifies the shared policy is used in the renderer and mutation gateway, the old overflowing expressions are removed, the constructor blocks non-finite defaults, and `NumericSliderScale.class` is present in the reobfuscated JAR.
- Merge only after exact PR-head Core Java17/21, Linux/Windows JDK8 Forge/JAR/provenance, plus Windows acceptance and rollback rehearsals all pass. Verify exact post-merge `main` SHA separately.

## Limitations

Tests are headless and use deterministic floating-point arithmetic. The change does not establish real Minecraft 1.8.9 startup, an actual ClickGUI screenshot or pointer integration in Minecraft, measured FPS or 60-minute stability. G1/G2/G4/G5 runtime acceptance remains open.
