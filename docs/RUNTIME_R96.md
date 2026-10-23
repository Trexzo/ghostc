# GhostC R2.76 — bounded footer errors and warnings

## Reproduced defect
The ClickGUI's warning line directly drew either `message` or
`host.uiWarning()` at `x+sidebar+11` with no clipping or width limit.
Long validation/fault warnings could extend past the right edge of
compact or wide panels and collide with neighboring UI.

## Fix
The new host-independent `GuiFooterText` centralizes local validation
error > host warning > contextual hint/pagination priority. Both warnings
and focused keyboard/pagination hints share the same width budget
(`panel width - sidebar - 32px`, saturated at zero and validated for
extreme dimensions), and pass through the existing Unicode-aware,
font-measured `HudTextFit` fitting path before native drawing.
We preserve the footer's reserved strip, colors and no-extra-write
behavior. Long errors are visually truncated, not silently discarded
from diagnostics or changed at the source.

## Verification
Java8 `GuiFooterTextTests` verifies priority, non-null validation,
long Unicode strings and 85,000 seeded width/priority/layout scenarios.
Forge source-wiring checks verify warnings are never directly painted
without pixel fitting and that contextual hints respect warning ownership.
Exact-head Core Java17/21 and Forge Linux/Windows JDK8 builds, JAR
verification and simulated installation/rollback are mandatory.
Matching tested/merged trees and postmerge CI are required for release.
Native game drawing and visual legibility require 1.8.9 acceptance.
