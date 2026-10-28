# GhostC R3.54 — independent native combat acceptance receipts

**Stack:** R3.54 is an isolated GitHub draft stacked on fully CI-green R3.53 PR #187, starting from exact commit `372a97322a4e903f3f16862c089951359b1e438c`. No parent combat branch, `main`, or separately owned visual PRs #164/#181 are modified.

## Why the previous evidence was insufficient

The R3.53 Java8 probe executes genuine Minecraft 1.8.9 `KeyBinding` API against the real shared combat state-machine policies. This proves key queue mechanics in a headless test JVM. It does NOT establish that the original game received an attack, sent a packet, or that any actual Forge `AttackEntityEvent` occurred. Previous optional runtime diagnostics counted AutoClicker local key pulses, but TriggerBot used the same `EXTRA_ATTACK` aggregate without its own classified receipt. It was impossible to separate TriggerBot's local native dispatch from AutoClicker or compare either with actual native callbacks in the later operator Collect evidence.

## Actual runtime change

New **fixed, anonymous, opt-in** evidence in the existing `hud.diagnostics` module:

- `count.action_trigger_dispatched`: `TriggerBotPolicy.dispatched` and `KeyBinding.onTick` completed locally under the TriggerBot owner after the selected ATTACK request passed Hit Select and final native player/crosshair guards. This means **queued a local key input**, NOT a hit.
- `count.action_trigger_rejected`: a selected TriggerBot owner was refused before the native key pulse (for example via Hit Select or the final world/target/input/binding guard). No claim that the underlying player was attacked.
- `count.action_clicker_dispatched` remains the separate existing AutoClicker native output receipt.
- `count.combat_native_attack_callback`: a genuine local Forge `AttackEntityEvent` with the current local player, active world identity and exact current-world living target object was observed. This counter is **not attributed** to either synthetic source: a vanilla physical click can create it; cancellation, dropped packets and server rejection remain possible. Existing WTap event delivery is unchanged.
- `count.combat_aim_trigger_yield`: Aim Assist declined its camera write for a current, valid native crosshair TriggerBot candidate.
- `count.combat_aim_mouse_yield`: the optional manual mouse/input priority denied Aim Assist camera output for this tick (or the enabled mouse-helper signal was missing).

The existing opt-in `ModuleActionTelemetry` lane vocabulary gains `TRIGGER`; R3.54 adds only classified `DISPATCHED` and `REJECTED` events from ForgeCombatController. It does not change actual combat input generation, settings, attack scheduling, mouse, movement, Netty, or WTap sprint behavior. Runtime Diagnostics auto-exports these bounded numeric keys into existing schema-1 `config/ghostc/diagnostics-last.properties`; the strict backup rotation explicitly permits only the fixed new `COMBAT_*` counters to be absent from an older valid schema-1 report, while continuing to reject unknown keys, symlinks and oversized reports.

No packet bodies, target UUIDs, names, server addresses, chat, coordinates, mouse deltas, keys or per-tick timeline events are stored. This is passive local aggregated evidence only, off by default until the user enables F7 diagnostics.

## Tests and acceptance

- `CombatNativeEvidenceTests`: real HostRuntime/F7 diagnostics gating; distinct `TRIGGER`, `CLICKER` lanes and real callback totals; persisted schema-1 counters and pre-R3.54 backup migration; no sensitive fields; 50,000 seeded independent event samples showing no synthetic-key → native-callback inference; foreign thread owner enforcement.
- `tools/test_forge_wiring.py` checks real Forge callback and source-level opt-in wiring; the full Core CI `IntegratedModuleCatalogTests` executes the new test class.
- Exact-head Core Java17/21, Forge 1.8.9 Linux/Windows JDK8 build, native Netty/chest/ray/KeyBinding probes, reobfuscation and downloaded Windows artifact rehearsal must pass.
- Original Minecraft 1.8.9 gameplay: **not yet conducted**. A real session must still verify local input, event timing, manual mouse and camera, target switching, WTap sprint, and actual server acceptance. Native callback counters cannot identify which key caused them or show hit success.

## Machine readiness check (read-only)

The connected Windows development device has a vanilla `1.8.9` and `1.8.9-forge1.8.9-11.15.1.2318-1.8.9` version, but the default `.minecraft/mods` had **no GhostC JAR**, and no active Minecraft Java client process was found during the read-only inspection. The machine was left unchanged: no mods replaced, no client launched and no application interrupted. An original-game acceptance session remains a meaningful milestone, not a CI inference.

**GitHub-only draft, no merge, no force-push and no visual modifications.**
