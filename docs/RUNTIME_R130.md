# GhostC R3.10 — reject symbolic-link aliases in config recovery

## Source-level issue

The R3.06 bounded recovery copy stopped oversized sources, but a symbolic
link named `default.properties.bak` or `ui.properties.bak` was still
accepted. The recovery loader followed the link and could import a
checksum-valid configuration located outside the expected directory.
Likewise, evidence copying from a symbolic-link primary could read a
different on-disk file into `.corrupt`, unexpectedly duplicating bytes.

## Change

Recovery refuses a symlink primary and requires the backup to be a
regular file without following symbolic links *before* creating staging
files. The shared bounded copy routine additionally opens source streams
with NOFOLLOW_LINKS, so a link substituted between an initial type check
and stream open is rejected instead of followed. Backup rotation already
checks that its primary is a regular non-symlink file; it also receives
the hardened bounded copy automatically.

The established maximum 1 MiB actual-byte budget, SHA256 integrity seal,
typed validation, temporary-file cleanup, backup preservation and atomic
promotion remain unchanged. Existing legitimate ordinary-file config
recovery still works. This reduces symlink exposure; it does not claim a
complete filesystem race-proof transaction against adversarial renames.

## Regression

BackupRotationTests.symlinkRecoveryFences uses a valid sealed file in a
separate temporary folder. It proves recovery refuses a linked backup
without importing external bytes or altering the corrupt primary.
It also confirms a linked primary cannot be copied to `.corrupt`, and
that the trusted ordinary backup, external file and temporary-file
inventory remain unchanged. The existing valid recovery and 3 MiB
bounded-copy tests still run. Forge source wiring pins NOFOLLOW_LINKS
and both admission checks.

Exact-head Core Java17/21, Forge Linux/Windows JDK8 CI, matching
tested/merged Git tree and postmerge workflows are required.
Native Minecraft acceptance is not implied.
