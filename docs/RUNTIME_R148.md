# GhostC R3.28 — shutdown-time input and feature admission fence

The owner HostRuntime already fenced reentrant tick/render/world lifecycle
calls and saves while \`close()\` executes. However, \`closed\` is
committed *after* module shutdown callbacks. During that interval,
\`key()\` could still attempt a keybind toggle on the sealed
ModuleRegistry, raising \`IllegalStateException\` inside an innocent
\`onDisable\` and incorrectly faulting that module. \`inputFocus()\`
could mutate diagnostics and focus state; \`runFeature()\` could execute
another still-enabled peer callback inside shutdown. A producer could
also consume an event sequence ID before the sealed queue denied it.

R3.28 makes the \`closing\` flag observable to asynchronous producers
and blocks these four paths during shutdown:

- \`key()\` and \`inputFocus()\` return without toggling module state,
  modifying focus, or counting a focus transition.
- \`runFeature()\` does not execute another guarded module callback.
- \`enqueue()\` refuses before reserving an event ID, including at
  \`Long.MAX_VALUE\`. The existing ingress seal retains final atomic
  admission authority for concurrent producers.

All four methods preserve their previous behavior in a live session;
normal in-game focus, keybind toggles, and local feature callbacks
are not changed. The existing owner-thread restrictions remain.

\`HostCloseInputFenceTests\` covers 192 independent shutdown sessions:
normal pre-close hotkeys and callbacks, one adversarial onDisable that
requests all four blocked operations while a peer remains enabled,
exhausted producer sequences, unchanged private diagnostics, exactly
once module cleanup, and inert behavior after close.

Automated verification uses Core Java 17/21, Forge Linux and Windows
JDK8 and the independently downloaded Windows ZIP acceptance suite.
Native Minecraft 1.8.9 GUI, rendering, gameplay input and FPS are
still **not verified in game**.
