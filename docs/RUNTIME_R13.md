# GhostC R1.3 — fail-closed combat runtime boundary

## Motivation

A source audit found that R1.2's per-feature exception guard protected HUD renderers, ESP and Auto Sprint, but **not the real Forge combat bridge**. A Java `RuntimeException` in Aim Assist, AutoClicker, Hit Select or Block Hit could interrupt the entire end-of-tick callback and leave a recently requested action in a shared coordinator.

R1.3 addresses that gap while keeping each combat module independently configurable and disabled by default.

## Implemented

- **Aim Assist** now executes its selection, `CAMERA` rotation request and camera write inside `host.runFeature("combat.aimassist", ...)`. Its owned rotation intent is canceled before each new tick; when a callback fails, it is canceled again. A fault disables only Aim Assist and records its sanitized exception type.
- **AutoClicker** runs the fixed-CPS timer and ATTACK/OUTPUT request within its own fault guard. When it fails, it resets the scheduler and rejects/removes its current attack intent. Vanilla physical clicks are untouched.
- **Hit Select** executes as a *nested, independent* guarded module only when the clicker proposes an extra attack pulse. If it throws, that extra pulse is rejected and only Hit Select is disabled; the next normal clicker tick can continue without hit filtering. It never cancels vanilla physical attacks.
- **Block Hit** runs its sword/use and interval policy under an independent feature guard and cancels its USE/OUTPUT intent if it fails. The single-owner bridge retains mutual exclusion between synthetic attack and use actions.
- Final native `KeyBinding.onTick` attack/use calls also execute within the respective module's guard, with full world/focus/physical-key revalidation. No direct entity attack, packet rewrite or network interception was added.
- `ForgeCombatController.reset()` clears all owned timers, physical-input admission and action/rotation intents across GUI/focus/world/disconnect transitions.

All normal Java `RuntimeException` failures are quarantined through the existing `FeatureFaultGuard`; Java `Error` and driver/native failures are deliberately **not** swallowed. The intended recovery is to inspect the reason and explicitly re-enable a fixed module. Fault status never includes exception messages, usernames, server addresses or raw packets.

## Further reliability changes

Previously the status HUD used the hardcoded label `R1.2` while the optional diagnostics HUD still displayed `R1.1`. Both now read a **single Forge-side compile-time `GhostCMod.CLIENT_VERSION` constant**, synchronized to Gradle and `mcmod.info` by a CI wiring test. R1.3 artifacts carry version **1.3.0**.

## Automated verification

- The new headless `CombatIsolationTests` covers isolated exceptions across all four COMBAT module IDs, persistence/re-enabling, partial action/rotation request cancellation, 900 fake service ticks with zero ghost actions and seeded recovery/fault sequences.
- `tools/test_forge_wiring.py` runs in Core and both Forge workflows and checks that the *real* Forge combat adapter uses independent module guards, cancels pending owned intents and emits vanilla key pulses through one bridge. It also checks native HUD labels read the shared build version.
- Java 17/21 Core CI compiles `--release 8`; Linux and Windows JDK8 Forge 1.8.9 CI compiles and reobfuscates the 1.3.0 JAR. The JAR classfile/CRC/version checks and fake-profile checksum install/collect/rollback rehearsal remain enabled.

## Outstanding runtime acceptance

No licensed Minecraft 1.8.9 process, GUI, authorized multiplayer test world or long-running OpenGL session has been launched by these checks. Java exceptions, real keybinding behavior and actual on-screen ESP/Target HUD rendering must still be observed in a **single consolidated Windows runtime milestone**. Consult `docs/RUNTIME_R10.md` for the SHA-256 guarded acceptance install/rollback and record results in Issues #2–6.

CI success does not prove actual game performance, hit registration, server compatibility or anti-cheat compliance.
