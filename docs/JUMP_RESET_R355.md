# GhostC R3.55 — Jump Reset (actual combat module)

**Parent:** fully CI-green R3.54 draft PR #188, exact SHA `bd45664173eb7e6f5b086a413172dae0e8629315`.

## Gameplay behavior

New **Combat → Jump Reset**, independently toggleable in F7 and **disabled by default**. The module observes the native Minecraft `EntityPlayerSP.hurtTime` counter and requests a **single native `player.jump()`** when a fresh high-hurt-time edge arrives while the player is eligible and grounded.

Settings: `min_hurt_time` (1–10, default **8**), `cooldown_ticks` (2–40, default **12**) and `require_attack_held` (default **true**). The module never modifies raw `motionY` or `motionX/Z`, spoofs key-up/key-down, injects packets, rotates the camera, selects a target, or submits synthetic attacks.

`JumpResetPolicy` is an owner-thread **ARMING → READY → PREPARED → COOLDOWN** state machine. On login/reconnect it seeds the first hurtTime sample and deliberately **does not** jump into an existing hurt animation. A new injury is recognized only when hurtTime increases compared with the immediately preceding accepted frame and reaches the configured threshold. Ineligible frames still update the hurt baseline; no queued/delayed jump can appear after the user opens a GUI, changes an input binding, moves away, disables a feature or returns from an inactive window. The cooldown starts **only after the genuine native `EntityPlayerSP.jump()` call**. A failed safety check cancels preparation without claiming a jump or consuming cooldown.

Native admission requires a current, exact-world local player that is alive and grounded, with valid health, forward movement, no manual jump/sneak, no item use, no water/lava/ladder, riding, flight, horizontal collision or GUI/focus loss. The optional `require_attack_held` must be satisfied by actual Minecraft `keyBindAttack.isKeyDown()`. Minecraft's own jump method retains responsibility for jump movement physics and normal local/server updates.

## Working alongside other modules

- `ModuleInteractionStateMachine.Resource.JUMP_RESET` is distinct from the attack and velocity resources; legacy 8-argument `Enabled` frames default it **off**. A non-game/GUI/focus-lost frame disallows the feature before any native write.
- If WTap owns its time-bounded **sprint reset** window, `JUMP_RESET` receives `WTAP_RESERVED` denial. The skip is not replayed after WTap releases. AutoSprint continues its original priority rule.
- Forge order remains: AutoSprint, Combat, BridgeAssist, Velocity, WTap, **then JumpReset**. This means BridgeAssist's synthetic sneak (if any) and Velocity's own hurt-edge decision are known before jump eligibility is evaluated. The jump does not override their attack/sprint, sneak, key or packet ownership.
- No new state machine owns Sprint or Attack; Jump Reset owns only a single locally authorized jump decision and clears on F6 Panic, module disable, disconnect, world swap, GUI, and focus loss. Manual jumping is never overwritten.
- Existing diagnostics `ACTION_JUMPRESET_DISPATCHED` records *native jump method called*, never server-accepted movement/damage.

## Verification

- `JumpResetTests`: exact hurt-edge and cooldown boundaries, denied native commit, session reset, owner-thread constraints, manual/GUI/WTap denial, backward tick rejection, profile persistence and F6 Panic, plus **50,000 deterministic input/hurt frames with independent decision oracle**. Called by `IntegratedModuleCatalogTests` Core Java17/21.
- Mandatory ForgeGradle `nativeJumpResetProbe` on Windows/Linux JDK8: actual Minecraft 1.8.9 `EntityPlayerSP` linkage and public `jump()` method, plus real core policy under the Forge dependency classpath. The true Forge `ForgeJumpResetController` compiles and reobfuscates with the full mod.
- Mandatory `tools/test_forge_wiring.py` assertions for independent F7 module registration, shared WTap/GUI admission, original native jump, no raw motion writes/key spoofing, and call ordering.
- **Original launched Minecraft 1.8.9 gameplay remains UNVERIFIED.** In a controlled licensed-game run, verify hurtTime timing relative to END client tick, when the player is grounded, manual jump priority, interaction with WTap/Velocity, visible movement results, FPS and server authority. No source/CI test can infer real PvP benefit or anticheat acceptance.

**No main merge, force push, direct installation, or visual PR edits.**
