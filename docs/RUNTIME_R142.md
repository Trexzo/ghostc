# GhostC R3.22 — close-time save reentry fence

GhostC deliberately snapshots the live module profile **before**
calling \`onDisable\` during shutdown, so a restart preserves what
the owner enabled instead of restoring all modules as disabled.

Previously, the host's \`closing\` flag blocked recursive \`close()\`
and its ingress fence blocked late worker events, but the public
\`persist()\` and \`persistUi()\` APIs remained callable from a custom
module's \`onDisable\`. That reentrant save could overwrite the
correct pre-shutdown checkpoint with partially disabled module state,
or write UI anchors changed by an arbitrary shutdown callback.

R3.22 separates the two trusted final-save implementations
(\`saveProfileFile\` and \`saveUiFile\`) from their public entrypoints.
Public \`persist()\` and \`persistUi()\` now return without writing while
\`closing\` is active. \`close()\` invokes the private save methods **once,
before any module shutdown callback**, then safely seals the session
as before. Normal pre-close GUI, keybind and HUD edits still persist
normally. Existing R3.20 failed-startup-file preservation fences
remain fully operative in both paths.

## Regression evidence

\`HostRuntimeTests.testClosePersistenceReentryFence\` executes 96
independent temporary-host shutdowns with a maliciously reentrant
\`onDisable\` implementation that modifies live UI coordinates and
calls public persistence repeatedly after some modules are already
disabled. Tests ensure:

- The original profile and UI primary bytes match the pre-callback
  trusted snapshot (not merely semantically equivalent values).
- Both \`.bak\` files remain byte-for-byte identical.
- The module lifecycle runs exactly once without fault.
- The stored status HUD and extra module remain enabled as they were
  immediately before closing.
- Restart loads the pre-shutdown HUD and UI state.

Core Java 17/21 and Forge Linux/Windows JDK8, plus the downloaded
Windows archive acceptance checks, are the offline CI gates.
Minecraft 1.8.9 rendering, GUI interaction and FPS remain
**not native-tested** by these regressions.
