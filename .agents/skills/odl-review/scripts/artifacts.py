#!/usr/bin/env python3
"""List the members of build artifacts and check expected paths. Reads files only.

Usage: artifacts.py ARTIFACT [ARTIFACT ...] [--expect GLOB ...] [--absent GLOB ...]

ARTIFACT: .whl, .jar, .zip, .tar.gz, .tgz (wheel, jar, sdist, npm pack).
--expect GLOB   each artifact must have a member that matches (fnmatch, for example '*/py.typed')
--absent GLOB   no artifact may have a matching member
Exit code 1 if a check fails.
"""

from __future__ import annotations

import fnmatch
import sys
import tarfile
import zipfile


def members(path: str) -> list[str]:
    if path.endswith((".whl", ".jar", ".zip")):
        with zipfile.ZipFile(path) as z:
            return z.namelist()
    if path.endswith((".tar.gz", ".tgz")):
        with tarfile.open(path) as t:
            return t.getnames()
    raise SystemExit(f"unknown artifact type: {path}")


def main(argv: list[str]) -> int:
    files, expect, absent, mode = [], [], [], None
    for a in argv:
        if a in ("--expect", "--absent"):
            mode = a
        elif mode == "--expect":
            expect.append(a)
        elif mode == "--absent":
            absent.append(a)
        else:
            files.append(a)
    if not files:
        print(__doc__)
        return 2
    bad = False
    for f in files:
        names = members(f)
        print(f"{f}: {len(names)} members")
        for g in expect:
            hits = fnmatch.filter(names, g)
            print(f"  expect {g}: {'ok ' + hits[0] if hits else 'MISSING'}")
            bad |= not hits
        for g in absent:
            hits = fnmatch.filter(names, g)
            print(f"  absent {g}: {'FOUND ' + hits[0] if hits else 'ok'}")
            bad |= bool(hits)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
