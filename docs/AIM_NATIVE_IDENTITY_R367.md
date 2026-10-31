# GhostC R3.67 — native Aim Assist identity and camera ownership

**Parent:** R3.66 draft PR #200 exact commit `1d6aa7c2a953065d3701ab4473df2e636c43c8b7`. Combat-only stacked draft. No main, visual-branch, installation or release modifications.

## Finding and implementation

R3.51 AimTargetSwitchPolicy keyed both committed and pending camera targets by UUID and numeric Minecraft entity ID. A replaced EntityPlayer object with the **same UUID and entity ID** could inherit an earlier camera decision or switch dwell. The R3.51 hard AimTargetLock used UUID alone for a retained target hint.

- The new native `considerNative/appliedNative` path requires exact Minecraft entity **object reference and world reference** alongside UUID+ID. A replaced object must earn its own positive `switch_delay_ticks` window, without camera output during the dwell. An arbitration loss does not commit a camera identity. Previous pure `consider/applied` remain for compatibility.
- `AimTargetLock.sameNativeIdentity/appliedNative` rejects stale hard-lock hints after a same-UUID/ID replacement or world swap. An actual new target still goes through native snapshot, range, visibility and source-pose admission.
- The Forge controller checks the original player/world, same live registry entity and source/target pose again **immediately before** native `rotationYaw/rotationPitch` writes; on failure it cancels prepared Aim output.
- `switch_delay_ticks=0` intentionally remains immediate and unchanged. TriggerBot's native crosshair camera yield, manual mouse pause, real physical attack/use priority and single action/rotation owners remain intact. No new input packet or targeting capability is introduced.

## Proof and limitations

`AimNativeIdentityTests`: 80,000 seeded independent simulated object/world replacement events, hard-lock negatives, denied camera commit and reset/owner thread constraints. `NativeAimIdentityTests`: original Minecraft 1.8.9 Entity/World/camera API linkage under Java8 ForgeGradle. CI must run Core Java17/21, Forge Linux/Windows Java8 check and reobfuscation plus Windows artifact rehearsal. Forge source wiring enforces final-gate ordering.

**Not established by CI:** a launched Minecraft session, local physical mouse event timing, live target-switch feel or server combat outcomes. Before merge, test TriggerBot loss and reacquisition, same-UUID entity replacement, manual mouse override/resume, nearest-player switching and F6 Panic on an authorized original Minecraft 1.8.9 instance. Keep draft until verified.
