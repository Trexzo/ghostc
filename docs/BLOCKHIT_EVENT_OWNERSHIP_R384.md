# GhostC R3.84 — Block Hit event ownership and native retry limits

Parent: CI-verified combat draft PR #221 (R3.83) at `5dfd99dceced0c711855d37b143ba45b1f146489`. Stacked draft; no merge, release, Minecraft launch, installation or changes to the separate visuals track.

## Source-audited issues

`BlockHitPolicy.due` is the owner-thread event source for `post_attack` (only previously **confirmed** extra Clicker output) and `on_damage` (genuine rising local `hurtTime`). Native Forge uses `preview -> KeyBinding.onTick -> committed`, with `cancelPrepared` restoring one speculative USE attempt. The policy already limits a pending event to three subsequent ticks and checks the shared interval.

Two source-level gaps remained:

1. Both interval and event modes allowed the **same** generation/tick to run `due` again. After a staged native USE was rejected and `cancelPrepared` restored its event, a duplicate END callback could stage a second USE request on that same tick. Existing `NativePulseSettlement` independently protects actual native dispatch, but proposals must also be idempotent at their source.
2. With a pending `post_attack` event, another successful Clicker pulse overwrote `pendingSince`, restarting the three-tick expiry window. In `on_damage`, another hurt-time increase did likewise. Repeated overlapping events or denied output could stretch the original event lifetime, effectively changing the intended bounded retry behavior. There is only one pending event slot; replacing it with newer provenance silently discarded the older event.

## Implementation

- Both original interval `due` and event `due` now return `false` on a repeated client tick; backward ticks still throw and valid new generations reset normally. A duplicate `preview` revokes an earlier speculative stage through existing `cancelPrepared`, but cannot produce an additional USE attempt. The first observation for a tick owns the hurt sample, so later callbacks cannot fabricate a second rising edge.
- For `post_attack` and `on_damage`, the **first pending event** retains `pendingSince` until one native USE commits, real input/feature/world lifecycle invalidates the event, or `tick-pendingSince>3` expires it. New attack/hurt edges seen during a pending event do not refresh the deadline or generate an extra queued event. They still update the current hurt-time baseline as appropriate.
- After expiry, a genuinely new event may acquire the empty slot on its own tick; that cannot emit USE until a later tick. There is no new backlog, timer, hold or native output. A vetoed native USE continues to use the existing `beforePulse/beforePending` rollback and may retry on another **client tick** inside its original event window.
- Interval cadence, user timing, default enabled settings, Hit Select / AutoClicker / TriggerBot ATTACK arbitration, R3.83 uncertain ATTACK→USE interlock, original `KeyBinding.onTick` transaction and native player/world guards are unchanged.

## Regression evidence

`BlockHitEventOwnershipTests` exercises overlapping ATTACK/damage edges, duplicate callbacks both before and after native commit, duplicate cancellation of a prepared attempt, denied USE retries limited to the original three ticks, expiry, separate fresh events, interval-mode same-tick prevention, mode/epoch switch, held-key release and owner-thread protection. It runs 90,000 seeded client frames across both event modes against an independent event-first-owner, interval and expiration oracle, including changed synthetic input/hurt values in duplicate callbacks.

Mandatory original Minecraft 1.8.9 Java8 `NativeBlockHitEventOwnershipTests` exercises actual `KeyBinding` USE pulse linkage, a headless staged/canceled/committed post-attack event and hurt-time event retry with duplicate veto, verifying one key pulse and no game or server launch. Python source wiring enforces one-tick gates, event lifecycle, pending restore, existing final native guards and test registration.

Require exact-head Core Java17/21, Forge Linux/Windows Java8 compilation/reobfuscation and Windows downloaded-artifact acceptance. Headless CI is **not** real Minecraft gameplay, server input acceptance or PvP hit evidence.
