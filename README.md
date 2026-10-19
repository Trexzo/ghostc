# GhostC — modular Minecraft 1.8.9 client

**Milestone R2.6:** Java 8-compatible core, Forge adapter, original ClickGUI/HUD and shared gameplay service contracts. **Windows/in-game launch and visual fidelity are not yet verified.**

## What is implemented

- Host-independent registry, module lifecycle, typed settings, owner-thread policy, bounded event queue and profile persistence.
- `HostRuntime` receives client tick/render/input/world/disconnect transitions, isolates them from Minecraft objects, and persists toggles.
- Forge `@Mod` adapter under `forge189/`: world-only status/metrics HUD, F8 status toggle and F7 ClickGUI.
- New searchable ClickGUI: categories, settings, keybind capture, themed wide/compact layout; HUD editor with normalized positions and atomic preference persistence.
- R2.6 recovery transaction and bounded read: the actual input stream is capped at 1 MiB (including concurrent growth); legacy schema-1 files are also limited to 4096 parsed fields. Failed typed validation of a backup can no longer modify live module or UI settings before file recovery succeeds. Restoration promotes the **same exact staged and validated backup bytes** rather than applying one revision and restoring another.
- R2.5 typed backup validation: profiles and UI backups are checked against the **same complete typed settings parser used at load time**, in addition to the canonical SHA-256 digest. Correctly sealed but unloadable keybinds, enums or HUD positions can no longer replace a known-good .bak. Both success and negative paths are regression-tested.
- R2.4 backup rotation hardening: module-profile and UI saves now verify the exact staged source bytes, canonical digest and schema before replacing the prior .bak. A damaged or legacy unsealed primary can be overwritten with a new healthy save **without poisoning the last verified backup**; atomic promotion and temporary-file cleanup protect existing backups. Seeded corruption-and-save regression tests and Forge source contracts prevent reintroduction.
- R2.3 config resilience: modern module profiles and UI preferences include a canonical SHA-256 corruption check while legacy schema-1 files remain readable. Startup can restore validated .bak copies without overwriting the only good backup, preserving damaged originals as .corrupt. Oversized config files and malformed digests are rejected before any state mutation.
- R2.2 bounded Status/Metrics renderer: measure the combined Status/FPS/XYZ footprint so metrics-only HUDs and long coordinates remain screen-anchored. Status and Metrics now render through separate fault guards, and the F7 editor uses their measured footprint. Includes seeded geometry and independent fault-isolation regression coverage.
- R2.1 accessible HUD controls: dedicated Status/Keys/CPS/Target selector tabs, one-pixel arrow nudges (Shift+Arrow 10 pixels), and a reset action. Live editor previews and the actual Forge renderers now share settings-dependent Keystrokes/Target HUD heights, preserving normalized anchors and UI schema-1 persistence.
- R2.0 viewport-accurate HUD editor: a letterboxed representation of the actual Minecraft scaled screen replaces the stretched editor canvas; preview widget footprints, normalized drag coordinates and rendered anchors now share a pure geometry model, with automatic status size/scale parity. This corrects visual misplacement on widescreen aspect ratios while retaining all R1.9 widget preferences.
- R1.9 multi-widget HUD editor: Keystrokes, CPS and Target HUD now have separate draggable normalized anchors alongside the original Status HUD. The existing UI profile schema and status position remain backward-compatible; new coordinates are validated and persisted atomically. Forge renderers consume these anchors instead of hardcoded screen offsets.
- R1.8 reserved shortcut safety: the global F7 ClickGUI key can no longer be assigned to modules through direct binds or imported profiles, while the existing F8 status shortcut remains supported. The capture dialog now lets F7 close the GUI. A deterministic 10k-key replay and negative profile tests verify rejection without partial changes.
- R1.7 Compact GUI recovery: replaced the impossible 445px minimum (Compact maxes at 390px) with geometry-derived safe boundaries so Compact and Wide both work; reconciles selected module with current search/category results, preventing hidden settings/keybinding edits and handling zero results safely. Automated tests validate resize/toggle behavior and 25k seeded selection transitions.
- R1.6 ClickGUI chrome safety: header tabs/layout/theme controls and sidebar category clicks share exact visible geometry, while undersized scaled viewports display a resize notice and disable ambiguous controls. The module registry now retains exception types without private exception messages; both behaviors are protected by new Java and Forge-wiring tests.
- R1.5 immediate render/input admission: reject mouse and both 2D/3D render callbacks that arrive after an unadmitted world/player swap but before the next END tick; visible-row-only ClickGUI module and setting hitboxes, bounded wheel regions and reliable small viewport click handling. New seeded `ViewportHitTests` and Forge wiring gates guard against regressions.
- R1.4 session correctness: detect world/dimension/local-player object identity replacements without an empty-world tick, invalidate both old session generations and stale snapshots, skip malformed player poses, and reject nonfinite ESP geometry before calling OpenGL. All backed by direct Forge wiring and seeded lifecycle regression tests.
- R1.3 combat reliability: independent Java exception quarantine for Aim Assist, AutoClicker, Hit Select and Block Hit with fail-closed action/camera intent cancellation. Enhanced pure-Java session/fault replays and real Forge combat wiring checks, plus one shared mod version constant for both in-game HUD labels.
- R1.2 Forge feature wiring reconciliation: all HUD features, 3D ESP and Auto Sprint are once again routed through `HostRuntime.runFeature` with per-feature exception quarantine; independently guarded OpenGL state, visible sanitized fault warning and remapped Minecraft key labels.
- New Java/Python regression gates verify both the pure feature fault behavior and its actual Forge source wiring so later refactors cannot silently bypass the guard. **In-game rendering, input and stability remain unverified.**
- R1.1 ClickGUI viewport fixes: search hitboxes and focus rebind on scaled-screen changes; HUD editor preview and list geometry cannot exceed the window, and F7 can also close the GUI.
- Opt-in real-Forge callback evidence counters (GUI open/frame/close, overlay/world render, key/mouse events), saved as aggregates without names, chat or coordinates. In-game behavior remains unverified until a real client session records them.
- R1.0 Windows acceptance tooling in GitHub: SHA-256-gated install, owned-JAR backups, safe rollback, redacted counter-only evidence checklist and a CI-tested transaction against a fake Windows profile. This does **not** launch Minecraft automatically.
- Full JAR verification in CI: ZIP CRC, duplicate/unsafe archive entries, every Java class' Java-8 bytecode, required Forge entrypoint and matching mod metadata, tested on Linux and Windows.
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
- [R1.0 verified Windows acceptance transaction](docs/RUNTIME_R10.md)
- [R1.1 viewport reliability and callback evidence](docs/RUNTIME_R11.md)
- [R1.2 Forge feature wiring reconciliation](docs/RUNTIME_R12.md)
- [R1.3 fail-closed combat integration](docs/RUNTIME_R13.md)
- [R1.4 world identity and snapshot safety](docs/RUNTIME_R14.md)
- [R1.5 render identity and accurate GUI input](docs/RUNTIME_R15.md)
- [R1.6 safe ClickGUI chrome and private fault reporting](docs/RUNTIME_R16.md)
- [R1.7 compact GUI and filtered module selection](docs/RUNTIME_R17.md)
- [R1.8 reserved F7 keybind and profile admission](docs/RUNTIME_R18.md)
- [R1.9 draggable multi-widget HUD and schema-1 migration](docs/RUNTIME_R19.md)
- [R2.0 screen-aspect accurate HUD preview](docs/RUNTIME_R20.md)
- [R2.1 accessible HUD selection, keyboard moves and dynamic footprints](docs/RUNTIME_R21.md)
- [R2.2 bounded Status/Metrics rendering and fault isolation](docs/RUNTIME_R22.md)
- [R2.3 integrity-checked preferences and safe backup recovery](docs/RUNTIME_R23.md)
- [R2.4 verified backup rotation and corruption-safe saves](docs/RUNTIME_R24.md)
- [R2.5 typed backup admission](docs/RUNTIME_R25.md)
- [R2.6 transactional recovery and bounded config admission](docs/RUNTIME_R26.md)
- [Provenance](docs/PROVENANCE.md)
- [Verification](docs/VERIFICATION.md)
