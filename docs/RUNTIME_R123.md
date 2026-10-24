# GhostC R3.03 — bounded physical CPS event history

## Problem

CpsMeter used two ArrayDeque<Long> histories of all physical mouse-down
edges observed within the most recent second. It correctly evicted expired
edges, but had no upper bound for events sharing or clustering inside a
single second. An abnormal source of repeated down/up mouse events could
allocate an arbitrary number of Long instances even if the CPS HUD
only needed a compact numeric readout. This also happened when the HUD
module was disabled, because the event collector remains wired.

## Change

Keep up to MAX_WINDOW_CLICKS=4096 physical down edges for each of the
left and right buttons. Before admitting a new edge, evict the oldest
one only if its queue is already at capacity; the newest events are
always retained because they are the ones that will remain inside
the rolling one-second window longest. The integer readout saturates
at 4096; all normal physical click rates remain exact.

The elapsed-time eviction, nondecreasing owner-thread clock, focus
suppression, down-edge de-duplication, world/disconnect reset, and
visual layout remain unchanged. This does not create synthetic click
events or change any combat/movement scheduling.

## Regression

FeatureTests.cpsBoundedFlood tests 30,000 left and 30,000 right mouse
down edges with release between each, confirms the exact independent
saturation boundary, checks that all retained events expire at their
correct one-second boundaries, verifies accurate ordinary counting
after saturation, focus suppression and reset. Another 72 seeded
burst/expiry cycles check rolling admissions at varying densities.
Python Forge wiring pins the capped recording path and the existing
physical mouse event bridge.

Certification requires exact-head Core Java17/21 and Forge
Linux/Windows JDK8, merged Git tree parity and postmerge Core/Forge
success. Native Minecraft frame/CPS acceptance remains untested.
