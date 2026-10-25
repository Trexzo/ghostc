# GhostC R3.23 — physical input rebinding release fence

GhostC's optional local combat helpers require an existing **physical**
attack hold. The pure Java \`PhysicalInputGate\` already requires an
actual key-up after a world/session change, loss of focus, an open
GUI or explicit reset; it does not intercept vanilla input.

However, the attack/use keybind codes are mutable while Minecraft is
running. Previously, an already-admitted hold stayed admitted even if
the attack key or use-item key was rebound without an observed release.
In particular, changing the use key from a conflicting value to a
non-conflicting one could resume synthetic input without a fresh press.

R3.23 tracks the last attack and use binding **as an additional input
admission epoch**. Whenever either changes, the gate revokes the
current hold and requires a subsequent physical release while
in-game/focused before any new synthetic pulse can be admitted.
This also covers transitions to/from unbound attack, reserved F7
or an attack/use collision. If the key is already released when the
binding changes, the release is observed normally on that call.

The original world/focus/GUI and owner-thread fences are preserved.
The existing ForgeCombatController continues to supply both live
keybinding codes on every tick; false admission automatically
disarms the existing FixedClickScheduler. No changes to Minecraft's
normal key handling, direct attack interception, network packets or
anti-cheat behavior are introduced.

\`PhysicalInputBindingTests\` exercises 4,096 independent remapping
sequences, focus/GUI/world/reset transitions, reserved F7, wrong
thread access, and the actual fixed-rate click scheduler's lack of
catch-up pulses after a remap. Existing reserved F7 tests remain.

CI scope: Core Java 17/21; Forge 1.8.9 Linux and Windows JDK8
builds; downloaded Windows artifact verification/reversible
installation. These are **offline tests**; a real Minecraft native
launch, GUI inspection, FPS and gameplay soak remain unverified.
