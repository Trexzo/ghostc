# GhostC R1.5 — render identity and ClickGUI hitbox correctness

## Motivation

R1.4 changed world and player identity handling at the END client tick. Render or mouse events may still arrive after Minecraft swaps its world reference and before the next END tick. Earlier handlers depended only on the previous `worldPresent` state and could show old session HUD content during that narrow transition.

The ClickGUI also used broad list rectangles to interpret clicks. These could select an entry in the separator between two visible module rows or an area not drawn at a small resolution.

## Changes

- `WorldIdentityTracker.matches(world,player)` checks the exact admitted object pair on the owner thread without changing session state.
- The three Forge render and mouse callbacks check the live pair before calling 2D HUD, 3D ESP or mouse-feature processing. END tick remains the sole owner of session transitions.
- `GuiHitZones` is a pure Java model that selects only visible module and setting rows, rejects separators and off-panel clicks, and identifies the appropriate module or settings scroll area.
- `GhostClickGui` refreshes layout before mouse interactions. Module selection, setting mutations and scrolling now use the same geometry as drawn UI elements. Search text, focus and HUD dragging remain unchanged.
- `ViewportHitTests` covers identity matching and seeded small/large viewport pointer coordinates. Existing headless regression suites are preserved.
- New direct Forge wiring checks ensure the real adapter uses the admission guard and actual ClickGUI uses shared hit zones. Repository inventory checks include the R1.5 tests and guide.

## Verification boundaries

Java 17/21 headless regression CI and independent Windows/Linux Forge 1.8.9 JDK8 builds, JAR integrity and fake-profile install/rollback remain the automated gates. Version is 1.5.0.

The builds do not launch a Minecraft game window. Real visual quality, GUI scaling, input responsiveness and frame stability still require one consolidated local runtime verification session with appropriate redaction of any uploaded logs. The detailed installation and rollback workflow is described in `docs/RUNTIME_R10.md`.
