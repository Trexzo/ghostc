# GhostC — Minecraft 1.8.9 client project

**Current milestone: R0.1 headless core. Not yet a playable Minecraft mod or injector.**

The first working codebase is a clean-room Java module framework (`dev.ghostfusion.core`) developed for GhostC. Its provenance and test scope are documented in `docs/`. Do not infer in-game behavior from the offline tests.

## Implemented

- Stable module IDs, owner-thread lifecycle, fault isolation and shutdown
- Input key edges, duplicate-bind detection and typing-focus suppression
- Typed settings and schema-versioned on-disk profiles, with backups and prevalidation
- Bounded ingress for immutable events crossing into the owner thread
- Offline regression tests and a headless demo

## Build / test

With JDK 17+ installed (Java 8 bytecode target):

```bash
mkdir -p .build/classes .build/tests
javac --release 8 -d .build/classes $(find src/main/java -name '*.java')
javac --release 8 -cp .build/classes -d .build/tests $(find src/test/java -name '*.java')
java -cp .build/classes:.build/tests dev.ghostfusion.tests.CoreTests
java -cp .build/classes dev.ghostfusion.demo.DemoMain
```

Or use `./gradlew check` **after** a Gradle wrapper is added; until then, `gradle check` works with a compatible installed Gradle. GitHub Actions is the authoritative offline CI, using only JDK and checked-in Java source.

## Architecture & planning

- [Architecture / development gates](docs/ARCHITECTURE.md)
- [Donor reference policy](docs/PROVENANCE.md)
- [Verification scope](docs/VERIFICATION.md)
- [Tracked milestones](https://github.com/Trexzo/ghostc/issues)

The next technical gate is a **real Forge 1.8.9 adapter**. No renderer, GUI, packet hook, aim assist, auto-clicker, native injection or bypass logic is implemented in this repository yet.

## Security and licensing

Public repository: **never commit cracked third-party binaries, proprietary recovered code, account tokens, executable injectors or private data**. Donor archives are *architectural references only*. Licensing for GhostC must be explicitly chosen before releasing redistributed binaries or accepting third-party code.
