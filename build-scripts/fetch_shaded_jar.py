"""
Finds and copies the latest shaded JAR from the Java build to the Python package source.

This script is intended to be run from the monorepo root, typically as part of a
CI/CD pipeline, before the Python package is built.
"""

import argparse
import logging
import re
import shutil
import sys
from pathlib import Path
from typing import Optional

# Requires 'packaging' library (pip install packaging)
from packaging.version import parse as parse_version

def find_latest_jar_by_semver(target_dir: Path) -> Optional[Path]:
    """Finds the shaded JAR with the highest semantic version in its filename."""

    # Example filename: opendataloader-pdf-runtime-0.1.0.jar
    jar_pattern = "opendataloader-pdf-runtime-*.jar"
    version_regex = re.compile(r"opendataloader-pdf-runtime-(.+?)\.jar")

    latest_version = parse_version("0.0.0")
    latest_jar_path = None

    # Exclude Maven's 'original' JARs to ensure we get the shaded (fat) JAR.
    potential_jars = [p for p in target_dir.glob(jar_pattern) if 'original' not in p.name]

    if not potential_jars:
        return None

    # Iterate through potential JARs to find the one with the highest version number.
    for jar_path in potential_jars:
        match = version_regex.search(jar_path.name)
        if match:
            try:
                current_version = parse_version(match.group(1))
                if current_version > latest_version:
                    latest_version = current_version
                    latest_jar_path = jar_path
            except Exception:
                # Ignore files with non-parseable version strings.
                continue

    return latest_jar_path

def main():
    """Parse command-line arguments and orchestrate the copy process."""

    logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s', stream=sys.stdout)

    parser = argparse.ArgumentParser(description="Copies the latest shaded JAR to the Python source tree.")
    parser.add_argument("java_target_dir", type=Path, help="Path to the Java module's 'target' directory.")
    parser.add_argument("python_jars_dir", type=Path, help="Path to the Python package's destination directory for JARs.")
    args = parser.parse_args()

    java_target_path: Path = args.java_target_dir.resolve()
    python_jars_path: Path = args.python_jars_dir.resolve()

    if not java_target_path.is_dir():
        parser.error(f"Java target directory not found: {java_target_path}")

    # Ensure the destination directory exists.
    python_jars_path.mkdir(parents=True, exist_ok=True)

    source_jar_path = find_latest_jar_by_semver(java_target_path)
    if not source_jar_path:
        parser.error(f"No versioned shaded JAR found in: {java_target_path}")

    # Standardize the destination name for consistent access within the Python package.
    destination_jar_path = python_jars_path / 'runtime.jar'

    shutil.copy2(source_jar_path, destination_jar_path)
    logging.info(f"Copied '{source_jar_path.name}' to '{destination_jar_path}'")

if __name__ == "__main__":
    main()
