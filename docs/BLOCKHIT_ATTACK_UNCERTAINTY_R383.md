# GhostC R3.83 — Block Hit deferred USE versus uncertain native ATTACK

Parent: fully CI-verified R3.82 stacked draft PR #220 at `5f57372cc17516848f1c84a18adcff74d4371f60`. Only combat native input coordination and tests; no main merge, visual branch edits, installation, Minecraft launch, release, defaults or packet changes.

## Source-audited safety gap

`ForgeCombatController` arbitrates AutoClicker/TriggerBot into a single optional native ATTACK `KeyBinding.onTick`. `extraAttackQueued[0]` becomes true **only after** native input and all policy receipts complete (AutoClicker's post-commit path; TriggerBot operates while the physical attack key is unheld). A native ATTACK may already have reached Minecraft before `HitSelectPolicy.committed` or `TriggerBotPolicy.dispatched` fails. `NativePulseSettlement` then quarantines ATTACK as UNCERTAIN, but `extraAttackQueued[0]` remains false. Block Hit `preview` could see that false flag and schedule USE, and because `NativePulseSettlement` treats ATTACK and USE lanes independently, the Block Hit output path could send a native USE pulse in the same game tick despite an ambiguous ATTACK. This is not evidence of two server-accepted hits, but it is unsafe client-side action coordination.

## R3.83 fix

- Add read-only owner-thread `NativePulseSettlement.attackConflictsWithBlockHit(epoch,tick)` relying on already pinned world/player settlement. It denies dependent Block Hit USE if ATTACK is IN_FLIGHT, RETURNED, or UNCERTAIN, **or** if a native ATTACK was attempted in this same client tick even when all receipts completed and the lane is back to IDLE. Invalid/stale generation or tick fails closed. It does not mutate either lane or create output.
- In `ForgeCombatController`, the Block Hit final USE branch now calls the above guard immediately **after** existing `admitNative(USE,session.generation(),tickWorld,player)` and **before** all native keybinding output. Successful and uncertain native ATTACKs cannot share the output tick with Block Hit. While ATTACK remains unresolved, its dependent Block Hit USE is suppressed on subsequent ticks. The earlier USE admission still establishes exact native session proof before the guard runs.
- `BlockHitPolicy.preview` and `blockHit.cancelPrepared` continue their existing two-phase model. A denied native USE cannot consume cadence or a post-attack/on-damage event. Only **confirmed** extra ATTACK output can arm `post_attack`; a partially settled ATTACK is never treated as a successful event. Existing Block Hit `interval` / `post_attack` / `on_damage` modes and user settings remain unchanged.
- The original `NativePulseSettlement` ATTACK and USE lanes remain separately recorded and separately recoverable. The new dependency constraint applies only to synthetic Block Hit use, not vanilla manual use or unrelated inputs. Native uncertainty can only recover with a later verified generation and changed exact native player/world identity per existing code.
- Original WTap/projectile priorities, Clicker/Trigger arbitration, Hit Select final live damage and settings fences, key binding and target checks, and diagnostics counters remain unchanged.

## Regression coverage

`BlockHitAttackUncertaintyTests` validates actual delivered-but-uncommitted local ATTACK, ATTACK IN_FLIGHT/RETURNED/UNCERTAIN, completed same-frame ATTACK, pending Block Hit restore on denied output, no invented cooldown after a completely settled ATTACK, rejected pre-native attack (no fabricated receipt), confirmed next-tick post-attack USE, owner-thread protection and exact session identity recovery. It also runs 90,000 seeded independent ATTACK completion/partial/unknown cases against an independent same-tick/uncertainty oracle plus Block Hit two-phase cadence.

Mandatory original Minecraft 1.8.9 Java8 `NativeBlockHitAttackUncertaintyTests` checks real `KeyBinding` API and the genuine `ForgeCombatController.tick` entrypoint in a **headless** Java8 test: it simulates native ATTACK pulse receipt then partial policy failure and verifies no synthetic USE key pulse is emitted; no Minecraft process is launched. Source wiring assertions pin the interlock before the actual USE key pulse and preserve existing fallback/retry tests.

Required exact-head verification: Core Java17/21 and Forge Linux/Windows Java8 compile/reobfuscation, including Windows downloaded-artifact acceptance. Headless CI is not proof of Minecraft gameplay or server combat success.
