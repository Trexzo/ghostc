# GhostC R3.49 — commit native attacks before consuming shared cooldown

**Stack:** R3.49 is a separate draft stacked on TriggerBot R3.48 PR #182 at exact parent `44b4ede674757a2916e26a724fe25cb9fe2bef78`. Prior combat branches, `main`, and separately owned visual PRs #164/#181 are unchanged.

## Reproduced source-level correctness gaps

1. R3.48 `ForgeCombatController` used `HitSelectPolicy.accept(...)` immediately after consuming an `ActionCoordinator` winner, **before** final native eligibility and `KeyBinding.onTick`. If a target, held-use, focus or binding guard rejected that winner, Hit Select already counted a cooldown despite no local key pulse.
2. R3.48 treated a *selected* AutoClicker request as an `extraAttackQueued` event in the `BlockHitPolicy` post-attack mode. Block Hit was scheduled before the attack's final native guard and could start an event for an attack that never actually dispatched.

## Repair

- Preserve the legacy atomic `HitSelectPolicy.accept` API for existing policy consumers/tests.
- New `preview(...)` records an owner-thread, same-epoch and same-tick pending admission **without committing cooldown**; `committed(tick,epoch)` is legal only once, after the successful `KeyBinding.onTick` call; `cancelPrepared()` drops any stale candidate without affecting last actual dispatch. Mode switch, backwards tick, wrong epoch, foreign thread and Panic/reset are strictly checked.
- Both AutoClicker and TriggerBot still share exactly one session-scoped `ActionCoordinator.Type.ATTACK` decision. Native output commits the shared Hit Select gate only on a completed local key pulse. TriggerBot's own cooldown also commits only after that pulse.
- Only the successfully dispatched AutoClicker native pulse marks `extraAttackQueued`; the real `BlockHitPolicy` post-attack plan now executes **after** attack dispatch. No speculative Block Hit from an approved but rejected click.
- Keep existing physical mouse/GUI/focus/world/target checks, real Minecraft 1.8.9 KeyBinding dispatch, WTap's genuine AttackEntityEvent gate, and unchanged Aim Assist/Velocity/Bridge policies.
- No extra packets, direct entity hits, anticheat bypass logic, spoofing, or GUI changes.

## Regression evidence and acceptance boundaries

`AttackCommitTests` exercises 50,000 seeded frames of mixed AutoClicker/TriggerBot proposals, Hit Select preview/commit/rejection, one action winner per game tick, successful-click-only Block Hit input, owner-thread, resets, exact cooldown boundaries and rejected native dispatch retry. It is required by the existing `IntegratedModuleCatalogTests` Core runner.

Exact-commit Core CI Java17/21, Forge Minecraft 1.8.9 Linux/Windows JDK8 build/reobfuscation and native probes remain release gates. Core policy simulation and Forge compilation **cannot** certify launched original Minecraft click processing, physical input feel, server hit acceptance, or anticheat effects. A controlled in-game pass must test failed dispatch, held/released click vs TriggerBot and Block Hit timing and WTap native event provenance before merging.

**Integration policy:** Draft only, no force-push, no auto-merge, no modifications to parallel visual branches or `main`.
