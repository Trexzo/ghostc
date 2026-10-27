# GhostC R3.47 — Passive original-game acceptance evidence

**Stacked on R3.46 native tandem acceptance, Draft PR #179**, base commit `3cb430e3d90b5aaee18a06f66cb1da31d7fd5285`. The other chat owns visual PR #164. No parent branch or `main` is modified.

## Why this matters

R3.46 executed **real Minecraft 1.8.9 container and packet classes under Java8**, but never launched Minecraft. R3.47 adds **actual native tick and Netty observation** so a later original Minecraft run can record what the integrated client *really reached*, with clear boundaries between evidence and untested claims.

It is **passive**. It adds no gameplay automation, packet modification (beyond existing explicit Velocity packet mode), target selection, inventory click, chat command, or telemetry endpoint. **Everything is off until the existing F7 Runtime Diagnostics feature is enabled.**

## Runtime observations

`ForgeEventAdapter` reads the already-authoritative R3.43 `ModuleInteractionStateMachine.Frame` **after** native tick controllers, plus the R3.47 `ForgeVelocityPacketBridge.health()` snapshot. It records each first-seen category **once in the current client process**, never every tick:

| Fixed anonymous counter | What a nonzero value can establish |
|---|---|
| `count.accept_gameplay` | A foreground, live GAME phase was observed |
| `count.accept_concurrent` | Combat, Bridge Assist and Velocity all received action permission on the same client tick |
| `count.accept_armor` | AutoArmor was the actual player-inventory action owner |
| `count.accept_sorter` | Inventory Manager received player-inventory ownership when AutoArmor was disabled |
| `count.accept_chest` | Chest Stealer was the sole chest-menu owner |
| `count.accept_gui_block` | Other GUI, or a player inventory GUI that denied combat, was observed |
| `count.accept_focus_block` | A live enabled gameplay module was denied because the window lost focus |
| `count.accept_wtap_hold` | WTap held sprint ownership and AutoSprint received no grant |
| `count.accept_hook_attached` | The actual Netty event loop installed the Velocity inbound handler on the active channel, while Velocity had a game grant |
| `count.accept_s12_local` | An inbound **S12 Entity Velocity** packet matched the local player identity while packet mode was active with non-100% scaling |
| `count.accept_s27_explosion` | An inbound **S27 Explosion** packet was observed with non-100% packet-mode scaling |

This is an *observed coverage checklist*, not a certification checklist. A full `11/11` is **still not** proof of server ACKs, player damage, human control compatibility, or anticheat/server acceptance. Existing R3.45 per-module ATTEMPT/LOCAL_CHANGE/TIMEOUT/DISPATCHED counters provide separate action observations.

## Exact native Netty status

The R3.42 Netty bridge now exposes fixed hook states:
- `DISABLED`: the Velocity packet listener has not been requested or has been reset
- `MAPPING_MISSING`: a required Minecraft 1.8.9 private field alias/type is unavailable
- `ATTACH_PENDING`: an enabled owner tick has requested installation, but the Netty event loop has not yet confirmed it
- `ATTACHED`: the handler has been installed or recognized as this bridge's handler in the real Netty pipeline
- `UNAVAILABLE`: unrecognized status is mapped to a fixed vocabulary before the readout

When Diagnostics is opted in, Netty maintains **only bounded atomic aggregate counts** of local S12, foreign-entity S12, S27 and exceptions. No entity IDs, packet bodies, positions, channel names, server address, credentials or player names are stored. No Netty thread ever calls `HostRuntime` or accesses a Minecraft player/world object.

The existing F7 diagnostics overlay gains a fourth, fixed-format bottom-left line like `Acceptance evidence 4/11 | packet hook ATTACHED`. No changes are made to other HUD modules or appearance PR #164.

## Files and acquisition

With Runtime Diagnostics enabled, GhostC already writes `config/ghostc/diagnostics-last.properties` (with a validated `.bak`) on a permitted export, disconnect or shutdown checkpoint. R3.47 adds only the `count.accept_*` **nonnegative numeric values** to that existing anonymous schema-1 report. A valid older backup is still recognized when these new keys are absent; unknown fields, malformed values, oversize and symlinks remain rejected.

**No local manual Windows test required merely to compile this patch.** A single future authorized original Minecraft 1.8.9 session, with diagnostics opted in and the desired module combinations actually enabled, can produce the missing in-game evidence; it is not possible to manufacture evidence from CI.

## Validation levels

1. **Core Java17/21**: `LiveAcceptanceCoverageTests` runs 45,000 deterministic mixed-scene/sprint/packet-status states and confirms first-observation semantics, bounded anonymous readout, owner-thread isolation, invalid-input rejection and old diagnostics backup migration.
2. **Native Forge Linux/Windows JDK8**: existing mandatory `velocityNativeProbe` now verifies real Minecraft packet classes, actual event-loop hook state, opt-in local-vs-foreign S12 and S27 class counts, opt-out and detached hook behavior; existing `nativeTandemProbe` continues 193 real Minecraft chest/packet tests and F6/world handoff contract.
3. **Original launched Minecraft gameplay**: **not yet run**. Must still confirm real client tick/render event order, attached hook states across actual connections, true player/container state, physical input precedence, GUI transitions, disconnected/reconnects and server-authoritative results. No success claim until such evidence exists.

The first two levels cannot establish the third. Keep this PR Draft, do not merge/force-push parent work, and coordinate the independent UI branch separately.
