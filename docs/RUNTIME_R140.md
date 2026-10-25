# GhostC R3.20 — failed startup config preservation fence

If a schema-1 module profile or UI settings file is malformed and no
typed-valid, **sealed** recovery backup exists, GhostC starts in a usable
fallback state and reports a startup load warning. Previously, automatic
saves from GUI actions, keybind toggles or \`HostRuntime.close()\` could
silently replace the unreadable primary with these fallback settings,
destroying the evidence needed for manual repair.

R3.20 introduces two **independent per-session save fences** in
\`HostRuntime\`: profile persistence is refused after a failed startup
profile load, and UI persistence is refused after a failed startup UI load.
The other file may still save normally. This applies equally to explicit
persist calls and close-time persistence; in-memory module/UI behavior
remains usable. Warnings explicitly say \`save blocked\`, and are not
cleared by failed persistence attempts.

A **successfully validated backup recovery** does not engage the fence:
the restored, sealed primary may be updated normally and its original
corrupt bytes remain in the corresponding \`.corrupt\` evidence file.

## Recovery action if a warning says save blocked

1. Preserve the unreadable \`config/ghostc/default.properties\` or
   \`config/ghostc/ui.properties\`, along with any \`.bak\` copies.
2. Exit the client. Inspect/fix or **manually move aside** the damaged
   file before restarting. A clean startup then permits new saves.
3. Do not expect module/UI changes made during a session with that
   file's save fence active to survive restart. The **other** healthy
   configuration remains independently persistent.

Never interpret a load-warning session as a successfully restored profile.
No automatic file deletion or resetting of the unreadable primary occurs.

The new \`StartupPersistenceFenceTests\` covers 96 failed-profile
sessions, 96 failed-UI sessions, dual failed files and backups, and
successful verified-backup recovery followed by real persisted changes.

CI tests the headless runtime on Java 17/21 and the Forge package on
Linux/Windows JDK8, including the independently downloaded Windows ZIP.
Actual Minecraft 1.8.9 GUI, rendering, input and FPS are not
verified by these offline tests.
