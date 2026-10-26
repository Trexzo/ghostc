# GhostC R4 visual track — optional live Module List HUD

An independent visual feature developed alongside the separate combat-timing/Aim Assist work.

## Available in Forge 1.8.9
- **Module List** in ClickGUI, disabled by default for old-profile compatibility.
- Only *enabled* modules appear; disabled/faulted modules and Module List itself are excluded.
- Optional inclusion of RENDER category modules (defaults off so HUD cards don't flood the list).
- Four screen corners, width/alphabetical sort, a bounded 1–24 row limit.
- Background and accent toggles, theme-aware accent color (Graphite/Indigo).
- Font-measured text fitting and viewport clipping, including tiny GUI scales.
- Uses existing profile persistence, settings UI, render guard and per-feature fault isolation.
- No injection, packet interception, input rewriting or combat changes.

## Verification
The pure Java ModuleListLayoutTests cover module lifecycle visibility, render filtering,
sort, row cap, all corner anchors, truncated/malicious labels and 1,600 seeded viewport
variants. Core CI runs it on Java 17/21 targeting Java 8; Forge CI compiles Java 8 and
checks bundled classes. CI cannot certify native OpenGL appearance, real in-game FPS,
or conflicts with other installed Minecraft mods. Those require native acceptance.

## Coordination
Do not automatically merge this branch while the concurrent R4 combat PR edits HostRuntime.
The only shared core file this change needs is HostRuntime, for independent settings and
module registration; preserve both feature sets when rebasing.
