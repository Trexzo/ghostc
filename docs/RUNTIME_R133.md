# GhostC R3.13 — deterministic sparse profile defaults

Sparse/legacy profile records can omit recently introduced settings. The
profile staging code previously skipped those fields, allowing an unrelated
profile's live setting values to leak into the next profile load. That meant
the same valid sparse file could produce different client settings depending
on what was loaded before it.

ProfileStore now stages an explicit constructor default for each absent
setting, using Setting.serializeDefault(), while preserving every supplied
typed-valid value. Staging remains non-mutating; profile application still
uses the existing rollback path and R3.12's disk-recovery/runtime-fault fence.

SettingDefaultResetTests now exercises 512 sparse sealed and unsealed profiles,
varying which Boolean, choice and numeric values are specified; it checks the
keybind and adds verified sparse-backup recovery. No defaults of existing
features are changed, and fully populated profiles retain their values.

Verification: Core Java 17/21, Forge Linux/Windows JDK8, JAR provenance
and simulated install/rollback on the same tested commit. Gameplay, GUI,
FPS and native Minecraft 1.8.9 acceptance remain unverified.
