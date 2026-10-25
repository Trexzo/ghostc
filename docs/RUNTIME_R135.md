# GhostC R3.15 — sparse profile module activation baselines

R3.13 fixed missing setting values and R3.14 fixed missing keybinds, but
module.enabled still defaulted unconditionally to false. A sparse legacy
default profile could disable the status HUD, which HostRuntime explicitly
enables before constructing ProfileStore. Repeated loads also discarded
the intended startup policy rather than applying a stable baseline.

ProfileStore captures enabled/disabled state per registered module once,
at construction time on its owner thread. Missing enabled fields use this
immutable initial policy, not the state of a previously loaded profile.
Explicit true/false values remain authoritative, and malformed values
continue to fail during typed preflight without mutation.

SettingDefaultResetTests covers 512 mixed sealed/legacy sparse profiles,
both initially enabled and initially disabled modules, explicit overrides,
malformed boolean rejection, immutable keys, validated sparse backup,
and an actual HostRuntime initialization with absent or explicit false
status-HUD.enabled.

The change does not alter complete profiles or default values. CI must
pass on Core Java 17/21 and Forge Linux/Windows JDK8, with exact-tree
merge and post-merge checks. Native Minecraft GUI, render performance
and gameplay acceptance remain unverified.
