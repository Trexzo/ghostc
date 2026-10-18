# GhostC G3 / R0.4 — shared service contracts

**Status: Java 8-compatible owner-thread services with offline test coverage, plus read-only player snapshots from Forge. No packet interception, combat automation, aim mutation or network injection is implemented.** Always distinguish a CI-tested contract from a functioning Minecraft feature.

## Service ownership / invariants

| Service | Responsibility | Acceptance boundary |
| --- | --- | --- |
| `WorldSnapshot` | Immutable player/entity data, immutable collection, unique IDs, generation/tick markers | Forge adapter may copy live data; no Minecraft classes enter the core |
| `TargetSelector` | Explicit alive/visible/player/range filter; distance, health or ID sorting, ID tie-break | Selection is pure, no attack, no targeting hook |
| `RotationCoordinator` | Scope CAMERA or OUTBOUND; priority arbitration, request owner, generation, exclusive tick expiry, cancellation | Returns **intent only**. Does not alter camera, input, Netty packets or game values |
| `ActionCoordinator` | Exclusive ATTACK/USE/SWING/HOTBAR_SELECT intent resolution at specific PRE_INPUT/SIMULATE/OUTPUT phase | No automatic key or attack injection; future adapter must explicitly consume and validate |
| `PacketCoordinator` | Defensive-copied byte payloads, bounded queue, max size 32 KiB, completion callback exactly once, cancel, stale-session, transport-failure outcomes | Transport-independent test abstraction, **not connected to Minecraft networking/Netty** |
| `InventoryCoordinator` | Bounded serial queue, one active transaction, priority then ID, explicit ID+generation acknowledgment, expiry | No inventory clicks, no inferred success |
| `SessionServices` | Single owner thread, generation fencing, tick clock, world enter/leave/disconnect and resource teardown | Host owns service lifecycle; reconnect invalidates all previous intents |

**Threading:** Only `HostRuntime`'s owner thread may mutate coordinators or evaluate decisions. Packet callback errors are contained; accepted intent always gets one terminal result on cancellation/reset/shutdown/flush. These are contracts for future adapters rather than behavior claims.

**Phase order:** At Forge END client tick: set world presence -> host tick/generation advance -> bound event ingress -> module CLIENT_TICK -> read-only snapshot publish. Modules requesting new rotation/input actions on this phase may be resolved later by an explicitly built adapter. Direct packet submission requires the owner thread and explicit transport; future raw Netty callback/promise bridging must be tested before integration.

**Epoch semantics:** Every world presence transition rotates generation. Disconnect always rotates it again to invalidate queued work. World snapshots bind both generation and owner tick and old epochs are rejected. No mutation should survive a disconnected session.

**Validation gates:** Core CI runs deterministic replay and failure tests in `ServicesTests` on JDK17/21 with Java 8 bytecode. Forge CI checks new classes in the reobfuscated mod JAR. Neither CI lane exercises real Minecraft packets, physical actions, packet promises, camera rotations or PvP effectiveness.

## Follow-up work
1. Implement an explicit Forge-side rotation/input consumer with authoritative phase ordering and operation ownership; test only within a controlled/local Minecraft environment.
2. Add a safe Netty binding that preserves original outbound `ChannelPromise` completion on every cancellation/failure; no hook is installed in R0.4.
3. Integrate inventory ACK semantics against actual click/transaction IDs rather than blindly assuming success.
4. Add listener lifecycle (register once, unregister on teardown) and real reconnect/packet tests, replay harness for actual game events, measured in-game behavior.
5. Continue Issue #5 only once this contract's core CI and Forge compilation are green. Keep Issue #4 open for actual service-adapter acceptance.
