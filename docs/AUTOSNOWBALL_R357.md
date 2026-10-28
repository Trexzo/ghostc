# GhostC R3.57 — AutoSnowball

**Stacked parent:** R3.56 AutoRod, draft PR #190 at exact CI-green SHA `d7f071406b2a9809b8503d7bc2031dd4730e76ac`. New branch contains only the increment above it. No merge/force push, no visuals, no Windows installation.

## Module

A real native **Combat → AutoSnowball** module in the existing F7 ClickGUI, default **OFF**.

On a **new physical attack press** (not an already-held attack, not a synthetic AutoClicker pulse), it may select a hotbar snowball, use Minecraft 1.8.9's real `PlayerControllerMP.sendUseItem`, then restore the originally selected hotbar slot on the following tick. Requires a living visible opposite player under the current native crosshair, exact-world entity identity, no GUI/focus loss, a valid attack/use binding, snowballs actually present in the hotbar and no manual item-use or hotbar key. Optional sword-before-selection and teammate protection.

Defaults: `cooldown_ticks` = **20** (10–100); `range` = **3.25** (2–4.5); `require_sword` = **true**; `ignore_teammates` = **true**. All values are F7 profile-persisted, F6 Panic disables the module, and old profiles leave it OFF.

## Native ownership and safe aborts

The owner-thread `AutoSnowballPolicy` mirrors the established epoch-fenced, staged **SELECT → USE → RESTORE** contract: no Native request is considered successful until its actual hotbar selection or vanilla right-click is committed. Failed use transitions to restoration, not automatic retries. World changes, disabled feature, focus/GUI loss, manual hotbar change, changed settings and stale output cannot silently reuse an old intent. Restoration is conditional on exact same world/player and the original owned selected snowball slot; any human hotbar selection wins without being overwritten.

The single Forge END-tick adapter executes the two hotbar controllers with **explicit multi-tick ownership**, in the order:
- If AutoSnowball already owns an in-flight selection/use/restoration, AutoRod is held and reset; finish Snowball ownership first.
- Otherwise AutoRod evaluates first. Its pending native transaction wins; AutoSnowball is reset/held while AutoRod owns the hotbar.
- If AutoRod has no transaction on this tick, AutoSnowball may evaluate its own fresh physical attack edge.
- While either has a transaction, the adapter disables GhostC optional AutoClicker/TriggerBot/BlockHit/Aim Assist camera writes. WTap pending reservation denies beginning a new AutoRod or AutoSnowball transaction; actual vanilla human keys remain untouched.

Both use `ActionCoordinator.Type.HOTBAR_SELECT` and `Type.USE`, with their **separate owner names** and real vanilla `PlayerControllerMP.updateController` + `sendUseItem`. No fabricated packets, Netty injection, raw motion writes, camera rotation spoofing or external key state injection. Anonymous F7 diagnostics: `ACTION_AUTOSNOWBALL_ATTEMPT`, `DISPATCHED`, `LOCAL_COMPLETE` and `CANCELLED` are only **local** native calls and do not imply a successful projectile hit/server acceptance.

## Verification

- `AutoSnowballTests`: 50,000 deterministic mixed owner frames; new-world held-key exclusion; exact one-use/one-restore cadence, cooldown, manual hotbar override, world/focus/GUI safety, WTap preemption, native veto after SELECT, F7 settings/profile/Panic and off-by-default.
- `NativeAutoSnowballTests`: mandatory real Minecraft 1.8.9 Java8 method signature/class linkage for `ItemSnowball`, `PlayerControllerMP.sendUseItem`, `updateController`; attached to ForgeGradle `check` on Linux and Windows.
- `tools/test_forge_wiring.py`: assert the real native controller is registered and that multi-tick AutoRod/Snowball handoff, original packet-free item use and output suppression cannot silently disappear.
- **Exact-head gates:** Core Java17/21, Forge 1.8.9 Linux/Windows Java8 build/reobfuscation, downloaded Windows artifact rehearsal. Real launched Minecraft, server projectile spawn, camera/input timing and PvP success remain **unverified until captured in a separately approved gameplay acceptance run**.

No changes to `main` or visual development branches; keep this PR in Draft.
