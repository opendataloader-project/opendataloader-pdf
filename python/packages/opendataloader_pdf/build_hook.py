# python/packages/opendataloader_pdf/build_hook.py

import os
import platform
import shutil
import tarfile
import zipfile
import urllib.request
from hatchling.builders.hooks.plugin.interface import BuildHookInterface

class CustomBuildHook(BuildHookInterface):
    PLATFORM_JRE_MAP = {
        "Linux": {
            "arch": "x64",
            "url_template": "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.22%2B7/OpenJDK11U-jdk_x64_linux_hotspot_11.0.22_7.tar.gz",
            "extract_name": "jdk-11.0.22+7",
            "archive_type": "tar.gz",
            "dest_subdir": "linux-x64"
        },
        "Darwin": { # macOS
            "arch": "x64",
            "url_template": "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.22%2B7/OpenJDK11U-jdk_x64_mac_hotspot_11.0.22_7.tar.gz",
            "extract_name": "jdk-11.0.22+7",
            "archive_type": "tar.gz",
            "dest_subdir": "macos-x64"
        },
        "Windows": {
            "arch": "x64",
            "url_template": "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.22%2B7/OpenJDK11U-jdk_x64_windows_hotspot_11.0.22_7.zip",
            "extract_name": "jdk-11.0.22+7",
            "archive_type": "zip",
            "dest_subdir": "windows-x64"
        }
    }

    def initialize(self, version: str, build_data: dict) -> None:
        # This hook runs before building the wheel.
        # We need to ensure the JRE is present in the _jars directory.
        print("Running CustomBuildHook initialize...")
        
        # Define paths relative to the package root (src/opendataloader_pdf)
        package_root = os.path.join(self.root, "src", "opendataloader_pdf")
        jars_dir = os.path.join(package_root, "_jars")
        jre_dest_dir = os.path.join(jars_dir, "jre")

        os.makedirs(jars_dir, exist_ok=True)
        os.makedirs(jre_dest_dir, exist_ok=True)

        # Determine the current platform for JRE bundling
        current_system = platform.system()
        if current_system not in self.PLATFORM_JRE_MAP:
            print(f"Warning: JRE bundling not supported for {current_system}. Skipping JRE download.")
            return

        platform_info = self.PLATFORM_JRE_MAP[current_system]
        jre_url = platform_info["url_template"]
        jre_archive_type = platform_info["archive_type"]
        jre_extract_name = platform_info["extract_name"]
        jre_dest_subdir = platform_info["dest_subdir"]

        final_jre_path = os.path.join(jre_dest_dir, jre_dest_subdir, jre_extract_name)

        if os.path.exists(final_jre_path):
            print(f"JRE already exists at {final_jre_path}. Skipping download.")
            return

        # Download JRE
        archive_name = jre_url.split('/')[-1]
        download_path = os.path.join(jars_dir, archive_name)
        
        print(f"Downloading JRE from {jre_url} to {download_path}...")
        urllib.request.urlretrieve(jre_url, download_path)
        print("JRE download complete.")

        # Extract JRE
        extract_to_path = os.path.join(jre_dest_dir, jre_dest_subdir)
        os.makedirs(extract_to_path, exist_ok=True)

        print(f"Extracting JRE to {extract_to_path}...")
        if jre_archive_type == "tar.gz":
            with tarfile.open(download_path, "r:gz") as tar:
                tar.extractall(path=extract_to_path)
        elif jre_archive_type == "zip":
            with zipfile.ZipFile(download_path, "r") as zip_ref:
                zip_ref.extractall(path=extract_to_path)
        else:
            raise ValueError(f"Unsupported archive type: {jre_archive_type}")
        print("JRE extraction complete.")

        # Clean up archive
        os.remove(download_path)
        print(f"Cleaned up {download_path}")

        # Add the JRE directory to the build data to be included in the wheel
        # This assumes the JRE is placed directly under src/opendataloader_pdf/_jars/jre
        # The `packages` field in pyproject.toml should handle this if it points to src/opendataloader_pdf
        # If not, you might need to explicitly add it to build_data['force_include']
        # For hatchling, if the JRE is inside a package that's included, it should be fine.
        # We'll verify this during testing.
        
        # Ensure the JRE is included in the wheel
        # build_data['force_include'].append(os.path.join(jre_dest_dir, jre_dest_subdir))
        # print(f"Added {os.path.join(jre_dest_dir, jre_dest_subdir)} to force_include")
        
        print("CustomBuildHook initialize finished.")

    def finalize(self, version: str, build_data: dict, artifact: str) -> None:
        # This hook runs after the build but before the wheel is finalized.
        # You can perform any final adjustments here.
        print("Running CustomBuildHook finalize...")
        print("CustomBuildHook finalize finished.")
