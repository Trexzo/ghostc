# GhostC — modular Minecraft 1.8.9 client

**Milestone R0.6:** Java 8-compatible core, Forge adapter, original ClickGUI/HUD and shared gameplay service contracts. **Windows/in-game launch and visual fidelity are not yet verified.**

## What is implemented

- Host-independent registry, module lifecycle, typed settings, owner-thread policy, bounded event queue and profile persistence.
- `HostRuntime` receives client tick/render/input/world/disconnect transitions, isolates them from Minecraft objects, and persists toggles.
- Forge `@Mod` adapter under `forge189/`: world-only status/metrics HUD, F8 status toggle and F7 ClickGUI.
- New searchable ClickGUI: categories, settings, keybind capture, themed wide/compact layout; HUD editor with normalized positions and atomic preference persistence.
- Two opt-in combat modules: bounded Aim Assist (local camera, FOV/range/speed/visible/hold-gated) and deterministic hold-to-click AutoClicker (CPS and optional entity-only constraint) through shared G3 owner-thread arbiters. No anticheat evasion or packet injection, and game runtime unverified.
- Five first-party functional modules: keystrokes, CPS counter, nearby-player Target HUD, player box/tracer ESP, and Auto Sprint. Forge code provides actual rendering or local player sprint requests, while testable pure-Java policies own filtering and telemetry. **In-game behavior is unverified.**
- Shared pure-Java services: read-only player snapshots and selection, rotation/action arbitration, packet-intent completion, serial inventory intent, session generation fencing. **No real combat/packet interception yet.**
- Offline core, host and UI regression tests run in GitHub Actions for JDK 17/21; separate legacy ForgeGradle CI produces a reobfuscated mod JAR on JDK 8.

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
- [R0.6 optional combat controls](docs/COMBAT.md)
- [Provenance](docs/PROVENANCE.md)
- [Verification](docs/VERIFICATION.md)
