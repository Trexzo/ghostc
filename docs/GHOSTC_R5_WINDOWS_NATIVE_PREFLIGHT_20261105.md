# GhostC R5 — Windows native preflight (2026-11-05)

Scope: **visual PR #181 only**. Source branch `feature/ghostc-r5-slate-polish-20261028`; tested source HEAD `b726943f54570a1467ce0545bd0c7908fa904b46`. This is a genuine Windows/Forge 1.8.9 startup and offline build preflight, **not** a completed visual or FPS acceptance.

## Reproducible environment

- Isolated clone: `C:\Users\Felix\Desktop\GhostC-R5-Acceptance` (separate from the existing Minecraft launcher profile and its mods).
- Windows 11, AMD Radeon RX 7900 GRE, Temurin JDK 8u504.
- Gradle 4.10.3 downloaded from its official distribution, SHA-256 verified against the corresponding official checksum.
- Forge `1.8.9-11.15.1.2318-1.8.9`, project `forge189/build.gradle`, GhostC version `3.0.31`.
- Source-only later commit `b726943f` fixes a Windows-incompatible HTML-escaping test fixture; it does **not** change the native JAR bytes relative to the earlier compiled Java sources.

## Evidence obtained

1. `gradle --no-daemon -p forge189 build` completed successfully on Windows/JDK8.
2. Versioned artifact `forge189/build/libs/ghostc-forge189-3.0.31.jar` produced. Local SHA-256: `D4F122DD147BA9BBDA3CC0CB31F70246497ECB28E4765FF8F19AFEC73414269B`. This is a **locally built** artifact hash, not a claim that CI binaries are byte-for-byte identical.
3. `tools/test_forge_wiring.py`: **145 tests passed** on Windows; `tools/verify_forge_jar.py --selftest`: PASS; packaged JAR verifier: **PASS**, Java 8 classes = **286**.
4. `tools/test_slate_visual_review.py`: **3 tests passed on Windows** after changing the synthetic filename fixture to use Windows-valid HTML-sensitive characters.
5. `gradle setupDevWorkspace`: PASS. `gradle runClient` started a real Minecraft 1.8.9/LWJGL2 client in the separate `forge189/run` directory. FML log recorded **"Forge Mod Loader has successfully loaded 4 mods"**, namely mcp, FML, Forge, ghostc. The sound and texture resources proceeded to initialize.
6. The launched test Java process was **intentionally terminated after load validation**. Gradle consequently reported a nonzero `runClient` exit; this must not be misreported as a spontaneous game crash.

## Not yet verified

- F7 ClickGUI open/close, real search/sliders, theme switching, HUD editor and native screenshots at GUI Scale Auto/2/3.
- Whether the optional, default-disabled Slate smooth font actually paints correctly, whether its GL state is restored, and actual FPS/frame-time differences.
- No screenshot was created: the remote process could not activate the game window to issue dependable F7/F2 controls.
- No signed/release-ready native artifact, full gameplay soak or merge approval is implied.

The normal Minecraft installation, launcher profiles, and existing mods were **not modified** during this isolated preflight. For visual evidence, use the existing `docs/SLATE_SMOOTH_FONT_NATIVE_ACCEPTANCE_R5.md` checklist and `tools/slate_visual_review.py` with original Minecraft F2 screenshots. Keep PR #181 draft and do not change the parallel combat branch.

## 6 November — reproduced native ClickGUI glyph corruption and scoped guard fix

A genuine Minecraft 1.8.9 visual review exposed severe colored atlas fragments instead of readable module/setting labels, even with the optional smooth font disabled. Source inspection isolated the original `RenderStateGuard.draw` wrapper around the entire `GhostClickGui.drawScreen`: its `GL_ALL_ATTRIB_BITS` pushes and restores `GL_TEXTURE_BIT`, potentially desynchronizing OpenGL's actual texture binding from Minecraft's `GlStateManager` binding cache. A locally compiled diagnostic build that skipped this GUI wrapper rendered the native text normally, without changing the font or layout.

The production fix uses `RenderStateGuard.drawGui` with `GL_TRANSFORM_BIT`: it still protects projection/modelview matrices and caller matrix mode, but leaves Minecraft's font/texture binding state under its native managers. `RenderStateGuard.draw` retains full-attribute restoration for non-GUI callbacks. Added a Forge wiring contract that explicitly rejects returning to the old full-attribute GUI wrapper. No game logic, profile serialization, input capture, combat path, or default font flags changed.

**Verification boundary:** diagnostic native screenshot establishes the full GUI guard as the source of corrupted glyphs; the scoped implementation compiled and loaded into an isolated Forge client/world, but a second pixel-perfect F7 screenshot of the scoped variant was not obtained before the test session closed. No FPS or long-session GL safety certification is implied. Continue native GUI-scale A/B acceptance before release.
