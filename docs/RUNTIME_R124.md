# GhostC R3.04 — primitive CPS timestamp rings

## Why

R3.03 bounded the number of physical mouse events retained per button to 4,096.
The remaining ArrayDeque<Long> representation still boxed every accepted
mouse-down timestamp into a new Long object. Under heavy input streams this
produced avoidable transient allocation and garbage-collector load on the
Minecraft client thread, even though the CPS readout only needs a bounded
one-second history.

## Implementation

Replace each timestamp deque with a fixed `long[4096]` ring and private
head/count indices. Expire oldest samples using the same unsigned difference
against the one-second window. On a full ring, evict exactly the oldest
entry before writing a new event at the tail. Both pointers wrap in O(1)
without copying values, allocating objects or scanning other retained
timestamps. `clear` resets indices without zeroing all 8,192 slots.

Physical down-edge detection, focus rejection, unknown mouse buttons,
owner-thread validation, signed-clock-order rejection, capped count and
cross-world reset retain their previous behavior. Combat pulse generation
and native Minecraft click handling are unchanged.

## Verification

Existing 30,000 left/right click-flood tests, 72 dense seeded replay cases,
and wide unsigned-clock expiry regressions remain enabled.
FeatureTests.cpsPrimitiveRingOracle independently models the previous
boxed deque semantics across 160,000 deterministic events, including
full-ring wraparound, one-second jumps, invalid mouse buttons, focus
suppression and multiple clears. Source wiring forbids a boxed deque
in the production CPS path.

Certification requires exact-head Core CI Java17/21 and Forge
Linux/Windows JDK8, tested/merged source-tree identity and successful
postmerge workflows. No native Minecraft game launch, FPS or actual
GC benchmark is claimed.
