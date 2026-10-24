# GhostC R3.12 — preserve healthy profiles on runtime activation faults

The recovering profile loader previously caught **both** invalid profile
inputs and errors from applying a valid profile. If a module's onEnable
callback failed, an intact checksum-verified primary could be replaced by an
older sealed backup, creating a false .corrupt snapshot and silently
discarding the newer configuration.

ProfileStore now separates file/typed staging from live application.
Only read, checksum or typed-parse failures enter the backup recovery path.
A fault during application still triggers the existing in-memory rollback and
propagates to the caller; it cannot rotate on-disk config. A valid profile
uses the already staged plan rather than reading it a second time.

ConfigRecoveryTests exercises 128 repeatable callback failures against a
valid enabled primary and an older disabled backup. It checks propagated
failure, restored live state, unchanged SHA256-protected file bytes,
absence of false .corrupt evidence and successful retry.

Verification gates: Core Java 17/21, Forge 1.8.9 Linux/Windows JDK8,
JAR check and simulated installation/rollback both before and after merge.
No native Minecraft 1.8.9 visual, FPS or gameplay acceptance is implied.
