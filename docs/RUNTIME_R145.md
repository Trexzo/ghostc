# GhostC R3.25 — ingress drain reentry and concurrent consumer fence

The asynchronous event ingress is a bounded, thread-safe producer queue,
consumed by the client owner tick. When a module's \`onEvent\` callback
called \`drain()\` or \`drainAfter()\` on the same queue, the nested call
previously executed \`queue.poll()\` **before** ModuleRegistry's dispatch
reentry protection threw. That silently removed a second event even
though the nested module dispatch was refused.

A separate owner thread with a second ModuleRegistry could also begin
draining while the original consumer was inside its callback. The
\`queued\` count remained bounded, but no exclusive consumer authority
prevented that second registry from stealing FIFO work.

R3.25 introduces a lightweight atomic drain-acquisition guard:

- Refuse any recursive or overlapping consumer **before** polling.
- Retain producer submission during a valid drain, subject to the
  existing sealed, capacity and sequence/session fences.
- Always release the guard in \`finally\`, even if target dispatch fails.
- Do not increment producer-rejection metrics for a refused consumer.
- Preserve existing outer drain budget, stale-event filtering and FIFO.

\`IngressDrainReentryTests\` performs 2,048 alternating recursive
\`drain\`/\`drainAfter\` sessions, checking all original events arrive
in order, no module is spuriously faulted, and the guard resets on
successful exit. A latch-synchronized adversarial second owner tests
cross-thread dequeue exclusion, without sleeps or race assumptions.
An exceptional target dispatch confirms that later queued events
remain drainable on a healthy registry.

CI gates are Core Java 17/21, Forge 1.8.9 Linux/Windows JDK8,
independent downloaded Windows ZIP acceptance, exact tested/merged Git
trees and post-merge runs. Real Minecraft 1.8.9 GUI/render/input/FPS
remain unverified by headless CI.
