# GhostC R1.7 — Compact-mode recovery and filtered module selection

## Regressions found in the real Forge source

**Compact GUI was always locked.** R1.6 introduced `GuiChrome.interactive()` with a fixed 445-pixel minimum, although `GuiLayout.calculate()` caps Compact at 390 pixels. Clicking WIDE → SMALL therefore opened the resize-only view permanently, with the layout button unavailable to switch back. The safe threshold now depends on actual sidebar and header control geometry: the HUD tab ends at `sidebar + 131` while the layout button begins at `width - 99`. Requiring `width >= sidebar + 230`, a sidebar of at least 70 pixels and at least 250 pixels of height keeps click regions disjoint in both Wide and Compact. Tiny viewports still display a noninteractive resize hint, with ESC/F7 usable.

**Invisible module settings stayed editable.** Search and category filtering previously left `selected` pointing to the old module even after that module had disappeared from visible results, so its settings/keybind remained in the lower panel and were still clickable. New `GuiSelection.reconcile` keeps a selection only while it belongs to the filtered modules; otherwise selects the first visible result or `null` if the filter returns no results. The real `GhostClickGui.entries()` now uses this reconciliation, resets settings scroll and key capture on changes, shows an explicit no-results message and refuses editing/scrolling settings without a visible selection.

The new behavior preserves typed search text, focus, themes, persisted normalized HUD coordinates and module settings, and does not introduce packet interception or donor code.

## Automated verification

- `CompactGuiTests` validates an actual 390-pixel Compact panel, the Wide → Compact → Wide control route, minimum supported geometry and thousands of seeded viewport/layout pairs.
- It also verifies category/search filter transitions, empty results, stable selected module IDs, and a 25,000-step seeded visible-selection state sequence.
- `tools/test_forge_wiring.py` confirms the real Forge GUI calls the selection reconciliation helper, null guards, and immediate filter invalidation. The old impossible 445px predicate is forbidden by the source contract.
- Inventory CI now covers 17 Java regression classes and the ten R08–R17 runtime guides. Both Java17/21 test matrices and Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation, JAR-integrity checks and fake-profile SHA-256 install/rollback are mandatory before merge.
- Version metadata and runtime HUD constant are synchronized to **1.7.0**.

## Runtime status

This release remains explicitly **unverified in-game**. GitHub Actions does not start a licensed Minecraft GUI session; visual placement, actual mouse interaction, low resolution behavior, ESP, combat and FPS/soak still require one consolidated real-world test. Use the checksum-gated Windows transaction described in `docs/RUNTIME_R10.md` and report only redacted evidence in GitHub Issues #2–6.
