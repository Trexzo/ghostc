# GhostC R3.19 — sparse legacy UI defaults and backup recovery

Prior revisions protected sparse module settings, bindings, enabled flags
and optional movable HUD widget positions. However, the UI preferences
parser still required four top-level fields in every schema-1 file:
\`layout\`, \`theme\`, \`hud.x\` and \`hud.y\`. A valid legacy UI file
missing just one of those fields was rejected during startup. This could
prevent otherwise valid, older preferences from being loaded.

R3.19 changes **only the missing-field policy**. A field absent from a
schema-1 UI preferences file falls back to the immutable initial
\`UiState\` defaults (WIDE, INDIGO, X=0.025, Y=0.055); this is not
derived from a previously edited live UI. Explicit values continue to
take precedence, including values set to 0 or 1. Invalid explicit
names, NaN/Infinity or out-of-range coordinates still cause typed
preflight failure **before any UI mutation**.

\`UiTests.sparsePreferences()\` adds 1,024 combinations of omitted/
explicit fields in verified and unsealed schema-1 files; another 1,024
malformed cases exercise atomic rejection. Further cases verify a
sealed sparse backup with a typed-invalid primary, preservation of
the original corrupt bytes, immutable movable-widget defaults and
actual \`HostRuntime\` initialization using a minimal sealed UI file.

Existing full UI preference files are unchanged. The Forge package
version increments to 3.0.19. Core Java 17/21 and Forge Linux/Windows
JDK8 plus the independent downloaded-archive job are required.
Actual Minecraft GUI, rendering, input and gameplay performance
remain **unverified in-game**; these tests are offline only.
