# GhostC R3.05 — allocation-free hotkey lookup

## Source-level cost
Every HostRuntime.key() physical event previously called ModuleRegistry.all().
That method allocates an ArrayList snapshot and an unmodifiable wrapper
even when the key is unbound, repeated, released or suppressed by GUI focus.
Hotkey callbacks do not need a defensive copy merely to find the one
matching entry before comparing its state around the InputRouter action.

## Change
ModuleRegistry.boundToKey(int) validates owner thread and scans the stable
dispatchEntries array, already maintained whenever a module is registered.
Because Entry.keybind values are read live, subsequent rebinds and profile
loads are reflected instantly without a redundant key map. The all() API
remains a defensive snapshot for ordinary UI/profile callers.

HostRuntime.key switches to the lookup; focus, press/release edge gating,
module toggling, fault reset, reserved F7 policy, persisted module state,
and no-module behavior are unchanged.

## Regression
CoreTests.testAllocationFreeHotkeyLookup exercises 64 dynamically rebound
modules, a late registration, unbinding and owner-thread checks. 48,000
seeded lookups compare identity against the independent all() reference.
HostRuntimeTests.testLiveHotkeyRebinding sends 18,000 unbound key
down/up pairs, then verifies old/new key authority and saved module
state across a fresh host restart. Python source wiring pins the
nonallocating input path.

Certification requires exact-head Core Java17/21 and Forge Linux/Windows
JDK8 CI, identical tested/merged Git tree and passing post-merge workflows.
No native Minecraft input latency benchmark is claimed.
