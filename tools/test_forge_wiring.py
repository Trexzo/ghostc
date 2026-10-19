#!/usr/bin/env python3
"""Source-level Forge adapter contract tests to catch lost integration wiring.

These deliberately complement Java policy tests and JDK8 compilation; they do
not simulate Minecraft rendering, prove in-game behavior or parse third-party
classes.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
FORGE = ROOT / "forge189/src/main/java/dev/ghostfusion/forge189"


def content(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


class ForgeWiring(unittest.TestCase):
    def setUp(self):
        self.feature = (FORGE / "ForgeFeatureDriver.java").read_text(encoding="utf-8")
        self.gl = (FORGE / "RenderStateGuard.java").read_text(encoding="utf-8")
        self.adapter = (FORGE / "ForgeEventAdapter.java").read_text(encoding="utf-8")

    def test_each_feature_has_runtime_fault_gate(self):
        for stable_id in (
            "hud.keystrokes", "hud.cps", "hud.target", "hud.diagnostics"
        ):
            with self.subTest(stable_id=stable_id):
                self.assertIn(f'drawFeature("{stable_id}"', self.feature)
                self.assertNotIn(f'host.featureEnabled("{stable_id}")', self.feature)
        self.assertIn('host.runFeature("movement.sprint"', self.feature)
        self.assertIn('host.runFeature("render.esp"', self.feature)
        self.assertIn("host.runFeature(id,", self.feature)
        self.assertIn("RenderStateGuard.draw(painter)", self.feature)
        self.assertIn('private void sprintTick()', self.feature)

    def test_keystrokes_use_native_bindings_not_hardcoded_wasd(self):
        self.assertIn("GameSettings.getKeyDisplayString(code)", self.feature)
        for key in ("Forward","Back","Left","Right","Jump","Sneak"):
            with self.subTest(binding=key):
                pattern=rf'key\(keyName\(mc\.gameSettings\.keyBind{key}\)'
                self.assertRegex(self.feature, pattern)
        for name in ("W","A","S","D","SPACE","SHIFT"):
            self.assertNotIn(f'key("{name}"',self.feature)
        self.assertIn('getStringWidth(label)>width-5',self.feature)

    def test_separate_gl_guard_scopes_and_matrix_restoration(self):
        self.assertIn("GL11.GL_ALL_ATTRIB_BITS",self.gl)
        self.assertIn("GL11.GL_PROJECTION",self.gl)
        self.assertIn("GL11.GL_MODELVIEW",self.gl)
        self.assertGreaterEqual(self.gl.count("GL11.glPushMatrix()"),2)
        self.assertGreaterEqual(self.gl.count("GL11.glPopMatrix()"),2)
        self.assertIn("GL11.glPopAttrib()",self.gl)
        self.assertIn("finally",self.gl)
        self.assertEqual(self.feature.count('drawFeature("hud.'),4)

    def test_fault_warning_reaches_real_forge_overlay(self):
        self.assertIn("host.faultWarning()",self.adapter)
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        self.assertIn('public boolean runFeature(String id, Runnable callback)',host)
        self.assertIn('featureFaults.execute(id, callback)',host)
        self.assertIn('faultWarning = "Module disabled:',host)
        self.assertIn('diagnostics.record(Counter.FEATURE_FAULT)',host)

    def test_gui_viewport_wiring_not_regressed(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        self.assertIn("GuiLayout.calculate(width,height,ui.layout())",gui)
        self.assertIn("syncSearch(g)",gui)
        self.assertIn("resized.setText(previous)",gui)
        self.assertIn("resized.setFocused(focused)",gui)

    def test_forge_project_version_matches_mod_annotation(self):
        gradle=content("forge189/build.gradle")
        match=re.search(r"(?m)^version = '([0-9]+\.[0-9]+\.[0-9]+)'",gradle)
        self.assertIsNotNone(match)
        mod=(FORGE/"GhostCMod.java").read_text(encoding="utf-8")
        self.assertIn('version = "'+match.group(1)+'"',mod)
        info=content("forge189/src/main/resources/mcmod.info")
        self.assertIn('"version": "'+match.group(1)+'"',info)


if __name__ == "__main__":
    unittest.main(verbosity=2)
