# GhostC R2.50 — asynchronous ingress session fencing

## Source-audited defect

`HostRuntime.enqueue` accepts immutable `GameEvent` records from other threads, and a fixed-capacity `BoundedIngress` holds them until the client owner thread's tick. Although the runtime resets world session services on world switch/disconnect, its queue previously carried old submitted events into a new world. A queued `CLIENT_TICK` or `RENDER_2D` might reach currently enabled modules in a later session.

## Implementation

- The existing `AtomicLong sequence` stamps submitted events before queue admission. Every world presence transition and terminal disconnect records the latest assigned sequence as an **exclusive stale-event cutoff**.
- `BoundedIngress.drainAfter` dispatches only events with a higher sequence. It scans at most the pre-existing 128 dequeue slots per client tick, *including* dropped old-session messages, so a full old queue drains over at most two ticks rather than causing an unbounded synchronous cleanup spike.
- In-flight producer reservations cannot bypass the fence merely by publishing after the world switch; the event's sequence was already assigned. A new event stamped after the transition is admitted.
- The old `BoundedIngress.drain(target,limit)` remains available with unconditional semantics for independent clients; no change to capacity, user keybinds, Render/Forge hooks, service action scheduling, or GUI.
- The ordering contract is based on **when the event is stamped**, not when outside Minecraft itself changes worlds. An event stamped after the boundary is considered new-session; other producers must not claim older-world origin after that point.

## Regression/verification

- Java8 host test probes cover menu-to-world entry, world leave+rejoin, disconnect received after a world leave, and ordinary same-session deliveries.
- Bounded 256-event stale backlog drains over two 128-slot ticks with no stale dispatch, then admits fresh work.
- Direct queue replay covers old-stamped event published *behind* a newer one, enforcing sequence filtering independent of FIFO order.
- Forge source-wiring checks assert both HostRuntime boundary updates and the queue's bounded per-tick filter. Core Java17/21 and Linux/Windows Forge JDK8, JAR provenance, and simulated Windows install/rollback are release gates.
- No real Minecraft 1.8.9 session, visual parity, measured FPS or extended stability is claimed. G1–G5 remain open pending runtime acceptance.
