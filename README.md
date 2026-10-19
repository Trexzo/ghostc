# GhostC — modular Minecraft 1.8.9 client

**Milestone R0.9:** Java 8-compatible core, Forge adapter, original ClickGUI/HUD and shared gameplay service contracts. **Windows/in-game launch and visual fidelity are not yet verified.**

## What is implemented

- Host-independent registry, module lifecycle, typed settings, owner-thread policy, bounded event queue and profile persistence.
- `HostRuntime` receives client tick/render/input/world/disconnect transitions, isolates them from Minecraft objects, and persists toggles.
- Forge `@Mod` adapter under `forge189/`: world-only status/metrics HUD, F8 status toggle and F7 ClickGUI.
- New searchable ClickGUI: categories, settings, keybind capture, themed wide/compact layout; HUD editor with normalized positions and atomic preference persistence.
- R0.9 stability: fault containment for Forge visual and Auto Sprint callbacks; failing features are disabled, persisted and reported without retaining exception messages. HUD keystrokes reflect actual Minecraft keybindings, not hardcoded WASD labels.
- New independent Windows JDK8 ForgeGradle build smoke gate, checks versioned Java 8 mod entrypoint and key classes; Windows GitHub runner is **not** a licensed Minecraft launch.
- R0.8 runtime safety: physical-release admission before automated combat pulses after GUI/focus/world changes, conflicting/unbound F7 keybind rejection, single-authoritative world readiness at END client tick and scheduled network disconnect callbacks on the Minecraft owner thread.
- Opt-in `hud.diagnostics`: nonidentifying tick/focus/session/action counters visible in game; on disconnect or close, writes `.minecraft/config/ghostc/diagnostics-last.properties` with a backup and no user/server metadata.
- Two additional opt-in combat controls: **Block Hit** (bounded sword-only USE pulses while attack is physically held, delayed when an extra attack pulse is queued) and **Hit Select** (target-hurt-time and minimum-tick-gap filtering of **AutoClicker-generated extra presses only**). Neither suppresses manual attacks; integration is Forge-CI-tested, not in-game verified.
- Two opt-in combat modules: bounded Aim Assist (local camera, FOV/range/speed/visible/hold-gated) and deterministic hold-to-click AutoClicker (CPS and optional entity-only constraint) through shared G3 owner-thread arbiters. No anticheat evasion or packet injection, and game runtime unverified.
- Five first-party functional modules: keystrokes, CPS counter, nearby-player Target HUD, player box/tracer ESP, and Auto Sprint. Forge code provides actual rendering or local player sprint requests, while testable pure-Java policies own filtering and telemetry. **In-game behavior is unverified.**
- Shared pure-Java services: read-only player snapshots and selection, rotation/action arbitration, packet-intent completion, serial inventory intent, session generation fencing. **Camera correction and vanilla attack-key intents exist; no Netty packet interception or manipulation.**
- Offline core, host, UI, service, feature and combat regression tests run in GitHub Actions for JDK 17/21; separate legacy ForgeGradle CI produces a reobfuscated mod JAR on JDK 8.

## GitHub builds

- [Core CI](../../actions/workflows/core-ci.yml) — headless Java core artifact, **not a mod**.
- [Forge 1.8.9 CI](../../actions/workflows/forge189-ci.yml) — builds `ghostc-forge189-*.jar` using ForgeGradle 2.1 and Forge 11.15.1.2318; when successful, the artifact is labeled **unverified in-game**.
- [Forge acceptance instructions](docs/FORGE189.md) — exact versions and testing boundaries.

For standalone core offline testing, with JDK 17+:

```bash
mkdir -p .build/classes .build/tests
javac --release 8 -d .build/classes $(find src/main/java -name '*.java')
javac --release 8 -cp .build/classes -d .build/tests $(find src/test/java -name '*.java')
java -cp .build/classes:.build/tests dev.ghostfusion.tests.CoreTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.HostRuntimeTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.UiTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.ServicesTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.FeatureTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.CombatTests
java -cp .build/classes:.build/tests dev.ghostfusion.tests.CombatMoreTests
java -cp .build/classes dev.ghostfusion.demo.DemoMain
```

For Forge builds use **JDK 8 + Gradle 4.10.3** and `gradle -p forge189 build`. This old Gradle / ForgeGradle toolchain may have external repository compatibility issues; CI results take priority over assumptions.

## Next gates

See [issues](../../issues) for Forge runtime acceptance (#2), ClickGUI/HUD (#3), shared service contracts (#4), modules (#5), and quality/release (#6). Forge code is a first-party adapter; it includes no cracked client payloads, bypass claims, injection binaries or proprietary donor source. Minecraft/Forge dependencies are subject to upstream terms. Licensing for GhostC itself has not yet been designated.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Forge integration](docs/FORGE189.md)
- [ClickGUI / HUD acceptance](docs/CLICKGUI.md)
- [Gameplay service contracts](docs/SERVICES.md)
- [R0.5 gameplay/render modules](docs/FEATURES.md)
- [R0.6–R0.7 optional combat controls](docs/COMBAT.md)
- [R0.7 action safety/verification](docs/COMBAT_R07.md)
- [R0.8 runtime admission and diagnostics](docs/RUNTIME_R08.md)
- [R0.9 Windows build and fault containment](docs/RUNTIME_R09.md)
- [Provenance](docs/PROVENANCE.md)
- [Verification](docs/VERIFICATION.md)
