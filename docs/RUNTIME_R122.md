# GhostC R3.02 — bounded Windows acceptance diagnostics

The opt-in Collect operation previously read an entire diagnostics file and accepted
arbitrarily long numeric fields. A damaged or oversized file could increase memory
consumption and generate unhelpfully large telemetry evidence.

The new bounded stream reader stops after 65,537 bytes, withholding files above
64 KiB even if they grow after initial inspection. Inputs with over 256 lines are
withheld. Numeric namespaced lines exceeding 120 characters are excluded, and
over 128 distinct valid counters cause a full withhold rather than partial results.
The existing Prepare timestamp gate, anonymized telemetry intent, per-transaction
report preservation and Rollback semantics remain unchanged.

Windows CI fake-profile tests inject a 70,000-digit input that must be withheld
without overwriting a previously owner-completed checklist. A second file combines
one valid counter, a 240-digit field and private text; only the short numeric
counter may appear in the telemetry sidecar. Source wiring locks the safeguards.

Passing Core Java17/21 and Forge Linux/Windows JDK8 workflows does not establish
in-game Minecraft 1.8.9, FPS or visual acceptance.
