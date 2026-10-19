# GhostC R1.6 — bounded ClickGUI controls and private fault reporting

## Bugs corrected

At very small Minecraft GUI scales, the fixed top control positions and six category labels could overlap or extend beyond the ClickGUI panel. Mouse category selection was also based on a tall rectangle rather than the exact visible row height, allowing clicks between two rows to select a category. The new pure Java GuiChrome model makes all four header controls and six sidebar category selections share the same geometry as the visible UI.

For scaled viewports too small to keep controls distinct (panel width below 445 or height below 250 logical pixels), the GUI displays an informative resize notice and does not permit ambiguous module, category, setting, theme, layout, or drag changes. ESC/F7 can still close the screen. At ordinary supported sizes, controls remain fully interactive.

An independent audit also identified a privacy inconsistency. ModuleRegistry previously stored the entire Throwable message in lastFault, which might include private account, server or chat data. The core now stores only a bounded exception class name; new FaultPrivacyTests check enable, dispatch, disable, reset, and randomized failure handling.

## Automated regression scope

- GuiChromeTests: thousands of seeded and grid-screen layouts; confirms distinct header rectangles, exact sidebar row boundaries, non-clickable separator gaps and safe undersized fallbacks.
- FaultPrivacyTests: lifecycle and seeded fault events with intentionally sensitive exception messages; verifies the original text is never retained in the module fault state.
- New source-contract checks verify GhostClickGui really invokes the GuiChrome geometry helper and that the module registry no longer records exception messages.
- Existing Java 17/21 tests, Linux/Windows Forge 1.8.9 JDK8 builds, JAR integrity verification and safe fake-profile Prepare/Collect/Rollback must remain green.
- Versioned Forge metadata and HUD version constant: 1.6.0. All code, CI, documentation and artifacts remain in GitHub.

## Acceptance boundary

The project is still not verified in a running Minecraft 1.8.9 window. The small-viewport fallback, header mouse inputs, GUI scaling and actual native render performance need one consolidated local game test. Use the SHA-256-gated installation and rollback process in docs/RUNTIME_R10.md, and add observed, redacted evidence to Issues #2–6 rather than treating CI success as proof of gameplay quality.
