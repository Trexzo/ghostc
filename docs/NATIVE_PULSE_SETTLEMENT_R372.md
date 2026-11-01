# GhostC R3.72 — uncertain native KeyBinding dispatch containment

Parent: exact R3.71 draft PR #209 head `12705899f76106841d92a1d9ea09df9a780beec0`. Scope: GitHub-only combat; no merge, no visual edits, no install or release.

## Gap

The native Forge attack and use callbacks ran `KeyBinding.onTick` before every associated post-dispatch policy was settled, and only then updated opt-in diagnostics. For TriggerBot, a RuntimeException during shared Hit Select commitment could leave the native attack already queued while TriggerBot cooldown was not committed. In R3.71 Block Hit, cancellation after a partial native pulse could also restore a staged USE timing event and retry it. An exception thrown by the native key call itself may have occurred before OR after vanilla queued the pulse; silently treating it as rejected is unsafe.

## Fix

A new read-only, owner-thread `NativePulseSettlement` guards two independent output lanes: ATTACK (AutoClicker/TriggerBot) and USE (Block Hit). `begin` occurs immediately before the real `KeyBinding.onTick`; `nativeReturned` marks that Java API's return; `committed` only follows every required Hit Select / TriggerBot / Block Hit policy commit; `abandon` executes unconditionally from `finally`. If execution exits at any point between begin and committed (including an ambiguous native API exception), the lane becomes UNCERTAIN and **cannot dispatch again in that game generation**. Ordinary focus changes, module resets and user toggles never silently clear it; only the actual SessionServices generation transition does. This is deliberately fail-closed.

If the key and all policies settled, optional telemetry or diagnostics failures occur *after* committed and do not retroactively mark a pulse unknown. Telemetry remains non-authoritative and may be incomplete after a diagnostic exception; the project must never claim server receipt or a hit. Existing feature fault isolation remains intact. The fence does not insert any extra key, adjust schedules, change settings or hook packets.

## Evidence

- `NativePulseSettlementTests`: injected native exception/partial Hit Select, TriggerBot and Block Hit policy state, settled postcommit diagnostic exception, independent ATTACK vs USE lane, owner-thread and generation rules, plus 90,000 seeded traces against a separate unknown-outcome oracle.
- `NativePulseSettlementTests` in Forge: Java8 original Minecraft 1.8.9 `KeyBinding.onTick` queues one attack/use event while the same settlement fence denies replay after simulated partial commitment.
- Mandatory ForgeGradle `nativePulseSettlementProbe` + source-wiring requirements enforce all three native call sites through begin/return/commit/finally. Exact-head Core 17/21, Forge Linux/Windows 8 check and reobfuscation, Windows downloaded-artifact rehearsal required.

No actual Minecraft client, hardware timing, network attack, server acceptance or player hit is certified by these offline tests.
