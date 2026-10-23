# GhostC R2.81 — deterministic legacy HUD widget fallback

## Source-level failure

`UiSettingsStore.stage` supports schema-1 UI preferences that predate
independently movable Keystrokes, CPS and Target HUD widgets. When a widget
or one coordinate was missing, loading the file used the current mutable
`state.widgets().x/y(widget)` as the default. Reading identical bytes
after earlier HUD editing or startup recovery could silently retain
different positions. A sparse, integrity-sealed backup could consequently
restore the wrong on-screen anchors.

## Fix

Read each missing axis from its stable `HudPositions.Widget.defaultX/defaultY`.
An explicitly persisted axis still wins. The original Status HUD
`hud.x/hud.y`, layout and theme are unchanged, and both legacy unsealed
and modern sealed schema-1 files remain readable.

The same deterministic typed staging is used for load, backup admission,
and recovery; previous live widget positions no longer influence the
interpretation of sparse files.

## Verification

Pure Java8 `ConfigRecoveryTests.legacyWidgetDefaults` performs 12,000
seeded mutation/reload rounds against unchanged sparse schema-1 bytes,
checking every widget and both axes plus existing layout, theme and
Status positions. Additional checks cover unsealed and integrity-sealed
sparse files, successful backup rotation of a sealed sparse primary,
digest-corruption recovery and preservation of `.corrupt` evidence.
Forge source-wiring tests pin immutable default fallbacks and forbid
live-state defaults.

Require exact PR-head Core Java17/21 and Forge Linux/Windows JDK8
builds, JAR verification and simulated installation/rollback,
identical tested and merged trees, and postmerge CI success before
certification. Native Minecraft 1.8.9 HUD visuals, scaling, FPS and
long-session behavior remain unverified.
