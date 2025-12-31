"""Custom build hook for hatch to copy JAR and license files."""

import glob
import shutil
from pathlib import Path

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CustomBuildHook(BuildHookInterface):
    def initialize(self, version, build_data):
        root_dir = Path(self.root)
        pkg_dir = root_dir / "src/opendataloader_pdf"
        dest_jar_dir = pkg_dir / "jar"
        dest_jar_path = dest_jar_dir / "opendataloader-pdf-cli.jar"
        license_path = pkg_dir / "LICENSE"
        notice_path = pkg_dir / "NOTICE.md"
        third_party_dest = pkg_dir / "THIRD_PARTY"

        readme_path = root_dir / "README.md"

        # Check if all required files already exist (building from sdist)
        if (
            dest_jar_path.exists()
            and license_path.exists()
            and notice_path.exists()
            and third_party_dest.exists()
            and readme_path.exists()
        ):
            print("All required files already exist (building from sdist), skipping copy")
            return

        # --- Copy JAR ---
        print(f"Root DIR: {root_dir}")
        source_jar_glob = str(
            root_dir / "../../java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar"
        )
        resolved_glob_path = Path(source_jar_glob).resolve()
        print(f"Searching for JAR file in: {resolved_glob_path}")

        source_jar_paths = glob.glob(source_jar_glob)
        if not source_jar_paths:
            raise RuntimeError(
                f"Could not find the JAR file. Please run 'mvn package' in the 'java/' directory first. Searched in: {resolved_glob_path}"
            )
        if len(source_jar_paths) > 1:
            raise RuntimeError(f"Found multiple JAR files, expected one: {source_jar_paths}")
        source_jar_path = source_jar_paths[0]
        print(f"Found source JAR: {source_jar_path}")

        dest_jar_dir.mkdir(parents=True, exist_ok=True)
        print(f"Copying JAR to {dest_jar_path}")
        shutil.copy(source_jar_path, dest_jar_path)

        # --- Copy LICENSE, NOTICE, README ---
        shutil.copy(root_dir / "../../LICENSE", license_path)
        shutil.copy(root_dir / "../../NOTICE.md", notice_path)
        shutil.copy(root_dir / "../../README.md", readme_path)
        third_party_src = root_dir / "../../THIRD_PARTY"
        print(f"Copying THIRD_PARTY directory to {third_party_dest}")
        if third_party_dest.exists():
            shutil.rmtree(third_party_dest)
        shutil.copytree(third_party_src, third_party_dest)
