# GhostC R2.39 — consistent visible-only ESP admission

## Audited behavior

The Forge 1.8.9 world snapshot used by Target HUD and Aim Assist deliberately excludes `EntityPlayer.isInvisible()` from the visible-only population before firing an expensive `canEntityBeSeen` ray. The separate ESP renderer did not: it checked line of sight but admitted an invisible player if that ray was clear. Minecraft visibility metadata and line of sight are distinct signals, so the ESP `visible_only` option did not consistently mean visually present players.

A separate issue in the same renderer checked `player.getHealth()<=0` but omitted finite-health validation. NaN passed that condition, allowing malformed player data to consume nearest-N capacity and, depending on mode, enter downstream rendering. The shared snapshot already required finite, positive health.

## Change

- In Forge `PlayerEspRenderer`, reject a dead or local player and reject non-finite or non-positive health before computing distance, invoking LOS or considering nearest-N admission.
- For visible-only ESP, reject `player.isInvisible()` **after the existing nearest-N cutoff and before the LOS ray**; require the existing `canEntityBeSeen` check for the remaining players.
- Do not reject invisible players when the user intentionally disables `visible_only`: unrestricted/through-wall ESP remains permitted to show those entities. Do not change the default state or any persisted setting.
- Preserve distance, ID tie-breaks, dynamically configured nearest-N limits, geometry validity checks, GL painter order and other rendering styles.

## Deterministic evidence

- Extended `EspLosPruningTests` retains the original/reference versus optimized admission comparison and now exercises invisible players, NaN/infinite/depleted health, tied distances, repeated IDs and geometry exclusions in **5,000 randomized scenes**. The reference applies corrected visibility semantics after a ray; the optimized path excludes invisible candidates before a ray. Membership and ordering must match while the optimized path cannot perform more rays or geometry checks.
- A focused case demonstrates that clear LOS **does not imply visible** if `isInvisible` is true. It also checks that only the genuinely visible, finite-health player survives admission and that early invisibility rejection avoids an otherwise wasted ray.
- Forge wiring regression checks finite health before admission, nearest-N cutoff before invisibility, invisibility before LOS, and parity with the Target HUD/Aim snapshot `!p.isInvisible()` contract.
- Build gates: Java 17/21 core and Python wiring, Linux/Windows JDK8 Forge reobfuscation/JAR/provenance, Windows acceptance/rollback fixture. Verify the exact final PR head and then the merged `main` commit independently.

## Verification boundary

This is a specific *user-facing semantic correction* to visible-only ESP, not a performance benchmark or proof of in-game pixels. It intentionally changes ESP admission for genuinely invisible players where the old code would have drawn them in visible-only mode. The real licensed Minecraft 1.8.9 launch, visual comparison at different rendering styles, actual LOS-ray counts/FPS and 60-minute soak remain unverified. G1/G2/G4/G5 stay open.
