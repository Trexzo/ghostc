# GhostC R1.2 — Forge feature-wiring reconciliation

## Why this milestone matters

The R1.1 `main` branch still built successfully, but a source audit found that **`ForgeFeatureDriver` had reverted to direct feature callbacks**: the documented R0.9 per-module fault isolation was no longer connected to HUD/ESP/Auto Sprint, and Keystrokes still showed fixed `W/A/S/D/SPACE/SHIFT` labels instead of the user's actual Minecraft 1.8.9 bindings. Compilation and headless `FeatureFaultTests` did not detect this, because those policy tests did not inspect the real Forge event adapter.

This batch repairs the regression and adds CI evidence preventing the same silent disconnect.

## Implementation

- `ForgeFeatureDriver.tick()` routes Auto Sprint through `host.runFeature("movement.sprint", ...)`.
- `render3d()` routes player ESP through `host.runFeature("render.esp", ...)`.
- Each of `hud.keystrokes`, `hud.cps`, `hud.target`, and `hud.diagnostics` now has its **own** `host.runFeature` and `RenderStateGuard.draw` scope. One feature's ordinary Java exception should not interrupt or contaminate the next module's draw. Only the offending feature is disabled and saved; it can be re-enabled from F7.
- `RenderStateGuard` now saves and restores **both the projection and modelview matrix stacks**, as well as GL attributes and the original matrix mode through `GL_TRANSFORM_BIT`. Cleanup is nested in `finally` blocks. This guards against Java-side state leaks; native driver/OpenGL crashes are not recoverable.
- Keystrokes uses `GameSettings.getKeyDisplayString(binding.getKeyCode())` for all six movement/jump/sneak labels, clips long display names to the available HUD cell, and respects rebinding.
- `ForgeEventAdapter` actually displays the existing sanitized `host.faultWarning()` ahead of configuration warnings, so the user knows why a feature was disabled.

## New verification gates

The existing Java `FeatureFaultTests` now tests fault isolation and persistence for **all six real first-party visual/movement module IDs**, including recovery after a saved profile reload.

`tools/test_forge_wiring.py` is a source-level contract test, run from **Core CI** and the **Linux/Windows Forge CI jobs**. It checks the real adapter still contains the guarded paths, native key binding labels, per-feature GL scopes, fault-warning forwarding and the R1.1 resize-aware ClickGUI wiring. These checks specifically catch the regression that passed earlier compilation and disconnected pure Java policy tests.

The 1.2.0 build continues to compile/reobfuscate with Forge 1.8.9 / JDK8 on Linux and Windows, validate its Java 8 JAR classes and rehearse checksum-gated Prepare/Collect/Rollback against a **fake** Windows Minecraft profile. SHA-256 manifests remain in GitHub Actions artifacts. No ZIP or local patch handoff is part of development.

## What is *not* verified

- No actual licensed Minecraft client was launched by GitHub CI.
- The Forge/OpenGL calls and Minecraft key display API may compile but still require rendering and input acceptance in a real game window.
- This batch does not implement packet interception, anti-cheat evasion, or a new combat module.

## Next meaningful local game acceptance

From the passing GitHub Actions Forge job, use `ghostc-forge189-1.2.0.jar` with its exact SHA-256 manifest and the existing `tools/Invoke-GhostCAcceptance.ps1` (`docs/RUNTIME_R10.md`) in a clean, legitimate Forge 1.8.9 profile.

In one consolidated session, verify: F7/ESC GUI, resized search hitboxes, F8 HUD, rebound Keystrokes labels, CPS, Target HUD, ESP boxes/tracers, Auto Sprint, optional diagnostic counters, focus/disconnect transitions and persistence. Capture a redacted `latest.log`, screenshots and representative FPS/soak results, and record actual failures in GitHub Issues #2, #3, #4, #5 and #6. **Do not close any in-game issue based solely on CI.**
