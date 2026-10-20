#!/usr/bin/env python3
"""Offline Forge build provenance. Source attribution, NOT Minecraft acceptance.

Manifest records the *checked-out* commit (PR CI can checkout a synthetic merge),
OS family, verified Java 8 mod structure and SHA-256 of the exact archived JAR.
It is not a signature, proof of reproducibility, or evidence the mod launched.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from verify_forge_jar import verify as verify_jar

STATUS = "CI_PACKAGE_ONLY_NOT_TESTED_IN_MINECRAFT"
SCHEMA = 1
PLATFORMS = ("linux", "windows")
REVISION = re.compile(r"[0-9a-fA-F]{40}\Z")
CHECKSUM = re.compile(r"([0-9a-fA-F]{64})  (ghostc-forge189-[0-9]+\.[0-9]+\.[0-9]+\.jar)\Z")


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def check_sha256sums(jar, sums, expected):
    lines = sums.read_text(encoding="ascii").splitlines()
    if len(lines) != 1:
        raise ValueError("Expected exactly one Forge JAR checksum entry")
    match = CHECKSUM.fullmatch(lines[0])
    if not match or match.group(2) != jar.name:
        raise ValueError("Checksum filename/format mismatch")
    if match.group(1).lower() != expected:
        raise ValueError("SHA256SUMS digest mismatch")


def create(jar, version, revision, platform, output, sums):
    if not REVISION.fullmatch(revision):
        raise ValueError("Missing or malformed checked-out source revision")
    if platform not in PLATFORMS:
        raise ValueError("Unsupported build platform")
    classes = verify_jar(jar, version)
    sha = digest(jar)
    check_sha256sums(jar, sums, sha)
    manifest = {
        "schema": SCHEMA,
        "status": STATUS,
        "source_revision": revision.lower(),
        "source_revision_kind": "CI_CHECKED_OUT_COMMIT",
        "platform": platform,
        "artifact": jar.name,
        "artifact_sha256": sha,
        "artifact_bytes": jar.stat().st_size,
        "minecraft": "1.8.9",
        "java_class_major": 52,
        "verified_java_classes": classes,
    }
    if output.parent.resolve() != jar.parent.resolve():
        raise ValueError("Manifest must be adjacent to the JAR")
    output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return verify(jar, output, sums, revision, platform)


def verify(jar, manifest_path, sums, revision=None, platform=None):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(manifest, dict):
        raise ValueError("Invalid manifest object")
    if set(manifest) != {
        "schema", "status", "source_revision", "source_revision_kind",
        "platform", "artifact", "artifact_sha256", "artifact_bytes",
        "minecraft", "java_class_major", "verified_java_classes"
    }:
        raise ValueError("Unexpected manifest schema fields")
    source = manifest["source_revision"]
    build_platform = manifest["platform"]
    if (type(manifest["schema"]) is not int or manifest["schema"] != SCHEMA or
        manifest["status"] != STATUS or
        manifest["source_revision_kind"] != "CI_CHECKED_OUT_COMMIT" or
        not isinstance(source, str) or not REVISION.fullmatch(source) or
        source != source.lower() or build_platform not in PLATFORMS or
        manifest["minecraft"] != "1.8.9" or
        type(manifest["java_class_major"]) is not int or
        manifest["java_class_major"] != 52):
        raise ValueError("Invalid CI provenance metadata")
    if revision is not None and source != revision.lower():
        raise ValueError("CI checkout source revision mismatch")
    if platform is not None and build_platform != platform:
        raise ValueError("CI platform mismatch")
    if manifest["artifact"] != jar.name or (
        type(manifest["artifact_bytes"]) is not int or
        manifest["artifact_bytes"] < 1 or
        manifest["artifact_bytes"] != jar.stat().st_size
    ):
        raise ValueError("Artifact identity or byte length mismatch")
    sha = digest(jar)
    if not isinstance(manifest["artifact_sha256"], str) or (
        manifest["artifact_sha256"] != sha
    ):
        raise ValueError("Artifact hash mismatch")
    check_sha256sums(jar, sums, sha)
    version_match = re.fullmatch(r"ghostc-forge189-([0-9]+\.[0-9]+\.[0-9]+)\.jar", jar.name)
    if not version_match:
        raise ValueError("Invalid versioned JAR name")
    classes = verify_jar(jar, version_match.group(1))
    if (type(manifest["verified_java_classes"]) is not int or
        classes != manifest["verified_java_classes"]):
        raise ValueError("Java class inventory mismatch")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    for name in ("create", "verify"):
        p = commands.add_parser(name)
        p.add_argument("--jar", type=Path, required=True)
        p.add_argument("--sha256sums", type=Path, required=True)
        p.add_argument("--manifest", type=Path, required=True)
        p.add_argument("--revision", required=True)
        p.add_argument("--platform", choices=PLATFORMS, required=True)
        if name == "create":
            p.add_argument("--version", required=True)
    args = parser.parse_args()
    if args.action == "create":
        result = create(args.jar, args.version, args.revision,
                        args.platform, args.manifest, args.sha256sums)
    else:
        result = verify(args.jar, args.manifest, args.sha256sums,
                        args.revision, args.platform)
    print("FORGE_PROVENANCE_PASS status=%s revision=%s platform=%s sha256=%s" % (
        result["status"], result["source_revision"], result["platform"],
        result["artifact_sha256"]))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
        print("FORGE_PROVENANCE_FAIL: " + str(exc), file=sys.stderr)
        sys.exit(1)
