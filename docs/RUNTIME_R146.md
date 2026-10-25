# GhostC R3.26 — verified, bounded diagnostics backup rotation

The opt-in \`RuntimeDiagnostics.saveReport\` wrote its new report
atomically but previously created \`diagnostics-last.properties.bak\` by
unconditionally copying the current primary. A damaged, oversized,
manually edited, or **symbolic-link primary** could overwrite the last
good diagnostics backup with arbitrary local data. Files.copy's normal
link-following semantics also risked copying unrelated contents.

R3.26 changes backup rotation only, preserving the established
diagnostic report fields and normal save behavior:

- Stage at most **64 KiB** from a non-symlink primary through
  \`NOFOLLOW_LINKS\`, including after initial path validation.
- Parse the exact bounded staged copy using the existing strict
  configuration parser (rejecting duplicate or aliased keys).
- Admit only schema-1 counter-only reports with every current counter,
  recognized performance/snapshot aggregate keys, and nonnegative
  decimal values representable as a Java \`long\`.
- Atomically replace the backup only after the staged snapshot passes
  validation. Keep any existing backup byte-for-byte on failure.
- Publish the new anonymous primary normally and clean staging files,
  even if an older primary was untrusted or excessive in size.

This is local **format and content validation**, not cryptographic
authenticity; diagnostic reports intentionally remain unsealed and
are not used as a module/UI recovery authority.

\`DiagnosticsBackupIntegrityTests\` covers 80 malformed-primary
scenarios (bad schema, extra private fields, invalid numbers,
duplicate keys, wrong scope, truncated data and oversized files),
symlink-to-unrelated-file protection, valid report rotation,
backup byte equality and staging cleanup.

CI: Core Java 17/21 and Forge 1.8.9 Linux/Windows JDK8, with
downloaded Windows artifact acceptance. The standard native
Minecraft 1.8.9 GUI, input, rendering and FPS acceptance remains
unverified.
