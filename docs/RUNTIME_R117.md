# GhostC R2.97 — idempotent sealed profile/UI saves

## Observed source-level issue

ClickGUI onGuiClosed and HostRuntime.close issue persistence even when
no module, HUD position, theme, layout or keybind changed. Both ProfileStore
and UiSettingsStore previously wrote a fresh Properties.store file every time,
rotated the previous primary into .bak, and rewrote the primary. Properties.store
also emits timestamp comments, so repeated unchanged closes cause unnecessary
disk churn and replace backup timestamps without any user state change.

## New save gate

ConfigIntegrity.unchangedWithRecoveryBackup validates the requested sealed
properties and verifies two independent durable files: an exact, typed-valid,
checksum-sealed primary and an existing, checksum-sealed, typed-valid recovery
backup. The backup may intentionally contain an earlier different state.
Only when all checks pass may save() return without touching either file.

After the first save there is intentionally no backup. The second save,
even when unchanged, runs the existing rotation/atomic-write path to establish
a verified recovery candidate. A changed profile/HUD value likewise preserves
verified backup rotation. Malformed or unsealed primaries, missing/invalid
backups, checksum mismatch, and sealed-but-typed-invalid backups all fall back
to the existing safe save path, which cannot promote an unverified primary
into a good backup. Existing recovery and schema-1 legacy migration remain
unchanged. No profile or UI fields, defaults or display behavior changed.

## Regression and scope

BackupRotationTests.idempotentSaves checks a fresh primary-only save, backup
bootstrap on an identical second save, 512 unchanged profile and 512 unchanged
UI saves preserving exact primary/backup bytes and pinned modified timestamps,
a legitimate settings mutation with correct old-primary backup, corrupted
backup self-repair, typed-invalid-but-sealed backup repair, and damaged primary
repair without overwriting known-good backup. Existing recovery, duplicate
field and tampering test suites remain enabled.

Merge gate: Core CI Java17/21 and Forge CI Linux/Windows JDK8 on exact PR head,
test-tree equality at merge, and green post-merge Core/Forge. These are offline
and packaging tests, not native Minecraft UI acceptance.
