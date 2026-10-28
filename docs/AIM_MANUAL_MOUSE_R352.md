# GhostC R3.52 — physical mouse precedence for Aim Assist

**Stack:** Draft based on exact R3.51 #185 head `f3b142a5bfa7fc4a10b885d492873c28153528d9`. No parent combat or independent visual changes, no modification to main.

## Motivation

Aim Assist's optional camera writes could compete with the user's intentional, real mouse rotation. The existing native `PhysicalInputGate` governs held attacks, not camera ownership. R3.52 introduces an **opt-in, bounded camera suppression policy** using Minecraft's already sampled mouse deltas.

## Behavior and scope
- New typed F7 Aim Assist controls: `respect_mouse=false` (legacy default), `mouse_delta_threshold=3` (1–32), `mouse_pause_ticks=3` (1–10).
- When enabled and the real Minecraft `mc.mouseHelper.deltaX/deltaY` magnitudes reach the threshold, `AimManualInputPolicy` records that native game tick and suppresses Aim Assist camera output for the configured hold (including the current tick). Camera writing resumes naturally on expiry without catch-up or old target rotation.
- Signed mouse deltas (including `Integer.MIN_VALUE`) compare safely by widening to long before absolute-value calculation. Owner-thread and game-generation fences prevent foreign worker/input threads from mutating rotation ownership.
- During deliberate manual override, R3.51 retained Aim Assist target identity and any pending switch are cleared. This prevents camera snapping back toward an opponent after the user turns away. R3.50 TriggerBot native-ray acquisition/hand-off continues unchanged: real physical attack/use and target correctness still govern TriggerBot.
- GUI/focus loss, Panic, world change, module disable, lost required physical attack hold and feature failures reset the manual policy. A missing Minecraft mouse helper when manual override is explicitly enabled results in a conservative no-camera-write.
- No independent mouse polling, OS hooks, synthesized mouse events, direct packet mutation, target spoofing, or additional attack click; native Minecraft remains the sole camera/mouse owner.

## Coverage and limitations
- `AimManualInputTests`: exact physical threshold, signed negative deltas, multi-tick pause expiry, settings edits, disable/reenable, epoch/Panic, foreign-thread refusal, real F7/profile roundtrip and 50,000 independent seeded input/setting traces, integrated into `IntegratedModuleCatalogTests` Core CI.
- Exact-head Core Java17/21 and Forge 1.8.9 Linux/Windows JDK8 native build/reobfuscation and existing chest/Netty/AABB probes remain required.
- **CI does not prove that Forge's client-tick event sees a fresh `MouseHelper.deltaX/deltaY` after mouse processing on all systems.** Original Minecraft 1.8.9 runtime acceptance must verify focus, GUI transitions, cursor grabbing, mouse sensitivity, manual movement versus aim feel, and FPS before enabling by default or merging. No claims about server acceptance or anticheat efficacy.

No force pushes, auto-merges, launcher work, or edits to the other chat's Slate/HUD/ClickGUI PRs.
