# GhostC R0.9 — cross-platform build and feature fault isolation

## What changed

### Per-feature callback fault containment

`FeatureFaultGuard` is an owner-thread gate between the Forge adapter and individual **visual or Auto Sprint** module callbacks. It checks the module is enabled; if the callback throws a `RuntimeException`, GhostC:

1. Turns off **only that module** through `ModuleRegistry`. Other enabled modules continue in the same GUI/world render or tick.
2. Persists the disabled state with the normal profile store, preventing repeated exceptions on subsequent restarts.
3. Increments `RuntimeDiagnostics.Counter.FEATURE_FAULT` and exposes a short warning with the stable module ID and exception **type**.
4. Keeps a bounded 32-entry snapshot of recent faulted modules. Exception messages, packet bytes, game chat, usernames and stack traces are not retained in this summary.
5. Allows recovery if the user explicitly re-enables the module from the GUI and its callback succeeds.

The guard deliberately does **not** swallow JVM `Error` subclasses. It is applied to `hud.keystrokes`, `hud.cps`, `hud.target`, `hud.diagnostics`, `render.esp` and `movement.sprint`. Combat and Forge framework callbacks are **not yet covered by per-module fault isolation** and remain a separately tracked integration risk. No claim is made that catching a Java exception can protect against native OpenGL crashes.

### Correct key indicators

The Keystrokes module now displays the current Minecraft 1.8.9 key binding for forward/left/back/right/jump/sneak using `GameSettings.getKeyDisplayString` instead of hardcoded W/A/S/D/SPACE/SHIFT. Long localized binding labels are clipped to the small HUD cells; physical pressed state still uses Minecraft's own `KeyBinding.isKeyDown`.

### Windows Forge build smoke

The existing Linux/JDK8/ForgeGradle 2.1 job remains. A second independently executed job runs on **GitHub Actions windows-latest**, uses Temurin JDK8 and Gradle 4.10.3, then:

- compiles and runs `reobfJar` through `gradle -p forge189 build`;
- checks that the versioned `ghostc-forge189-0.9.0.jar` is present;
- inspects the JAR entries for the Forge `@Mod` entrypoint, Forge event adapter, feature driver, combat driver and core;
- verifies Java classfile major version 52 for the mod entrypoint;
- publishes a separate Windows-build GitHub artifact with SHA-256 manifest.

This is a **Windows build compatibility check, not a Minecraft client GUI launch**. The Windows runner does not start Minecraft, authenticate with Mojang/Microsoft, use an actual game world, or render the HUD.

## Evidence levels

| Gate | Meaning |
| --- | --- |
| Core JDK17/JDK21 regressions | Pure Java source and mocked/simulated behavior tested offline |
| Linux Forge JDK8 CI | Compiles/reobfuscates and validates mod JAR class inventory/version |
| Windows Forge JDK8 CI | Confirms compatible ForgeGradle build and bytecode inventory under Windows |
| Actual Windows Minecraft 1.8.9 Forge launch | **Still pending; not implied by CI** |
| In-world GUI / input / ESP / combat / restart / soak | **Still pending** |

## Next consolidated runtime acceptance

Use an authorized, licensed Minecraft 1.8.9 Forge 11.15.1.2318 setup, not a public competitive server. Install the versioned R0.9 JAR from the passing GitHub Actions build, ideally with a clean mod profile. At a **single meaningful milestone**, capture whether the client launches, the F7 UI and F8 HUD show correctly, rebound key labels render, session diagnostics update after world/focus/reconnect, and the five visual/movement features behave. Test failed-module quarantine with a deliberate non-game fixture if available; never intentionally corrupt or crash a live Minecraft session.

If any test fails, provide `latest.log`, crash report when produced, exact Forge build and runtime steps. Logs may contain server and account data; redact before sharing. Attach the observation to Issues #2, #3, #4, #5 or #6 as appropriate. Do not mark an in-game gate green because a GitHub Actions job compiled successfully.

## Known limits

- ForgeGradle 2.1 is a historical snapshot dependency, not a pin to immutable content.
- No automatic Windows game GUI test or 60-minute Minecraft soak has been established.
- Platform-specific reobfuscated JAR **hashes may differ** due to ZIP timestamps/build ordering; matching class/source structure is the current gate, not bit-for-bit reproducibility.
- Windows build artifacts are labelled `unverified-ingame` deliberately.
