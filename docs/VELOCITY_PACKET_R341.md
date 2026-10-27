# GhostC R3.41 — packet-aware Velocity (S12 + S27)

**Independent Draft stacked on R3.40 AutoArmor #173**, tested parent commit `f2c7479b0abab98581b9b51b6ae283f08f1462ff`. No changes to `main`, previously opened PRs, or the parallel visual/HUD branch.

## What actually changed

The existing **Velocity** combat module now has `mode` under F7:

| Mode | Default? | Behavior |
|---|---|---|
| `hurt` | **Yes — legacy unchanged** | Owner-thread local motion scaling once per native `hurtTime` rising edge, as in R3.36 |
| `packet` | Optional | Intercepts inbound **S12 Entity Velocity** and **S27 Explosion** *before* vanilla `NetHandlerPlayClient`, scales the actual encoded knockback components with the existing horizontal/vertical percent settings and passes the packets to Minecraft |

At **100%/100%**, no packet mutation occurs. The `combat.velocity` module remains **disabled by default**; switching modes requires explicit F7 user action. The two modes are mutually exclusive: packet mode never applies the old hurt-edge motion scaler, preventing double reductions.

### Native 1.8.9 integration

The new `ForgeVelocityPacketBridge` uses the active `NetworkManager`'s 1.8.9 Netty channel and adds one inbound `ChannelDuplexHandler` directly *before* `packet_handler`, after decoding. The channel field and packet motion fields are located using known **MCP/SRG/obfuscated aliases**, and every reflected field must have the precise expected Java type (`Channel`, `int`, or `float`) before use; if a field is absent, incompatible, or inaccessible, the packet remains unchanged. The hook is installed/removed on the channel's Netty event loop, never through unsafe concurrent pipeline mutation.

The Minecraft **owner thread** alone reads the local player ID and settings, publishing an immutable, `volatile` `Settings` snapshot. The Netty listener reads only that snapshot and the incoming packet. It does **not** read the Minecraft world, a player object, HostRuntime, or UI off-thread.

- **S12** is an absolute, signed short-based (value × 8000) velocity write. Scale the raw packet's integer X/Z components by `horizontal_percent / 100`, its Y by `vertical_percent / 100` with deterministic rounding, **only when** `packet.getEntityID()` equals the live local player's captured entity ID. Do **not** modify S12 for other players or entities.
- **S27** adds a float impulse to the local player. Scale only the three bounded finite motion floats, leaving explosion center, block-destruction list, particles and all unrelated packet content unchanged. This captures explosion knockback without requiring any `hurtTime` increase.
- **No packet cancellation, delay, queueing, loss, replay, resends, artificial position updates, transaction manipulation, or bypass-specific timing** is introduced. Every inbound message continues via `ctx.fireChannelRead(msg)`.
- World change, GUI/focus loss, F6 Panic, module disable/fault, disconnect or switching back to `hurt` immediately sets the hook's immutable snapshot to OFF and schedules removal from the old channel. Existing input/host lifecycle safety checks remain.
- One handler per connection, named `ghostc_r341_velocity`, never a global or permanent injection. No coremod or transformer is required.

## Testing and limits

`VelocityPacketMath` is pure, owner-independent, and rejects nonfinite or excessive S27 impulses. `VelocityPacketTests` performs **45,000 deterministic seeded** S12/S27/identity traces (over 225,000 assertions), plus exact 100% bit-preserving no-ops, invalid inputs, cross-thread math and persisted mode/profile tests. This is included in the existing Core regression runner along with original Velocity, combat, AutoArmor, Inventory Manager, Chest Stealer and F6 Panic tests. No CI workflow YAML changes.

**Important:** Offline Core/Forge CI can confirm Java source, Forge 1.8.9 compilation/reobfuscation, settings and static math. It does **not** establish that Netty field aliases and the pipeline hook behave correctly in a real obfuscated Minecraft install, whether server velocity validation accepts the resulting position reports, or whether a given private server's custom packet model follows vanilla. The runtime must be checked with controlled S12 on local player versus other entity, S27 explosions without damage, rapid world switches, SP/integrated server, shared channel dimensions, focus, Panic, unrelated network traffic, and actual FPS. Only then consider a release.

**Prior modes preserved:** R3.36's `hurt` is still available in case runtime packet instrumentation is unavailable. It is intentionally *not* activated as a silent fallback when `packet` mode fails, because that could cause unpredictable double motion adjustment and obscure broken instrumentation.
