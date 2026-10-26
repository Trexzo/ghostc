# GhostC R3.36 — Velocity / Knockback (client-side hurt-event mode)

**New real combat module:** F7 → COMBAT → **Velocity**, disabled by default. This branch is stacked on the integration Draft PR #168 at commit `a9c26644a34c6a5068f6ba4921b0601cdbfb44ef`, and does **not** modify any of the independently owned visual, combat, Panic, or main branches.

## Exact behavior

- `horizontal_percent`: 0–100%, default **100**. Scales local Minecraft `EntityPlayerSP.motionX` and `motionZ` **once** after observing an increase in `hurtTime`, provided the module is enabled and the player state is eligible.
- `vertical_percent`: 0–100%, default **100**. Scales **upward** `motionY` on that same event. Downward Y remains unchanged to avoid altering falls and gravity.
- 100%/100% makes **no client-motion write**. A profile with the module disabled performs no modifications. Both settings survive the typed profile store and F6 Panic.
- The dedicated Forge 1.8.9 client-tick controller samples only the **local live player**, not other entities. It is limited to the owner thread and reset on world transition, disconnect, focus/GUI loss, module fault, module disable and F6 Panic.
- There is no catch-up loop and no continuous modification of each subsequent hurt tick. Starting the module partway through an existing hurt animation only establishes a baseline, preventing an immediate double application.
- Motion values must be finite and plausibly bounded before modification. Flying/riding/water/lava/ladder/dead states are excluded. No outgoing or incoming packets are changed.

## What this version does **not** do

Minecraft 1.8.9's serverbound/clientbound movement model applies S12 Entity Velocity and S27 Explosion through NetHandlerPlayClient. There is **no guaranteed single authoritative packet-hook point in this module**: the controller responds to a native *hurt-time increase*, not to a decoded S12/S27 event. Thus it can miss knockback events that do not raise hurt time (for example, some rod, explosion, piston or other externally applied impulses), and it can sample the player's motion on a later tick after Minecraft physics has already acted.

This means R3.36 is **not equivalent to Vape/Slinky full packet-level Velocity**. Effective online knockback may vary with packet ordering, server physics and Minecraft tick scheduling, and this module is not claimed to bypass any anticheat. Later full support requires carefully reviewed native packet-dispatch instrumentation and original-game acceptance, not simply another settings toggle.

## Verification

The actual Forge controller lives at `forge189/src/main/java/dev/ghostfusion/forge189/ForgeVelocityController.java`. Deterministic pure Java policy lives at `src/main/java/dev/ghostfusion/features/VelocityPolicy.java`.

`VelocityTests`, invoked by the existing `IntegratedModuleCatalogTests` Core CI runner, verifies 30,000 seeded hurt-time events and independent motion axes, no double application, default 100/100, downward gravity, invalid numeric guards, session ticks, world generation, owner-thread restriction, UI sliders and saved profile. The integrated suite also tests that F6 Panic disables Velocity and retains its numeric settings. Original combat and HUD suites are unchanged.

Forge JDK8 Linux/Windows compilation and downloaded-JAR acceptance rehearsal are required. **Neither compilation nor that rehearsal launches Minecraft or establishes live knockback behavior.** An authorized local 1.8.9 environment must be tested at 20 TPS with both S12- and S27-caused knockback, hurt-time rises, rod/no-damage impulse, and at different FPS values. Confirm explicit module disabling, focus/Panic, and real key+rotation interactions before treating this as gameplay complete.
