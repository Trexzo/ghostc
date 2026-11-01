# GhostC R3.77 — damage-motion handoff across Velocity, WTap, Jump Reset

Parent: CI-verified R3.76 draft PR #214 at `51b79272c643b2de7e92d064c53e131a35f22999`. Scoped to native combat movement modules and tests; separate visual branch, defaults, release and main unchanged.

## Confirmed module interaction

The existing Forge END-tick executes Velocity, WTap and then Jump Reset. In hurt-mode, Velocity can scale the live player's **positive motionY** on a fresh damage edge. Jump Reset independently sees the same hurt-time rise later in that tick and calls vanilla `EntityPlayerSP.jump()`, overriding the previously reduced vertical motion. There is no native vertical ownership handoff between these two independent module policies. WTap reservation is already preemptive at the frame plan level and is preserved.

## R3.77

- Add pure `DamageMotionHandoff.verticalChanged(beforeY, adjustment)`, only identifying a confirmed **local policy change in vertical motion** (not horizontal-only adjustments, a passive damage observation, or Netty packet-mode activity); and `jumpAvailable(granted, verticalMotionOwned)`.
- `ForgeVelocityController.tick()` now returns true if a hurt-mode positive-Y adjustment reached the final native output stage. The write is marked **before** mutating any motion axes, so an exception after partial mutation still blocks a conflicting same-frame jump. Packet mode returns false; it remains asynchronously owned by the existing packet bridge, never mislabeled as a synchronous vertical write.
- `ForgeEventAdapter` carries this one-frame boolean from Velocity to Jump Reset after existing AutoSprint, projectile and combat grants. It does not retain it across sessions or later ticks. WTap's preexisting grant priority is unchanged.
- `ForgeJumpResetController.tick(boolean)` invokes the real `JumpResetPolicy.preview` each otherwise-admitted tick. When Velocity already owns vertical motion, it passes inactive admission, **consuming** the current hurt-time rising edge without a jump or delayed replay. Normal Jump Reset still works for horizontal-only Velocity, unchanged/negative Y, packet mode, and future unowned fresh damage events.

No artificial packet, KeyBinding, motion prediction, AutoSprint mutation, added delay or server ACK/hit inference is involved.

## Evidence and limits

`VelocityJumpHandoffTests`: exact same-damage conflict, horizontal-only coexistence, falling Y, fresh-vs-stale damage, WTap-first arbiter, mid-hurt world switch, and 80,000 seeded policy arbitration frames against an independent oracle. Java8 original Minecraft 1.8.9 `NativeVelocityJumpHandoffTests` verifies the modified Forge methods, public native player `jump()`, double motionY field and unchanged S12 packet API. Both Core and Forge checks require these probes and explicit source wiring.

CI green is not in-game evidence: native client input, OpenGL, actual packet arrival timing and server-side hit/knockback behavior still require manual Minecraft 1.8.9 gameplay acceptance.
