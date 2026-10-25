# GhostC R3.24 — newest feature-fault warning and bounded history

GhostC's feature callback guard tracks at most 32 unresolved feature
failures and shows the most recent one in the HUD. Its insertion-ordered
\`LinkedHashMap\` previously used \`put(id, type)\` when an already known
module faulted again after being manually re-enabled. Replacing an
existing key **does not move its insertion slot**.

This produced two related problems:

- The HUD could keep showing an older peer fault while a retried feature
  had just failed again.
- Under the 32-entry cap, the newly retried feature could be evicted as
  though it were the oldest unresolved fault.

R3.24 removes an existing fault ID before reinserting it at the tail.
The eviction policy now uses the latest *occurrence* per feature, without
duplicating fault entries. Successful retry still removes the recovered
feature and reveals the next most recent unresolved fault. Read-only
warning access remains allocation-free until the history actually changes.

Anonymous RuntimeException subclasses can report an empty Java
\`getSimpleName()\`. They now receive the safe, nonempty label
\`RuntimeException\`; exception messages, private player/server data,
and stack traces are never stored in the warning.

## Automated proof

\`FeatureFaultTests.mostRecentFaultOrderingAndBound\` covers:

- Filling all 32 slots, retrying the oldest and proving it becomes newest.
- Four additional feature faults and precise least-recent eviction.
- 3,200 alternating re-fault cycles between two features; no duplicate
  keys, capped history and exact callback count.
- 16,000 unchanged per-frame HUD reads returning the same cached string.
- A successful retry revealing the next unresolved fault.
- An anonymous exception class and private-message redaction.

CI runs the entire headless Java 17/21 suite plus Forge 1.8.9
Linux/Windows JDK8 builds and the downloaded Windows artifact
Verify/Prepare/Rollback/tamper rehearsal. Actual native Minecraft
rendering, GUI, input and FPS are not asserted by these tests.
