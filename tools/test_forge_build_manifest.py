#!/usr/bin/env python3
"""Independent offline verification of forged/tampered CI artifact evidence."""
import copy
import json
import re
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

import forge_build_manifest as manifest
from verify_forge_jar import REQUIRED

REV = "a" * 40
VERSION = "2.33.0"
CLASS = b"\xca\xfe\xba\xbe\x00\x00\x00\x34\x00\x00"


def fixture(folder):
    jar = folder / ("ghostc-forge189-%s.jar" % VERSION)
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("mcmod.info",
                   json.dumps([{"modid": "ghostc", "version": VERSION}]))
        for name in REQUIRED:
            if name.endswith(".class"):
                z.writestr(name, CLASS)
        for i in range(20):
            z.writestr("dev/ghostfusion/tests/Fixture%d.class" % i, CLASS)
    sums = folder / "SHA256SUMS.txt"
    sums.write_text("%s  %s\n" % (manifest.digest(jar), jar.name), encoding="ascii")
    evidence = folder / "GHOSTC_BUILD_MANIFEST.json"
    return jar, sums, evidence


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.jar, self.sums, self.evidence = fixture(self.root)

    def generated(self):
        return manifest.create(self.jar, VERSION, REV, "linux",
                               self.evidence, self.sums)

    def tamper(self, key, value):
        record = json.loads(self.evidence.read_text(encoding="utf-8"))
        record[key] = value
        self.evidence.write_text(json.dumps(record), encoding="utf-8")

    def failed(self, action):
        with self.assertRaises((ValueError, OSError, TypeError, KeyError)):
            action()

    def test_verified_manifest_is_structured_and_not_runtime_claim(self):
        record = self.generated()
        self.assertEqual(record["source_revision"], REV)
        self.assertEqual(record["platform"], "linux")
        self.assertEqual(record["java_class_major"], 52)
        self.assertEqual(record["artifact_sha256"], manifest.digest(self.jar))
        self.assertGreaterEqual(record["verified_java_classes"], 20)
        self.assertEqual(record["status"], manifest.STATUS)
        self.assertNotIn("INGAME_PASS", record["status"])
        self.assertEqual(record, manifest.verify(self.jar,self.evidence,
                                                self.sums,REV,"linux"))

    def test_rejects_non_git_source_and_non_platform(self):
        for source in ("", "g"*40, "a"*39, "a"*41, "../main", "f"*64):
            self.failed(lambda: manifest.create(self.jar, VERSION, source,
                                                 "linux", self.evidence, self.sums))
        self.failed(lambda: manifest.create(self.jar, VERSION, REV,
                                             "mac", self.evidence, self.sums))
        self.assertFalse(self.evidence.exists())

    def test_invalid_checksum_does_not_create_manifest(self):
        self.sums.write_text("0"*64+"  "+self.jar.name+"\n",encoding="ascii")
        self.failed(self.generated)
        self.assertFalse(self.evidence.exists())

    def test_rejects_overlong_or_multiple_checksum_rows(self):
        self.sums.write_text("%s *%s\n" % (manifest.digest(self.jar),
                                             self.jar.name), encoding="ascii")
        self.failed(self.generated)
        self.sums.write_text("%s  %s\n%s  %s\n" % (
            manifest.digest(self.jar), self.jar.name,
            manifest.digest(self.jar), self.jar.name),encoding="ascii")
        self.failed(self.generated)
        self.assertFalse(self.evidence.exists())

    def test_detects_changed_jar_and_recomputed_checksum(self):
        self.generated()
        with self.jar.open("ab") as stream:
            stream.write(b"changed after manifest")
        self.failed(lambda: manifest.verify(self.jar,self.evidence,self.sums))
        self.sums.write_text("%s  %s\n" % (
            manifest.digest(self.jar), self.jar.name),encoding="ascii")
        self.failed(lambda: manifest.verify(self.jar,self.evidence,self.sums))

    def test_platform_source_and_schema_cannot_be_misattributed(self):
        self.generated()
        self.failed(lambda: manifest.verify(self.jar,self.evidence,
                                            self.sums,"b"*40,"linux"))
        self.failed(lambda: manifest.verify(self.jar,self.evidence,
                                            self.sums,REV,"windows"))
        for key,value in (
            ("status", "INGAME_PASS"),
            ("source_revision_kind", "CLAIMED_MAIN"),
            ("schema", 2), ("schema", True),
            ("platform", "other"), ("artifact_bytes", -1),
            ("java_class_major", 61), ("minecraft", "1.21"),
            ("verified_java_classes", 999), ("artifact_sha256", "0"*64),
            ("artifact", "../other.jar"), ("source_revision", "f"*39),
        ):
            with self.subTest(key=key):
                original = self.evidence.read_text(encoding="utf-8")
                self.tamper(key,value)
                self.failed(lambda: manifest.verify(self.jar,self.evidence,
                                                    self.sums,REV,"linux"))
                self.evidence.write_text(original,encoding="utf-8")

    def test_extra_and_missing_fields_fail_closed(self):
        self.generated()
        orig=self.evidence.read_text(encoding="utf-8")
        self.tamper("claimed_ingame_acceptance", True)
        self.failed(lambda: manifest.verify(self.jar,self.evidence,self.sums))
        self.evidence.write_text(orig,encoding="utf-8")
        record=json.loads(orig)
        del record["artifact_bytes"]
        self.evidence.write_text(json.dumps(record),encoding="utf-8")
        self.failed(lambda: manifest.verify(self.jar,self.evidence,self.sums))

    def test_manifest_must_live_alongside_jar(self):
        output = self.root / "nested" / "GHOSTC_BUILD_MANIFEST.json"
        output.parent.mkdir()
        self.failed(lambda: manifest.create(self.jar,VERSION,REV,
                                             "linux",output,self.sums))
        self.assertFalse(output.exists())

    def test_jar_bytecode_and_mcmod_info_also_verified(self):
        self.generated()
        with zipfile.ZipFile(self.jar,"w") as z:
            z.writestr("mcmod.info",json.dumps([{"modid":"ghostc",
                                                "version":"0.0.0"}]))
            for name in REQUIRED:
                if name.endswith(".class"):
                    z.writestr(name, CLASS)
            for i in range(20):
                z.writestr("dev/ghostfusion/tests/Fixture%d.class"%i,CLASS)
        self.sums.write_text("%s  %s\n" % (
            manifest.digest(self.jar),self.jar.name),encoding="ascii")
        self.failed(lambda: manifest.create(self.jar,VERSION,REV,
                                             "linux",self.evidence,self.sums))

if __name__=="__main__":
    unittest.main(verbosity=2)
