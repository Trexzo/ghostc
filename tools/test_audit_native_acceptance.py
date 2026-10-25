#!/usr/bin/env python3
"""Synthetic operator-evidence fixtures; none launch Minecraft."""
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import audit_native_acceptance as audit

class AuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / 'ghostc-forge189-3.0.16.jar'
        self.jar.write_bytes(b'CI-only fake jar bytes; never run as a mod')
        self.sums = self.root / 'SHA256SUMS.txt'
        self.sha = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        self.sums.write_text(self.sha + '  ' + self.jar.name + '\n', encoding='ascii')
        self.manifest = self.root / 'GHOSTC_BUILD_MANIFEST.json'
        self.meta = {
            'schema': 1, 'status': audit.PACKAGE_STATUS,
            'source_revision': 'a'*40, 'source_revision_kind': 'CI_CHECKED_OUT_COMMIT',
            'platform': 'windows', 'artifact': self.jar.name,
            'artifact_sha256': self.sha, 'artifact_bytes': self.jar.stat().st_size,
            'minecraft': '1.8.9', 'java_class_major': 52,
            'verified_java_classes': 42,
        }
        self.save_meta()
        self.tx = 'b'*32
        self.report = self.root / 'acceptance-report.md'
        self.evidence = self.root / 'native-observations.json'
        self.rows = list(audit.REQUIRED_CHECKS)
        self.make_report([])
        self.observations = {
            'schema': 1, 'session_transaction': self.tx,
            'artifact_sha256': self.sha, 'minecraft': '1.8.9',
            'forge': '11.15.1.2318', 'java_major': 8, 'soak_minutes': 60,
            'observations': {name: True for name in audit.OBSERVATIONS},
        }
        self.save_evidence()

    def save_meta(self):
        self.manifest.write_text(json.dumps(self.meta), encoding='utf-8')

    def save_evidence(self):
        self.evidence.write_text(json.dumps(self.observations), encoding='utf-8')

    def make_report(self, selected, transaction=None):
        lines = ['# GhostC Windows acceptance report', '',
                 '**Status: UNVERIFIED IN-GAME until the checklist is completed by a human.**',
                 '', 'Installed artifact: '+self.jar.name,
                 'Verified SHA256: '+self.sha.upper(),
                 'Session transaction: '+(transaction or self.tx)]
        lines += ['- ['+('x' if item in selected else ' ')+'] '+item for item in self.rows]
        self.report.write_text('\n'.join(lines)+'\n', encoding='utf-8')

    def run_audit(self, report=True, evidence=False):
        return audit.audit(self.jar, self.manifest, self.sums,
                           self.report if report else None,
                           self.evidence if evidence else None)

    def test_required_checklist_matches_real_collect_source(self):
        collect = Path(__file__).with_name('Invoke-GhostCAcceptance.ps1')
        if not collect.is_file():
            return
        source = collect.read_text(encoding='utf-8')
        for title in audit.REQUIRED_CHECKS:
            self.assertIn("'- [ ] " + title + "'", source)

    def test_ci_only_never_claims_gameplay(self):
        result=self.run_audit(report=False)
        self.assertEqual(result['status'],'AWAITING_NATIVE_REPORT')
        self.assertNotIn('PASS',result['status'])

    def test_checklist_missing_or_incomplete_is_not_verified(self):
        self.assertEqual(self.run_audit()['status'],'INCOMPLETE_OPERATOR_CHECKLIST')
        self.make_report(self.rows[:-1])
        self.assertEqual(self.run_audit()['status'],'INCOMPLETE_OPERATOR_CHECKLIST')
        self.make_report(self.rows)
        self.assertEqual(self.run_audit()['status'],'AWAITING_MANUAL_OBSERVATIONS')

    def test_operator_attestation_is_review_not_certification(self):
        self.make_report(self.rows)
        result=self.run_audit(evidence=True)
        self.assertEqual(result['status'],'OPERATOR_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW')
        self.assertEqual(result['checked'],len(self.rows))
        self.assertEqual(result['missing_checks'],[])
        self.assertEqual(result['missing_observations'],[])
        self.assertNotIn('INGAME_PASS',result['status'])

    def test_wrong_session_and_hash_rejected(self):
        self.make_report(self.rows,transaction='c'*32)
        with self.assertRaises(ValueError):self.run_audit(evidence=True)
        self.make_report(self.rows)
        self.observations['artifact_sha256']='f'*64
        self.save_evidence()
        with self.assertRaises(ValueError):self.run_audit(evidence=True)

    def test_missing_observation_or_soak_duration_not_ready(self):
        self.make_report(self.rows)
        self.observations['observations']['clickgui']=False
        self.observations['soak_minutes']=25
        self.save_evidence()
        out=self.run_audit(evidence=True)
        self.assertEqual(out['status'],'INCOMPLETE_OPERATOR_CHECKLIST')
        self.assertIn('clickgui',out['missing_observations'])
        self.assertIn('soak',out['missing_observations'])

    def test_invalid_boolean_or_runtime_versions_fail(self):
        self.make_report(self.rows)
        cases=[('java_major',True),('forge','other'),('minecraft','1.21'),
               ('soak_minutes','60'),('session_transaction','../foo')]
        for key,value in cases:
            with self.subTest(key=key):
                saved=copy.deepcopy(self.observations)
                self.observations[key]=value
                self.save_evidence()
                with self.assertRaises(ValueError):self.run_audit(evidence=True)
                self.observations=saved
        self.observations['observations']['launcher']='true'
        self.save_evidence()
        with self.assertRaises(ValueError):self.run_audit(evidence=True)

    def test_tampered_package_or_checksum_cannot_be_attributed(self):
        self.jar.write_bytes(b'changed after build')
        with self.assertRaises(ValueError):self.run_audit()
        self.jar.write_bytes(b'CI-only fake jar bytes; never run as a mod')
        self.sums.write_text('0'*64+'  '+self.jar.name+'\n',encoding='ascii')
        with self.assertRaises(ValueError):self.run_audit()

    def test_mutated_manifest_metadata_is_rejected(self):
        for key,value in (('status','GAMEPLAY_VERIFIED'),('schema',True),
                          ('platform','other'),('artifact_bytes',False),
                          ('source_revision','f'*64),('artifact','different.jar'),
                          ('java_class_major',61)):
            with self.subTest(key=key):
                before=copy.deepcopy(self.meta)
                self.meta[key]=value
                self.save_meta()
                with self.assertRaises(ValueError):self.run_audit()
                self.meta=before

    def test_removed_or_duplicated_checkbox_is_rejected(self):
        self.rows.pop()
        self.make_report([])
        with self.assertRaises(ValueError):self.run_audit()
        self.rows.append(audit.REQUIRED_CHECKS[-1])
        self.rows.append(audit.REQUIRED_CHECKS[-1])
        self.make_report([])
        with self.assertRaises(ValueError):self.run_audit()

    def test_linked_report_and_size_caps(self):
        other=self.root/'outside.md'
        other.write_text(self.report.read_text(),encoding='utf-8')
        self.report.unlink()
        try:self.report.symlink_to(other)
        except (NotImplementedError,OSError):return
        with self.assertRaises(ValueError):self.run_audit()
        self.report.unlink()
        self.report.write_text('A'*262145,encoding='ascii')
        with self.assertRaises(ValueError):self.run_audit()

    def test_report_metadata_and_distinct_session_required(self):
        self.make_report(self.rows)
        original=self.report.read_text()
        for mutation in ('Verified SHA256: '+('0'*64),
                         'Session transaction: ../invalid',
                         'Installed artifact: other.jar'):
            lines=[line for line in original.splitlines() if not line.startswith(mutation.split(': ')[0]+': ')]
            lines.append(mutation)
            self.report.write_text('\n'.join(lines)+'\n',encoding='utf-8')
            with self.assertRaises(ValueError):self.run_audit()
        self.report.write_text(original+'Session transaction: '+self.tx+'\n')
        with self.assertRaises(ValueError):self.run_audit()

if __name__=='__main__':
    unittest.main(verbosity=2)
