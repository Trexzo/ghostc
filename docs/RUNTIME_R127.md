# GhostC R3.07 — fixed-memory physical hotkey state

R3.05 removed registry snapshot allocations during keyboard events, but
InputRouter still stored held key-downs as HashSet<Integer>. Every new key
could allocate a boxed Integer and a hash-table node, even when the key
was unbound or its event was suppressed by a focused GUI or chat. A key
held while typing must nevertheless stay tracked until release to prevent
a hidden module toggle when focus returns.

The router now uses exactly 1,024 primitive longs (8 KiB) to cover the
validated 0–65,535 keycode range. A first down edge sets its bit; repeat
downs are inert, and key release clears it. The existing owner-thread
gate and ModuleRegistry.validateKey check run before indexing the bitmap.
Zero and reserved F7 do not change pressed state. Focus/world changes
still clear every pressed key; there are no new callbacks, module binds,
config fields or user-visible option changes.

CoreTests.testPrimitiveKeyboardBitmap replays 150,000 deterministic
events against the former independent HashSet reference, checking eight
registered modules across the full key domain. The replay covers
GUI/chat suppression, repeated down edges, releases, forced focus clear,
reserved keys, word boundaries (63/64, 127/128 and 65535), invalid
negative/out-of-range keys, and off-owner clear rejection. Existing
Core and ReservedKey regressions remain in place; Python source
wiring pins primitive storage and validation order.

Certification needs exact-head Core Java17/21 and Forge Linux/Windows
JDK8, identical tested/merged Git tree, and green post-merge workflows.
Native Minecraft input responsiveness is not asserted from headless CI.
