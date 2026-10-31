#!/usr/bin/env python3
"""Verify a Forge GhostC mod JAR beyond a successful Gradle exit code.

No Minecraft runtime or third-party Python modules are required.
"""
import argparse
import json
import re
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

REQUIRED = (
    "mcmod.info",
    "dev/ghostfusion/forge189/GhostCMod.class",
    "dev/ghostfusion/forge189/ForgeEventAdapter.class",
    "dev/ghostfusion/forge189/ForgeCombatController.class",
    "dev/ghostfusion/forge189/GhostClickGui.class",
    "dev/ghostfusion/forge189/SlateSmoothFontRenderer.class",
    "dev/ghostfusion/ui/GuiSlateSmoothRaster.class",
    "dev/ghostfusion/ui/GuiSlateSmoothPlacement.class",
    "dev/ghostfusion/ui/GuiSlateSmoothTrialMetrics.class",
    "dev/ghostfusion/host/HostRuntime.class",
    "dev/ghostfusion/core/ModuleRegistry.class",
    "dev/ghostfusion/services/SessionServices.class",
    "dev/ghostfusion/features/PhysicalInputGate.class",
    "dev/ghostfusion/features/LiveCombatTargetGate.class",
    "dev/ghostfusion/features/EspRenderMode.class",
    "dev/ghostfusion/features/EspSkeletonGeometry.class",
    "dev/ghostfusion/features/EspCornerBoxGeometry.class",
    "dev/ghostfusion/features/EspFilledBoxGeometry.class",
    "dev/ghostfusion/features/EspHealthBarGeometry.class",
    "dev/ghostfusion/features/TargetHudTextLayout.class",
    "dev/ghostfusion/features/TargetHudSlateBarGeometry.class",
    "dev/ghostfusion/features/TargetHudSlateReadoutGeometry.class",
    "dev/ghostfusion/features/TargetHudDistanceText.class",
    "dev/ghostfusion/features/HudLiveTargetGate.class",
    "dev/ghostfusion/features/PingHudText.class",
    "dev/ghostfusion/forge189/ForgePingReadout.class",
    "dev/ghostfusion/ui/DiagnosticsHudLayout.class",
    "dev/ghostfusion/ui/HudTextFit.class",
    "dev/ghostfusion/ui/ModuleListSlateRowGeometry.class",
    "dev/ghostfusion/ui/ModuleListSlateMiniatureGeometry.class",
    "dev/ghostfusion/ui/TargetHudSlateMiniatureGeometry.class",
    "dev/ghostfusion/ui/GuiSliderDrag.class",
    "dev/ghostfusion/ui/HudDragCapture.class",
    "dev/ghostfusion/ui/HudEditorSlateCanvasGeometry.class",
    "dev/ghostfusion/ui/HudEditorSlateTabTypography.class",
    "dev/ghostfusion/ui/GuiSlateTypeScale.class",
    "dev/ghostfusion/ui/GuiSlateSettingsEmptyState.class",
    "dev/ghostfusion/ui/GuiSlateDetailTitleTooltip.class",
    "dev/ghostfusion/ui/GuiSettingKeyboard.class",
    "dev/ghostfusion/ui/GuiSearchFocus.class",
    "dev/ghostfusion/ui/GuiKeybindCapture.class",
    "dev/ghostfusion/ui/NumericSliderScale.class",
    "dev/ghostfusion/ui/HudWidgetViewport.class",
    "dev/ghostfusion/features/EspHealthBarGeometry$Side.class",
    "dev/ghostfusion/features/EspLineColor.class",
    "dev/ghostfusion/features/EspDistanceFade.class",
    "dev/ghostfusion/features/EspLivePaintGate.class",
    "dev/ghostfusion/features/EspTeamPolicy.class",
    "dev/ghostfusion/features/TargetHudTeamPin.class",
    "dev/ghostfusion/features/EspNearestBudget.class",
    "dev/ghostfusion/features/EspDrawLimit.class",
    "dev/ghostfusion/services/WorldSnapshotAdmission.class",
    "dev/ghostfusion/services/SnapshotDemand.class",
)


def verify(path: Path, expected_version: str) -> int:
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", expected_version):
        raise ValueError("Invalid expected version")
    if path.name != f"ghostc-forge189-{expected_version}.jar":
        raise ValueError("Incorrect artifact file name")
    with zipfile.ZipFile(path, "r") as jar:
        infos = jar.infolist()
        if len(infos) > 2500 or len(infos) < len(REQUIRED):
            raise ValueError("Unexpected archive entry count")
        names = [info.filename for info in infos]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entry")
        for info in infos:
            name = info.filename
            if (name.startswith("/") or "\\" in name or
                ".." in name.split("/") or not name or
                info.file_size > 50 * 1024 * 1024):
                raise ValueError("Unsafe or oversized archive entry")
        missing = set(REQUIRED) - set(names)
        if missing:
            raise ValueError("Required Forge entry missing: " + ", ".join(sorted(missing)))
        corrupt = jar.testzip()
        if corrupt:
            raise ValueError("CRC failure in " + corrupt)
        metadata = json.loads(jar.read("mcmod.info").decode("utf-8"))
        if not isinstance(metadata, list):
            raise ValueError("mcmod.info must be an array")
        records = [d for d in metadata if isinstance(d, dict) and d.get("modid") == "ghostc"]
        if len(records) != 1 or records[0].get("version") != expected_version:
            raise ValueError("GhostC modid/version mismatch")
        count = 0
        for name in names:
            if not name.endswith(".class"):
                continue
            content = jar.read(name)
            if len(content) < 8 or content[:4] != b"\xca\xfe\xba\xbe":
                raise ValueError("Invalid Java classfile: " + name)
            major = struct.unpack(">H", content[6:8])[0]
            if major != 52:
                raise ValueError("Not Java 8 bytecode: " + name)
            count += 1
        if count < 20:
            raise ValueError("Incomplete or unexpectedly small client class inventory")
    return count


def selftest() -> None:
    cls = b"\xca\xfe\xba\xbe\x00\x00\x00\x34" + b"\x00\x00"
    version = "1.0.0"
    with tempfile.TemporaryDirectory() as directory:
        jar = Path(directory) / ("ghostc-forge189-" + version + ".jar")
        def write_fixture(major=52, duplicate=False, unsafe=False):
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("mcmod.info", json.dumps([{"modid": "ghostc", "version": version}]))
                for name in REQUIRED:
                    if name.endswith(".class"):
                        archive.writestr(name, cls[:6] + struct.pack(">H", major) + cls[8:])
                for i in range(20):
                    archive.writestr(f"dev/ghostfusion/tests/T{i}.class", cls)
                if unsafe:
                    archive.writestr("../outside.txt", "bad")
                if duplicate:
                    archive.writestr(REQUIRED[1], cls)
        write_fixture()
        assert verify(jar, version) >= 20
        for opts in ({"major": 61}, {"unsafe": True}, {"duplicate": True}):
            write_fixture(**opts)
            try:
                verify(jar, version)
            except ValueError:
                continue
            raise AssertionError("Tampered archive passed verification: " + str(opts))
    print("FORGE_JAR_VERIFIER_SELFTEST_PASS")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--version")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()
    if args.selftest:
        selftest()
        return
    if args.jar is None or args.version is None:
        parser.error("--jar and --version are required")
    count = verify(args.jar, args.version)
    print(f"FORGE_JAR_VERIFY_PASS version={args.version} java8_classes={count}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, zipfile.BadZipFile, OSError, json.JSONDecodeError) as exc:
        print("FORGE_JAR_VERIFY_FAILED: " + str(exc), file=sys.stderr)
        sys.exit(1)
