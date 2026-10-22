# GhostC R2.69 — optional multiplayer ping metric

The `hud.metrics` module now offers persisted Bool `show_ping`, off by default. With a valid local network tab-list entry, it draws `PING 42 ms` beneath any FPS/XYZ rows. The value is read locally from the existing Minecraft `NetHandlerPlayClient` player-info cache; no new request, packet, probe, timer, combat action or world mutation occurs.

`PingHudText` hides absent/negative response times and caps untrusted large values at `PING 9999+ ms`. The `StatusHudLayout` overload includes the ping row in the shared anchored and clipped footprint. The original overload preserves exact R2.68 placement with ping disabled. The F7 HUD editor uses the same `ForgePingReadout` and measured font width, preventing drag/preview mismatch.

`PingHudTests` cover boundary cases, 120,000 latency values, 85,000 random multi-line layouts, old-layout parity, profile save/reload and individual default reset. Forge wiring checks require the on-demand tab-list read, shared overlay/editor bridge and JAR class inclusion.

Verify Core Java17/21 and Forge Linux/Windows JDK8 exact PR-head builds, JAR/reobfuscation, simulated install/rollback, matching merged source tree and exact-main postmerge workflows. Native Minecraft 1.8.9 rendering, live ping accuracy, FPS and compatibility remain unverified.
