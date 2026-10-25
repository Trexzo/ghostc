#!/usr/bin/env python3
"""Read-only GhostC native acceptance *readiness* audit.

Checks local JAR identity, CI metadata and operator-created evidence consistency.
Neither CI nor this parser can verify that Minecraft was actually launched.
No logs/screenshots are read, copied, uploaded or echoed.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

PACKAGE_STATUS = 'CI_PACKAGE_ONLY_NOT_TESTED_IN_MINECRAFT'
REVISION = re.compile(r'[0-9a-f]{40}\Z')
DIGEST = re.compile(r'[0-9a-fA-F]{64}\Z')
TRANSACTION = re.compile(r'[0-9a-f]{32}\Z')
JAR = re.compile(r'ghostc-forge189-[0-9]+\.[0-9]+\.[0-9]+\.jar\Z')
CHECKBOX = re.compile(r'^- \[([ xX])\] (.+)$')
REQUIRED_CHECKS = (
    'Launcher starts without a Forge loading error',
    'GhostC appears in the Forge Mods list, with the correct version',
    'F7 ClickGUI opens/closes, and module search/settings work',
    'F8 status HUD toggles and remapped Keystrokes labels are correct',
    'Rendering and Target HUD behave correctly in an authorized local world',
    'Focus/GUI/reconnect do not leave synthetic attack/use held',
    'Profile changes survive restart, and Rollback can restore the old JARs',
    'FPS min/p50/p95/p99 and JVM heap peak observed during focused gameplay',
    'A representative 60-minute soak checked for crashes, memory/FPS and faults',
)
OBSERVATIONS = (
    'launcher', 'mods_list', 'clickgui', 'keybind_and_hud', 'local_world',
    'focus_reconnect', 'profile_restart', 'rendering', 'performance', 'soak',
)
MANIFEST_KEYS = {
    'schema', 'status', 'source_revision', 'source_revision_kind',
    'platform', 'artifact', 'artifact_sha256', 'artifact_bytes',
    'minecraft', 'java_class_major', 'verified_java_classes',
}
EVIDENCE_KEYS = {
    'schema', 'session_transaction', 'artifact_sha256', 'minecraft',
    'forge', 'java_major', 'soak_minutes', 'observations',
}

def read_limited(path, limit):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing or linked input: ' + path.name)
    with path.open('rb') as file:
        raw = file.read(limit + 1)
    if len(raw) > limit:
        raise ValueError('Input exceeds size budget: ' + path.name)
    return raw

def sha256(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing or linked JAR')
    value = hashlib.sha256()
    with path.open('rb') as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()

def package(jar, manifest_file, sums_file):
    jar = Path(jar)
    if not JAR.fullmatch(jar.name):
        raise ValueError('Expected versioned GhostC Forge JAR')
    manifest = json.loads(read_limited(manifest_file, 16384).decode('utf-8-sig'))
    if not isinstance(manifest, dict) or set(manifest) != MANIFEST_KEYS:
        raise ValueError('Unexpected build manifest schema')
    rev = manifest['source_revision']
    recorded_sha = manifest['artifact_sha256']
    if (type(manifest['schema']) is not int or manifest['schema'] != 1 or
        manifest['status'] != PACKAGE_STATUS or
        manifest['source_revision_kind'] != 'CI_CHECKED_OUT_COMMIT' or
        not isinstance(rev, str) or not REVISION.fullmatch(rev) or
        manifest['platform'] not in ('linux', 'windows') or
        manifest['minecraft'] != '1.8.9' or
        type(manifest['java_class_major']) is not int or
        manifest['java_class_major'] != 52 or
        type(manifest['verified_java_classes']) is not int or
        manifest['verified_java_classes'] < 1 or
        not isinstance(recorded_sha, str) or
        not DIGEST.fullmatch(recorded_sha) or recorded_sha != recorded_sha.lower() or
        type(manifest['artifact_bytes']) is not int or
        manifest['artifact_bytes'] < 1 or
        manifest['artifact'] != jar.name or
        manifest['artifact_bytes'] != jar.stat().st_size):
        raise ValueError('Build metadata does not describe this Forge package')
    value = sha256(jar)
    if value != recorded_sha:
        raise ValueError('JAR bytes differ from build manifest')
    checksum_line = read_limited(sums_file, 512).decode('ascii').splitlines()
    if checksum_line != [value + '  ' + jar.name]:
        raise ValueError('SHA256SUMS does not match this package')
    return manifest

def unique_field(lines, label):
    prefix = label + ': '
    found = [line[len(prefix):] for line in lines if line.startswith(prefix)]
    if len(found) != 1:
        raise ValueError('Missing or duplicated report ' + label)
    return found[0]

def report(path, manifest):
    lines = read_limited(path, 262144).decode('utf-8-sig').splitlines()
    if '# GhostC Windows acceptance report' not in lines or not any(
        'UNVERIFIED IN-GAME' in line for line in lines
    ):
        raise ValueError('Not a GhostC operator Collect report')
    if unique_field(lines, 'Installed artifact') != manifest['artifact']:
        raise ValueError('Report JAR identity differs from CI package')
    if unique_field(lines, 'Verified SHA256') != manifest['artifact_sha256'].upper():
        raise ValueError('Report checksum differs from CI package')
    tx = unique_field(lines, 'Session transaction')
    if not TRANSACTION.fullmatch(tx):
        raise ValueError('Invalid report session transaction')
    checkboxes = {}
    for line in lines:
        match = CHECKBOX.fullmatch(line)
        if match:
            title = match.group(2)
            if title in checkboxes:
                raise ValueError('Repeated checkbox label')
            checkboxes[title] = match.group(1) in ('x', 'X')
    if not checkboxes or any(key not in checkboxes for key in REQUIRED_CHECKS):
        raise ValueError('Required native acceptance checklist was removed')
    missing = [check for check in REQUIRED_CHECKS if not checkboxes[check]]
    return {'transaction': tx, 'checked': sum(checkboxes.values()),
            'total': len(checkboxes), 'missing': missing}

def evidence(path, manifest, session):
    obj = json.loads(read_limited(path, 8192).decode('utf-8-sig'))
    if not isinstance(obj, dict) or set(obj) != EVIDENCE_KEYS:
        raise ValueError('Unexpected manual evidence schema')
    if (type(obj['schema']) is not int or obj['schema'] != 1 or
        obj['session_transaction'] != session or
        obj['artifact_sha256'] != manifest['artifact_sha256'] or
        obj['minecraft'] != '1.8.9' or
        obj['forge'] != '11.15.1.2318' or
        type(obj['java_major']) is not int or obj['java_major'] != 8 or
        type(obj['soak_minutes']) is not int or
        obj['soak_minutes'] < 0 or obj['soak_minutes'] > 10080):
        raise ValueError('Manual evidence does not match this session/toolchain')
    obs = obj['observations']
    if not isinstance(obs, dict) or set(obs) != set(OBSERVATIONS) or any(
        type(value) is not bool for value in obs.values()
    ):
        raise ValueError('Manual observations must contain exact boolean fields')
    missing = [key for key in OBSERVATIONS if not obs[key]]
    if obj['soak_minutes'] < 60 and 'soak' not in missing:
        missing.append('soak')
    return missing

def audit(jar, manifest_file, sums, report_file=None, evidence_file=None):
    source = package(jar, manifest_file, sums)
    result = {'status': 'AWAITING_NATIVE_REPORT',
              'source_revision': source['source_revision'],
              'artifact_sha256': source['artifact_sha256'],
              'checked': 0, 'total': 0, 'missing_checks': list(REQUIRED_CHECKS),
              'missing_observations': list(OBSERVATIONS)}
    if evidence_file is not None and report_file is None:
        raise ValueError('Manual evidence requires matching Collect report')
    if report_file is None:
        return result
    review = report(report_file, source)
    result.update(checked=review['checked'], total=review['total'],
                  missing_checks=review['missing'], status='INCOMPLETE_OPERATOR_CHECKLIST',
                  session_transaction=review['transaction'])
    if evidence_file is None:
        if not review['missing']:
            result['status'] = 'AWAITING_MANUAL_OBSERVATIONS'
        return result
    pending = evidence(evidence_file, source, review['transaction'])
    result['missing_observations'] = pending
    if not pending and not review['missing']:
        # Even complete self-attestation is not a native game test executed by CI.
        result['status'] = 'OPERATOR_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW'
    return result

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--jar', type=Path, required=True)
    ap.add_argument('--manifest', type=Path, required=True)
    ap.add_argument('--sha256sums', type=Path, required=True)
    ap.add_argument('--report', type=Path)
    ap.add_argument('--evidence', type=Path)
    ap.add_argument('--require-review-ready', action='store_true',
                    help='Nonzero exit unless all operator evidence is ready for independent review')
    args = ap.parse_args()
    try:
        result = audit(args.jar, args.manifest, args.sha256sums,
                       args.report, args.evidence)
    except (OSError, UnicodeError, ValueError, TypeError, KeyError) as exc:
        print('NATIVE_ACCEPTANCE_AUDIT_FAIL: ' + str(exc), file=sys.stderr)
        return 1
    # No arbitrary owner notes, logs, identities or evidence text are emitted.
    print(json.dumps(result, sort_keys=True))
    if args.require_review_ready and result['status'] != 'OPERATOR_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW':
        return 2
    return 0

if __name__ == '__main__':
    sys.exit(main())
