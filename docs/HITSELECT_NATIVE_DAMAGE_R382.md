# GhostC R3.82 — Hit Select final damage-state revalidation

Parent: CI-verified R3.81 draft PR #219, head `d7d1a4dae1a93f7066a41d9888ab7eab4d4d60a9`. Combat-only, stacked GitHub draft; no release, merge, installation, Minecraft launch or separate visual branch edits.

## Source-audited flaw

The shared Hit Select preview is evaluated after the ActionCoordinator picks one AutoClicker or TriggerBot ATTACK candidate. It sees the then-current target `hurtTime`, player `hurtTime`, mode (`normal`, `ready`, `trade`), maximum target hurt, trade minimum and shared cooldown. The R3.81 final native gate checks exact target and item identity where configured, but did not recheck **target/self damage and Hit Select settings** before `KeyBinding.onTick`. A damage hit, state transition, or settings change after preview could therefore let the selected ATTACK bypass its own live timing predicate. The previous preview also had no immutable configuration proof to bind the output to its original granted settings.

## R3.82 change

- `HitSelectPolicy.preview()` now records the approved mode and max-hurt/minimum-gap/trade-minimum values for **only the pending candidate**.
- `HitSelectPolicy.confirmPrepared()` is a second owner-thread admission. It requires the exact pending generation/tick/mode/limits, revalidates current target presence, current target hurt threshold, ready-mode zero hurt, trade-mode current self hurt and the prior committed native cooldown. Failure **revokes the pending preview**, but cannot erase `lastAccepted` or permit a delayed replay. Success never dispatches or commits; only the existing native `KeyBinding.onTick` followed by `hitSelect.committed` does that.
- `ForgeCombatController` calls `confirmHitSelectNative` at the end of **both** Clicker and TriggerBot native output guards. The helper reads fresh registered target/crosshair identity, target `hurtTime` and local player `hurtTime`, then checks current timing/settings through `HostRuntime.runFeature(HIT_ID)` before any pulse begins. A previously observed entity cannot be silently replaced. Hit Select disabled during the same tick, invalid native object or a late settings edit causes a rejection rather than an unfiltered pulse.
- A require-target option captured at preview remains required if disabled later in that same tick; enabling it later is also honored. If the mode explicitly allows no target, it retains air/empty-target behavior, while not accepting a replaced originally sampled entity. The original optional Hit Select-off path remains unchanged.
- Shared action arbitration, AutoClicker scheduling, TriggerBot dwell, manual input, projectile cooldown continuity, native uncertainty quarantine and user defaults are unchanged. No new KeyBinding action, packet, cooldown, speed or camera effect is added.

## Test evidence and limitations

`HitSelectNativeDamageTests` includes exact normal/ready/trade-mode hurt changes; target disappearance, config edits, wrong session/tick, canceled pending permission, genuinely committed shared cooldown, next-session fresh preview, live world identity and foreign-thread rejection. It also evaluates 90,000 seeded combinations of mode, limits, target hurt, self hurt, target presence, edit flags and exact epoch/tick against an independent final-action oracle, ensuring a rejected final gate never consumes cooldown.

Mandatory original Forge 1.8.9 Java8 `NativeHitSelectDamageTests` links current `EntityLivingBase.hurtTime`, original `KeyBinding.onTick` and the native controller validation entrypoint, testing ready/trade vetoes. The Python source-wiring checks require the real native controller to gate both attack sources before `KeyBinding.onTick`.

Require exact-head Core Java17/21 and Forge Linux/Windows Java8 compile/reobfuscation plus downloaded Windows artifact acceptance. Offline/headless tests are not actual Minecraft gameplay, server packet acceptance, or verified PvP hit physics.
