# GhostC R3.65 — combat-wide native frame acceptance and in-game evidence checklist

**Stacked base:** exact CI-green R3.64 draft PR #198 at `0cbf7dba9d46e8f860ed6f2dcb12b59fd299bef5`. GitHub-only source update, no local installation, no launched Minecraft and no changes to visual branches.

## What was missing

R3.48–R3.64 checked individual combat policies, projectile hotbar state, local hook/inventory observations, and vanilla native key-binding linkage. They **did not** verify, on the same game END frame, that a projectile-owned hotbar / manual override / two-owner conflict never overlapped an optional AutoClicker, TriggerBot, BlockHit native key pulse or Aim Assist camera write. A frame grant and `AttackEntityEvent` are different from key pulses; none is a server ACK or damage confirmation.

## New runtime read-only acceptance

`CombatFrameAcceptance` is a pure, owner-thread, session/tick-fenced classification with fixed enum outcomes. `ForgeEventAdapter`, ONLY when **Runtime Diagnostics is explicitly enabled**, samples genuine `HostRuntime.nativeActionCount` for `CLICKER`, `TRIGGER`, `BLOCKHIT` / `DISPATCHED` and the existing `AIM_APPLIED` counter just **before** the projectile and combat controllers execute. It rechecks those counters immediately **after** combat output and classifies:

- `PROJECTILE_EXCLUSIVE`: AutoRod or AutoSnowball SELECT/USE/RESTORE owns the hotbar; no new optional attack, use or camera writes occurred.
- `MANUAL_OVERRIDE`: user wheel/number-key/right-click input preempts optional controllers with no new native combat writes.
- `OWNER_CONFLICT`: both projectile owners or arbiter conflict caused a fail-closed block with no optional native write.
- `POST_PROJECTILE_REARM`: first combat frame after an exclusive projectile frame was released; **no** stale synthetic CLICKER/TRIGGER attack was dispatched. The actual `PhysicalInputGate` still requires a fresh physical release and re-press; recording does not modify that.
- `UNEXPECTED_NATIVE_WRITE`: an opt-in diagnostic anomaly when optional combat runs/writes during a projectile/manual/conflict guard, any optional native write occurs without the combat controller executing, or a synthetic attack pulse appears in the first post-projectile frame.
- `COMBAT_RAN`, `COMBAT_SKIPPED`, `INACTIVE`: no inferred native action beyond what was sampled.

These are per-frame **anonymous aggregate counters**, not identities, targets, per-key histories, telemetry uploads, authentication of server action or proof a hit landed. Observations never authorize, block, delay, retry or spawn any native action. The **independent** native Forge `AttackEntityEvent` counter remains separate: a callback may be caused by vanilla manual input and may not be correlated to a Clicker/Trigger pulse without a real launched-client trace. Existing Aim Assist, HitSelect, WTap, JumpReset, BlockHit, projectile and arbitration behaviors remain unchanged.

Counts are visible in the existing opt-in export `diagnostics-last.properties` under `count.combat_frame_*`, and the older report backup whitelist now tolerates R3.62–R3.64 `PROJECTILE_*` additions. No report schema bump or additional persistence path is required.

## Mandatory offline and native tests

- `CombatFrameAcceptanceTests`: 80,000 deterministic mutually exclusive combat-frame scenarios, including two projectile owners, manual overrides, normal AutoClicker/Trigger/Block native writes, camera rotation, missed/duplicate/retrograde ticks, world switch, post-RESTORE release-first handoff and violations; integrates actual `AutoRodPolicy`, `AutoSnowballPolicy`, `PhysicalInputGate` and `FixedClickScheduler`.
- `NativeCombatFrameAcceptanceTests` (Forge 1.8.9 Java 8 `check`): actual original Minecraft `KeyBinding.onTick`, `isPressed`, physical held/release state and Forge `AttackEntityEvent.target/entityPlayer` API linkage, with separate native key output versus acceptance results. No game launch or real attack callback fired here.
- Mandatory `tools/test_forge_wiring.py` verifies opt-in capture *before* any projectile owner runs, same-frame read *after* the combat dispatch, absence of secret identity fields, exact counter mapping, observer isolation and native probe in `check`.
- Exact-head Core Java17/21 + Forge 1.8.9 Linux/Windows Java8 compile/reobfuscation/native probes and Windows downloaded artifact preparation/rollback rehearsal must pass. These are **not multiplayer acceptance**.

## Explicit original Minecraft 1.8.9 gameplay acceptance — not yet executed

For an authorized original Minecraft 1.8.9 runtime, install exactly the reviewed candidate JAR, enable Runtime Diagnostics in the F7 ClickGUI, and perform independent controlled trials:

1. **Baseline**: combat features all OFF; verify no `count.action_clicker_dispatched`, `count.action_trigger_dispatched` or `count.action_blockhit_dispatched` increments. `AttackEntityEvent` from actual manual clicks is tracked independently, never called an accepted hit.
2. **AutoClicker**: physical attack release → press on/without target; correct local key pulses only when eligible; no synthetic pulse without release following GUI/focus/world change.
3. **TriggerBot + Aim Assist**: stationary fresh target under native crosshair; TriggerBot dwell and cooldown before local pulse, Aim Assist yields camera when TriggerBot acquires; manual camera movement precedence when configured. Compare local key vs callback counts **without inferring which caused which**.
4. **HitSelect + AutoClicker/TriggerBot**: rejected preview must never commit an attack/cooldown or let the losing producer emit its own key pulse; same-tick arbitration makes at most one optional attack pulse.
5. **BlockHit + WTap**: a real attack event may begin a WTap lifecycle; BlockHit uses the vanilla key interface only if its native guard passes. WTap sprint reservation must suppress competing AutoSprint/JumpReset/rod actions without replay.
6. **AutoRod/AutoSnowball handoff**: SELECT → USE → RESTORE with actual manual number-key/wheel/right-click override, no `COMBAT_FRAME_UNEXPECTED_NATIVE_WRITE`; on the first post-projectile combat frame, no synthetic attack until a fresh physical release and re-press. Verify expected hooks/decrement and ambiguous inventory corrections using R3.63/R3.64 tests.
7. **Adversarial transitions**: GUI, focus loss, world/player swap, server disconnect, inventory replacement, dual projectile ownership and disabled modules; no wrong-world or stale native write. A proposed remote effect without a genuine independent gameplay/server observation remains **unverified**.

Record candidate Git SHA, mod JAR SHA-256, Java/Forge/Minecraft versions, redacted scenario pass/fail and the **existing local anonymous** diagnostics counters. The report is written only with explicit Diagnostics opt-in on normal export/disconnect/close; do not copy game chat, addresses, UUIDs, coordinates, player names or packet contents.

This patch only makes genuine in-game verification **measurable**. Neither GitHub CI nor the Java8 headless native probes can claim live Minecraft was launched or combat worked on a particular server.
