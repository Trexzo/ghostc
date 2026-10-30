# GhostC R3.64 — native fishing-hook provenance and acceptance evidence

**Parent:** CI-green R3.63 #197 at `d6d204917ac4cd6f4fd888c79e8af47cfb3d43e8`. Independent GitHub-only stacked draft; no changes to `main`, other chats' HUD/ClickGUI/ArrayList work or any installed client.

## Audited native fact and failure mode

Original vanilla Minecraft 1.8.9 `ItemFishingRod.onItemRightClick` creates the `EntityFishHook` entity **only when `!worldIn.isRemote`**; the multiplayer client observes its own hook later. This makes the client's exact new-world hook a potentially useful **client-observed remote-world event**, stronger than just the return value of `sendUseItem`, but **not** an acknowledged target hit, packet receipt, damage, or a definitive proof the intended server action succeeded.

R3.62–R3.63 sampled `rodPlayer.fishEntity != null && !isDead` after a local use. That is too broad: a lingering old hook, hook reference from another world or a stale/unregistered entity object could be credited to the new cast.

Original code evidence:
- [Vanilla MCP 1.8.9 ItemFishingRod.onItemRightClick](https://mc-packet-reference.netlify.app/1.8.9/source/net/minecraft/item/itemfishingrod/) — see `if (!worldIn.isRemote)` on spawn branch
- [Vanilla MCP 1.8.9 EntityFishHook](https://mc-packet-reference.netlify.app/source/net/minecraft/entity/projectile/entityfishhook/) — `angler` is the owning player, with a `worldObj` and exact entity identity

## Implementation

New pure owner-thread `ProjectileHookProvenance` records the exact **pre-native-use** `fishEntity` object, original player and world identity. After the original Minecraft `sendUseItem` returns and the projectile policy/inventory transaction commits, a separate read-only observation window remains armed, without delaying hotbar restoration or any manual player action.

The next client END-tick observations count `LOCAL_HOOK` **only if**:
- same exact Minecraft player object, world object and session generation;
- the new `fishEntity` is not null or dead and **not** the pre-use hook object;
- the native `EntityFishHook.angler` is that exact original local player;
- the hook's `worldObj` is that exact original world;
- the exact hook object is found in `rodWorld.getEntityByID(hook.getEntityId())`; matching numerical ID alone is never enough.

Everything else remains pending up to the existing **eight END-tick observation window**, then expires or invalidates when the session changes. No packet capture, hook spawning, camera/input/inventory writes, automatic retry, target acquisition or extra use has been added. Manual hotbar control and R3.58–R3.63 combat priority remain unchanged. Cached references are erased on completion/expiry and world/player transitions, and no identity is exported.

Existing diagnostic counter `PROJECTILE_ROD_LOCAL_HOOK` means **registered, live hook observed in the local world**. It does not mean server hit or a remotely confirmed transaction. This result can also be confounded by an unrelated manual rod cast during the observation window; without authenticated transaction correlation, **do not claim causal proof**.

## Tests

- New `ProjectileHookProvenanceTests`: **80,000 deterministic cases** for old hook baseline, foreign angler, cross-world player/hook, dead/absent hook and different world-registry object; same-tick delayed-observation denial, eight-tick expiry and owner-thread enforcement.
- Mandatory Forge Java8 `NativeProjectileHookProvenanceTests`: original Minecraft 1.8.9 `EntityPlayer.fishEntity`, `EntityFishHook.angler`, `Entity.worldObj`, `Entity.isDead`, `getEntityId`, `World.getEntityByID` method/field linkage, with normal Java owner-policy checks.
- Extended `tools/test_forge_wiring.py` verifies the original **pre-use** hook is sampled before `sendUseItem`, passive observer is armed afterward, provenance is read-only and requires angler/world/registry object identity; mandatory Core and Forge checks cannot silently drop the new tests.
- Require exact-head Java17/21 Core and JDK8 Linux/Windows Forge build/reobfuscation/native probes plus downloaded-artifact install/rollback rehearsal. These headless gates do **not** run original Minecraft, join a server or prove actual gameplay.

## Separate original Minecraft gameplay acceptance (NOT performed here)

On an authorized test server with actual original Minecraft 1.8.9, collect a redacted local acceptance record with exact candidate Git SHA and JAR hash. Verify: no old hooks credited after casting; new same-world hooks can be observed after the native rod use; target switch, dead/different-world hook and world swap are rejected; restoration and physical attack-release handoff work with no extra synthetic click; no false claims of server hit or damage. Run alongside survival snowball depletion and manual key/wheel override tests from R3.63. Any result without actual launched client evidence must be labeled **headless only / unverified**.

Keep PR Draft; do not merge, force-push, install locally, or modify unrelated branches.
