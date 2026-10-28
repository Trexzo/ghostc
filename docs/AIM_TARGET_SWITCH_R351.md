# GhostC R3.51 — native Aim Assist target-switch stability

**Scope:** separate combat Draft stacked on R3.50 #184 at exact head `31d73bd01181e73882577c6442b310e11aef1fe5`. Independent visual PRs #164/#181, parent combat branches, and `main` are unchanged.

## Source-level problem

In the original Aim Assist path, selecting by minimum angular error or distance every tick may alternate between similar nearby opponents. A hard `lock_target` setting already exists, but its persistent UUID preference differs from a brief optional switch stability window. A simple caching of the old entity is **not safe**: a despawned, moved, hidden or reused entity ID must never receive a camera write.

## Implemented

- New first-party, owner-thread `AimTargetSwitchPolicy`: tracks the **UUID and entity ID** last committed by a real owned camera write. A new already-eligible candidate must remain the selected UUID for `switch_delay_ticks` consecutive Minecraft ticks before changing players. During the candidate dwell the policy submits **no camera write**, rather than turning toward a stale old player.
- `combat.aimassist.switch_delay_ticks` is a bounded typed F7 setting (0–10, default **0**). Zero preserves original immediate target selection and existing camera speed. Initial target after world/reset is immediate. Editing the delay invalidates elapsed dwell. `lock_target=true` retains precedence and bypasses the separate switch delay on its hard-locked fallback.
- Admission occurs only after `AimAssistPolicy.suggest`, same-tick immutable snapshot and live Minecraft world object, player, visibility, alive/health and source/target pose checks have passed.
- `consider` stages a candidate. Only the actual `RotationCoordinator.Scope.CAMERA` owner writing the Minecraft player's camera may call `applied`. A failed arbitration cancels the prepared decision and **never commits target identity**.
- GUI/focus, module disable, Panic, world disconnect/epoch, a missing/stale native target, lost required physical attack hold, and the preexisting R3.50 TriggerBot handoff reset the switch gate. There is no lingering target after a gate loss.
- No extra attack packets, no new synthetic clicks, no enemy tracking outside live Minecraft's public entity/UUID data, no new camera/rotation writer; existing Aim Assist camera output continues in its single owner.

## Tests

- `AimTargetSwitchTests`: first acquisition and exact three-tick switch boundary; UUID reuse under same numeric entity ID; candidate loses a native camera decision without consuming/committing a switch; settings edits, module reset, owner-thread isolation, world epochs, profile persistence and F6 Panic. A seeded 50,000-tick independent UUID/candidate oracle includes denied camera writes, missing candidates and world resets.
- Existing `IntegratedModuleCatalogTests` includes the new suite in Core Java17/21 CI.
- Require exact-head Forge Minecraft 1.8.9 Linux/Windows JDK8 compile/reobfuscation, native Netty/chest/AABB probes and artifact rehearsal. **This cannot substitute for original Minecraft 1.8.9 runtime acceptance.** Real camera/mouse feel, combat engagement, HUD co-existence and server hit outcomes remain unverified.

## Ownership

No auto merge, no force push, no `main` writes and no changes to the concurrent Slate/HUD/ClickGUI visual branches.
