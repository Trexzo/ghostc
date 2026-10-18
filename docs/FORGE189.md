# G1 — Minecraft 1.8.9 Forge adapter

## Versions / trust boundary
- Minecraft **1.8.9**
- Forge **11.15.1.2318-1.8.9**
- ForgeGradle **2.1-SNAPSHOT** (legacy plugin coordinate: snapshot, so this *is not immutable pinning*).
- MCP mappings **stable_22**.
- JDK **8** for Forge toolchain, Gradle **4.10.3** from official distribution in CI.
- Headless core remains compiled with Java 8 target by JDK 17 and 21.
- Forge dependencies sourced via Maven Central and `https://maven.minecraftforge.net/`.

Historical ForgeGradle snapshot tooling is known to have repository/TLS and Java compatibility issues; **a real CI success is required** before claiming the Forge JAR is available. The exact runtime is not yet tested in Minecraft. Never upload game files or third-party paid-client binaries to this repository.

## Runtime wiring
`GhostCMod` initializes one `HostRuntime` and registers `ForgeEventAdapter` on both MinecraftForge and FML buses. The adapter covers:
- END client ticks and bounded queue draining;
- 2D status HUD (only in-world, only while module enabled);
- physical keyboard edges with GUI/chat suppression (F8 by default);
- client-world transitions; server disconnection;
- per-user profile under `.minecraft/config/ghostc/default.properties`.

The core references no Forge/Minecraft classes. It owns event ordering, lifecycle and profiles; the Forge adapter owns all gameplay and OpenGL interaction. F8 toggle is persisted to a schema-1 profile.

## Manual acceptance (not yet verified)
1. Use a clean licensed Minecraft 1.8.9 Forge profile with Forge 11.15.1.2318.
2. Install the JAR built by the **Forge CI** workflow, not the headless core demo.
3. Launch, open a world; confirm a small GhostC status string appears.
4. Press F8 once: hides text. Press F8 again: restores text. Repeated keydown must not cause multiple toggles.
5. Open chat, press F8, close chat: state must not toggle.
6. Leave and rejoin world, then restart: status and profile should persist; no cross-world phantom key presses.
7. Save `latest.log`, versions and startup/crash info as verification evidence before checking the in-game gate.
8. Observe the absence of console exceptions and conflict with other mods.

**CI compiling Forge is not proof of launch, input capture, rendering or behavior.**
