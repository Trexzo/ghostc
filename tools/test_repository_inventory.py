#!/usr/bin/env python3
"""Detect accidental deletion of established GhostC tests and milestone docs.

Runs before Java compilation, because a missing .java test otherwise fails
later with an opaque ClassNotFoundException. Never downloads repository data.
"""
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parent.parent
TESTS=(
    "CoreTests","HostRuntimeTests","UiTests","ServicesTests","FeatureTests",
    "EspSkeletonTests","EspTelemetryTests","FramePerformanceTests","LifecycleVisibilityTests",
    "EspHealthBarTests","EspHealthPlacementTests","EspNearestBudgetTests",
    "EspLosPruningTests","NearestSnapshotTests",
    "VisibilitySnapshotTests","SnapshotDemandTests","TargetHotPathTests",
    "SnapshotTelemetryTests","SnapshotModeTests","SnapshotRankIndexTests",
    "EspLineColorTests","EspCornerBoxTests","EspFilledBoxTests","EspBlendOrderTests",
    "EspDistanceFadeTests","EspDrawLimitTests",
    "TargetHudTextLayoutTests",
    "CombatTests","CombatIsolationTests","CombatMoreTests",
    "CoordinatorHygieneTests","RuntimeSafetyTests","FeatureFaultTests",
    "ViewportRuntimeTests","WorldLifecycleTests","ViewportHitTests",
    "GuiChromeTests","FaultPrivacyTests","CompactGuiTests","ReservedKeyTests",
    "HudPlacementTests","HudPreviewTests","HudEditorControlsTests",
    "StatusHudLayoutTests","DiagnosticsHudLayoutTests","HudTextFitTests",
    "HudWidgetViewportTests",
    "ConfigRecoveryTests","BackupRotationTests",
)
MUST_EXIST=(
    ".github/workflows/core-ci.yml",
    ".github/workflows/forge189-ci.yml",
    "forge189/src/main/java/dev/ghostfusion/forge189/ForgeEventAdapter.java",
    "forge189/src/main/java/dev/ghostfusion/forge189/ForgeCombatController.java",
    "forge189/src/main/java/dev/ghostfusion/forge189/ForgeFeatureDriver.java",
    "src/main/java/dev/ghostfusion/services/WorldIdentityTracker.java",
    "tools/test_forge_wiring.py",
    "tools/verify_forge_jar.py",
    "tools/forge_build_manifest.py",
    "tools/test_forge_build_manifest.py",
    "tools/Invoke-GhostCAcceptance.ps1",
)
def verify():
    workflow=(ROOT/".github/workflows/core-ci.yml").read_text(encoding="utf-8")
    issues=[]
    for test in TESTS:
        rel=Path("src/test/java/dev/ghostfusion/tests")/(test+".java")
        if not (ROOT/rel).is_file():
            issues.append(str(rel))
        if "dev.ghostfusion.tests."+test not in workflow:
            issues.append("core-ci.yml does not execute "+test)
    for name in MUST_EXIST:
        if not (ROOT/name).is_file():
            issues.append(name)
    for version in range(8,59):
        path=Path("docs")/("RUNTIME_R%02d.md"%version)
        if not (ROOT/path).is_file():
            issues.append(str(path))
    readme=(ROOT/"README.md").read_text(encoding="utf-8")
    for path in re.findall(r"\]\((docs/[^)]+\.md)\)",readme):
        if not (ROOT/path).is_file():
            issues.append(path+" (README reference)")
    if issues:
        raise AssertionError("Repository inventory incomplete: "+", ".join(sorted(set(issues))))
    print("REPOSITORY_INVENTORY_PASS tests=%d milestones=%d"%(len(TESTS),51))

if __name__=="__main__":
    try:
        verify()
    except (AssertionError,OSError) as exc:
        print(str(exc),file=sys.stderr)
        sys.exit(1)
