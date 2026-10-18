# GhostC — modular Minecraft 1.8.9 client

**Milestone R0.2:** clean-room Java 8-compatible core plus an experimental Forge 1.8.9 mod entrypoint. **Windows/in-game launch is not yet verified.**

## What is implemented

- Host-independent registry, module lifecycle, typed settings, owner-thread policy, bounded event queue and profile persistence.
- `HostRuntime` receives client tick/render/input/world/disconnect transitions, isolates them from Minecraft objects, and persists toggles.
- Forge `@Mod` adapter under `forge189/`: a world-only on-screen status label and F8 toggle.
- Offline core and host regression tests run in GitHub Actions for JDK 17/21; separate legacy ForgeGradle CI attempts a real reobfuscated mod JAR on JDK 8.

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
java -cp .build/classes dev.ghostfusion.demo.DemoMain
```

For Forge builds use **JDK 8 + Gradle 4.10.3** and `gradle -p forge189 setupDecompWorkspace build`. This old Gradle / ForgeGradle toolchain may have external repository compatibility issues; CI results take priority over assumptions.

## Next gates

See [issues](../../issues) for Forge runtime acceptance (#2), ClickGUI/HUD (#3), shared service contracts (#4), modules (#5), and quality/release (#6). Forge code is a first-party adapter; it includes no cracked client payloads, bypass claims, injection binaries or proprietary donor source. Minecraft/Forge dependencies are subject to upstream terms. Licensing for GhostC itself has not yet been designated.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Forge integration](docs/FORGE189.md)
- [Provenance](docs/PROVENANCE.md)
- [Verification](docs/VERIFICATION.md)
