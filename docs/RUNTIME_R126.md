# GhostC R3.06 — disk-bounded configuration recovery staging

## Source finding

ConfigIntegrity.read already enforces 1 MiB on the *actual* input stream, not
only metadata size. But rotateVerifiedBackup previously did an unrestricted
Files.copy(primary,staged) after the size stat, before read(staged). A file
growing between the check and the copy could produce unbounded staged data.
restoreBackup likewise copied the entire backup into its staging file
before its size/read checks; its .corrupt evidence copy of a damaged primary
was also unrestricted. Large damaged files could waste disk and slow
startup recovery when trying to repair a small config.

## Fix

copyBounded streams from the source to a private temporary destination,
stopping at 1 MiB before writing any over-budget block. Backup rotation
returns false on oversized source and leaves the previous verified .bak
untouched. Restoration of an oversized backup throws IOException without
promoting or touching the primary; the temporary snapshot is always deleted.

If a verified, typed-valid backup exists, recovery of a damaged primary
still proceeds. A small damaged primary is copied to .corrupt via another
bounded temporary file and atomically promoted; if the damaged primary
exceeds the 1 MiB evidence budget, no .corrupt copy is created rather than
duplicating an arbitrarily large file. This does not change validation,
integrity seals, typed schema checks or ordinary healthy profile/UI loads.

## Regression

BackupRotationTests.boundedRecoveryCopies creates oversized 3 MiB+ files.
It tests direct rotation refusal with prior backup preserved, successful
recovery over huge corrupt primary, no oversized .corrupt evidence,
oversized backup rejection preserving original primary, direct restore
refusal, and zero leaked ghostc-backup-, ghostc-recover- or ghostc-corrupt-
temporary files. Existing verified and typed backup recovery tests remain
enabled. Python source wiring prohibits unrestricted copies for these paths.

Certification requires Core CI Java17/21 and Forge CI Linux/Windows JDK8
for the exact PR head, matching merge tree and green post-merge CI.
No in-game Minecraft acceptance is implied.
