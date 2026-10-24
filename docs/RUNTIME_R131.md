# GhostC R3.11 — no-follow ordinary config reads

R3.10 rejected symbolic link aliases when restoring from a backup, but
normal profile and UI loads still used a generic parser that followed
symlinks. A valid sealed config stored outside the client directory could
be imported into live state without crossing any recovery checks. File
SHA256 protects content integrity, not file-system origin.

ConfigIntegrity.read now requires a regular non-symlink file and opens
the source with NOFOLLOW_LINKS. This also rejects a symlink introduced
between the metadata check and opening the source stream. Existing
1 MiB actual-byte cap, SHA256, duplicate-key checks, legacy schema
acceptance and typed staging remain unchanged.

BackupRotationTests.symlinkDirectLoadFences creates valid sealed profile
and UI files in an external temporary directory. It tests direct parsing,
ordinary profile/UI loads and recovering loads through linked primaries.
None may change live keybinds/HUD anchors, the external file, link path
or corruption-evidence inventory. Ordinary file recovery remains tested.

This hardens source admission but assumes the config parent directory
is trusted; it is not a complete filesystem race-proof transaction.
Require exact-head Core Java17/21 and Forge Linux/Windows JDK8,
matching tested/merged source tree and successful postmerge workflows.
No native Minecraft gameplay/GUI performance testing is claimed.
