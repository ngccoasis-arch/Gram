#!/usr/bin/env python3
"""Apply the smallest deterministic ABI-only patch to TDLib's official Android scripts."""

from __future__ import annotations

import argparse
from pathlib import Path


OFFICIAL_LOOP = "for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do"
SCRIPT_NAMES = ("build-openssl.sh", "build-tdlib.sh")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("android_example_dir", type=Path)
    parser.add_argument("abis", nargs="+")
    args = parser.parse_args()

    allowed = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"}
    unknown = set(args.abis) - allowed
    if unknown:
        raise SystemExit(f"Unsupported Android ABI(s): {', '.join(sorted(unknown))}")

    replacement = f"for ABI in {' '.join(args.abis)} ; do"
    for name in SCRIPT_NAMES:
        path = args.android_example_dir / name
        contents = path.read_text(encoding="utf-8")
        occurrences = contents.count(OFFICIAL_LOOP)
        if occurrences != 1:
            raise SystemExit(
                f"Refusing to patch {path}: expected one official ABI loop, found {occurrences}. "
                "Review the newly pinned TDLib source before changing this guard."
            )
        path.write_text(contents.replace(OFFICIAL_LOOP, replacement), encoding="utf-8")


if __name__ == "__main__":
    main()
