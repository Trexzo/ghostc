# GhostC R2.71 — keyboard navigation for typed Choice settings

## User experience

With a Choice setting focused in the F7 ClickGUI (Tab/Shift+Tab):
- **Left / Right:** choose the previous / next option, wrapping around the ordered choices.
- **Home / End:** jump to the first / last option.
- **Enter / Space:** preserve the existing forward-cycle behavior.
- **R:** preserve the existing typed default reset.

Numeric settings retain their R2.70 5%-range arrows, Shift+arrows 1%-range adjustments and Home/End endpoints. Search, key capture, module selection and HUD editor retain higher input priority.

## Implementation and verification

- Extend the existing owner-thread `UiActions` with directional Choice stepping and endpoint selection, while preserving the public forward-only cycle method used by the mouse.
- Reuse the Choice's validated ordered options and the existing profile persistence gateway. Changes persist once; already-selected endpoints and one-option choices do not persist.
- Java8 regression suite covers 75,000 deterministic direction/wrap cases, validated inputs, exact save counts, single-option no-ops and keyboard focus coexistence.
- Forge source-routing test verifies correct action routing and search/key-capture input precedence.
- Required release gates: exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 builds, JAR verification and fake-profile install/rollback; tested/merged tree equality; postmerge CI.

Native Minecraft 1.8.9 UI behavior, graphics, FPS, and mod compatibility remain unverified by CI.
