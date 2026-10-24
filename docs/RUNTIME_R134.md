# GhostC R3.14 — immutable sparse-profile keybind defaults

R3.13 made absent setting values deterministic, but an absent module key
still silently loaded as zero (unbound), even for a module originally
registered with a nonzero default key. A partially saved legacy profile
could therefore clear the default F8 status-HUD binding.

ModuleRegistry.Entry now stores its immutable validated registration
keybind alongside the mutable live keybind. ProfileStore uses the
registration value **only if the module key field is absent**. Explicit
key=0 and custom key values remain fully authoritative. Existing
reserved-key and duplicate-key preflight checks still apply before any
live state changes.

SettingDefaultResetTests exercises 1,024 deterministic sparse sealed and
legacy profiles, changed live keys, omitted keys, explicit unbound values,
custom keys, duplicate binding rejection, F7 reservation, and sparse
sealed-backup recovery.

This changes no fully populated profiles and no hot-path key lookup.
Require exact-head Core Java 17/21, Forge Linux/Windows JDK8, JAR and
simulated rollback verification; game UX, FPS and rendering remain
unverified in native Minecraft 1.8.9.
