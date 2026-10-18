# Verification baseline (2026-10-18)

- The standalone R0.1 source extracted from the previously created deliverable was compiled with `javac --release 8` on OpenJDK 21.0.12.1.
- **Observed offline:** `CORE_REGRESSION_PASS assertions=36` and `GhostFusion Core R0.1: DEMO_PASS (no Minecraft attached)`.
- The core files and tests were transferred into GitHub for continuous verification; GitHub Actions status must be checked separately. GitHub CI validates only the source committed in this repository.
- No Minecraft, Forge renderer, ClickGUI, packet logic, gameplay automation, injector, Windows GUI, gameplay tests, bypass assessment, FPS or long-run soak is verified by this baseline.
- GitHub Actions creates an explicitly named **headless** JAR artifact for each eligible CI run; it cannot launch Minecraft.

Definition of done: tests and CI green, applicable integration/runtime gates separately documented, no unsupported performance claims.
