# GhostC R3.50 — native Aim Assist / TriggerBot camera handoff

**Stack:** separate Draft based on R3.49 PR #183 exact head `81b22712cd0c43bde0eff4fb1e1b468bfe01830b`. Independent visual HUD/ClickGUI #164/#181, all parent combat branches and `main` remain unchanged.

## Observed gap
R3.48/R3.49 ran Aim Assist's camera write before TriggerBot evaluation, marking `aimAdjusted` for that tick. If Aim Assist was configured with `require_click=false`, it could continue turning every frame, so TriggerBot's multi-tick target acquisition repeatedly reset even while the human crosshair was already over a valid player.

## Actual Forge 1.8.9 changes
- Resolve a crosshair entity snapshot once before any optional Aim Assist camera write. Its current-world **exact object** identity and health must still validate. This value is then shared across all combat policies for the tick.
- New pure `AimTriggerHandoff.yieldCamera` rule: TriggerBot enabled, physical attack and use not held, no use action ongoing, safe attack key binding and a genuinely valid native crosshair player. **Only then** does Aim Assist decline its own optional camera write for this tick; it never rotates the camera on TriggerBot's behalf.
- To prevent a stale renderer `objectMouseOver` result from authorizing the handoff or an attack, `triggerTargetValid` verifies the target is still the current-world object, alive, visible, within reach and valid scoreboard team, and independently intersects the **current camera look ray** with the native Minecraft 1.8.9 entity bounding box. No packet/ray transformer or direct attackEntity calls.
- The ray calculation is short-circuited when TriggerBot is disabled, physical input is held, the target binding is unsafe or another use action is ongoing.
- Aim Assist retains all existing manual attack, FOV, source pose, target lock, current-world and rotation coordinator guards. When it yields, it clears its optional stale retained lock rather than carrying a previous opponent into manual control.
- TriggerBot still requires its independent stable target UUID and acquisition ticks, actual ActionCoordinator ATTACK ownership, shared post-selection Hit Select check and final native target validation from R3.49. Yielding camera does **not** authorize an attack.
- UI/visual work, WTap actual attack event, Block Hit native-use timing and other modules are unchanged.

## Test and verification limits
- `AimTriggerHandoffTests` exercises all 64 Boolean guard combinations, explicit manual priority, target loss and stable-target TriggerBot acquisition, plus 50,000 seeded mixed input frames, integrated into existing Core test runner.
- Exact-commit Core Java17/21 and Forge Linux/Windows JDK8 build/reobfuscation, native packet/chest probes and artifact rehearsal remain mandatory.
- Original Minecraft acceptance remains unverified until a launched client checks real renderer ray/snapshot order, the stable crosshair during Aim Assist output, module enabling/disabling, click precedence, player/team changes and FPS. No CI result should be characterized as multiplayer success or anticheat parity.

**Safety/integration:** Draft only, no force push or automatic merge, no mods to visual PRs, and no claim that camera ray check is a server-authoritative target confirmation.
