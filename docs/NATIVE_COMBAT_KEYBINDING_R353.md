# GhostC R3.53 — Real Minecraft 1.8.9 native combat key-input probe

## Scope and relation to parent releases

Separate, non-merging draft stacked on **R3.52 #186** exact parent commit `d5975fd1050564c26d6d722efec59bbd2d5e0e63`. All other combat PRs, independent visual work #164/#181, and `main` remain unchanged.

The previous Core CI evaluated AutoClicker/TriggerBot/HitSelect/BlockHit in pure-Java policy traces. Native Forge probes executed original 1.8.9 chest, Netty, ray/AABB classes, but did **not** exercise the actual Minecraft key press queue. R3.53 closes that *specific* gap, without calling it launched-game acceptance.

## Actual native test

`forge189/src/test/java/dev/ghostfusion/forge189/NativeCombatKeyBindingTests.java` executes **Minecraft 1.8.9's genuine**:
- `net.minecraft.client.settings.KeyBinding` creation and key-code registry
- `KeyBinding.onTick(code)` to generate a native key press
- `KeyBinding.isPressed()` to consume the exactly-one native event
- `KeyBinding.setKeyBindState(code, bool)` and `isKeyDown()` to show that a held physical key and synthetic press queue have different semantics
- `KeyBinding.unPressAllKeys()` for native reset.

It then runs a seeded 24,000-frame shared AutoClicker/TriggerBot replay through the real `ActionCoordinator`, `TriggerBotPolicy` acquisition/cooldown state machine, R3.49 two-phase `HitSelectPolicy.preview/committed`, R3.49 post-dispatch `BlockHitPolicy.post_attack`, and actual Minecraft `KeyBinding.onTick/isPressed` queues. The explicit last-native guard rejects some winners to test that denied intents generate **zero** native clicks, keep shared cooldown unaffected, and never schedule a phantom Block Hit use. It checks UUID acquisition, world epochs and both attack sources. Synthetic queuing alone cannot arm WTap: only Forge's existing genuine `AttackEntityEvent` pathway owns WTap attack state.

A mandatory `nativeCombatKeyProbe` task is attached to ForgeGradle `check`, so Linux and Windows JDK8 Forge CI and downloaded-jar rehearsal cannot pass by accidentally skipping this acceptance. Core's `tools/test_forge_wiring.py` asserts that the native probe, actual KeyBinding calls and genuine AttackEntityEvent/Wtap source boundary remain integrated.

## What this demonstrates — and what it cannot

**Demonstrates if Forge CI passes:** Minecraft's original 1.8.9 KeyBinding API really queues and consumes the requested attack/use input, and the shared policies preserve one-winner, cooldown, physical-hold and post-click ordering contracts against that native class. This is stronger than pure Java scheduling simulations.

**Does not demonstrate:** licensed Minecraft launched; native client tick vs rendering/mouse-delta timing; real game controller consumption of KeyBinding; real world EntityPlayer ownership; actual local vanilla AttackEntityEvent; latency/server hit ACK; WTap server effect; movement/input feel; or anticheat compatibility. Those require a controlled original-game session with opt-in diagnostics and the existing safe Prepare/Collect/Rollback evidence workflow.

The test uses only temporary unused key codes in its own headless Forge JDK8 JVM; it never modifies a game profile, sends packets, intercepts native input, or claims to simulate a network server.

## Acceptance gates

1. Exact-head Java17/21 Core CI (including the new mandatory native wiring source test).
2. Exact-head Linux/Windows Java8 ForgeGradle compilation, reobfuscation and `nativeCombatKeyProbe` plus existing chest, packet and ray probes, then Windows artifact acceptance rehearsal.
3. Later, separately captured original Minecraft session evidence, which **remains pending** until observed.

**Draft-only; do not merge, force-push, or alter the visual PRs.**
