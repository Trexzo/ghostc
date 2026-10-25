# GhostC R3.27 — no duplicate cleanup after event-induced lifecycle change

\`ModuleRegistry.dispatch\` invokes each enabled module's \`onEvent\` within
a per-module fault boundary. Its old \`catch(Throwable)\` always called
\`fault(entry, error, true)\`, which runs \`onDisable\`.

A module callback can legally call \`setEnabled(self,false)\` or
\`ModuleRegistry.shutdown()\` before returning. If it then throws,
the old fault boundary ran \`onDisable\` **again**, despite the module
already being disabled by the callback or the shutdown. If the first
\`onDisable\` had itself failed, the second cleanup could also hide the
original lifecycle failure behind the later event exception.

R3.27 checks the module's post-callback state before fault cleanup:

- Still \`ENABLED\` when \`onEvent\` throws: ordinary fault isolation
  remains unchanged, including exactly one \`onDisable\` and sanitized
  exception-type metadata.
- Already \`DISABLED\`: do not re-disable or spuriously mark it faulted.
- Already \`FAULTED\`: keep the original lifecycle fault and do not
  invoke its cleanup again.
- After a successful registry-wide shutdown: do not perform a second
  cleanup or restart callbacks for other modules.
- The existing snapshot, recursive dispatch, owner-thread and terminal
  shutdown guards remain unchanged.

\`EventPostLifecycleFaultTests\` covers 1,024 self-disable-then-throw
sessions, 1,024 shutdown-then-throw sessions, 512 failed-disable-then-
throw sessions, 512 ordinary event faults, fault reset/retry behavior,
healthy peers, exception privacy, and a post-disable JVM Error.

Offline verification includes Core Java 17/21, Forge 1.8.9 builds
on Linux and Windows JDK8, and independently downloaded Windows ZIP
Verify/Prepare/Rollback/tamper rehearsal. Real Minecraft GUI, render,
input and FPS remain unverified without an actual game run.
