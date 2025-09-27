import glob
import shutil
from setuptools import setup, find_packages
from setuptools.command.build_py import build_py
from pathlib import Path


class CustomBuildPy(build_py):
    def run(self):
        root_dir = Path(__file__).resolve().parent

        # --- Copy JAR ---
        print(f"Root DIR: {root_dir}")
        source_jar_glob = str(
            root_dir
            / "../../java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar"
        )
        resolved_glob_path = Path(source_jar_glob).resolve()
        print(f"Searching for JAR file in: {resolved_glob_path}")

        source_jar_paths = glob.glob(source_jar_glob)
        if not source_jar_paths:
            raise RuntimeError(
                f"Could not find the JAR file. Please run 'mvn package' in the 'java/' directory first. Searched in: {resolved_glob_path}"
            )
        if len(source_jar_paths) > 1:
            raise RuntimeError(
                f"Found multiple JAR files, expected one: {source_jar_paths}"
            )
        source_jar_path = source_jar_paths[0]
        print(f"Found source JAR: {source_jar_path}")

        dest_jar_dir = Path("src/opendataloader_pdf/jar")
        dest_jar_dir.mkdir(parents=True, exist_ok=True)
        dest_jar_path = dest_jar_dir / "opendataloader-pdf-cli.jar"
        print(f"Copying JAR to {dest_jar_path}")
        shutil.copy(source_jar_path, dest_jar_path)

        # --- Copy LICENSE & NOTICE ---
        shutil.copy("../../LICENSE", "src/opendataloader_pdf/LICENSE")
        shutil.copy("../../NOTICE.md", "src/opendataloader_pdf/NOTICE.md")
        third_party_src = root_dir / "../../THIRD_PARTY"
        third_party_dest = Path("src/opendataloader_pdf/THIRD_PARTY")
        print(f"Copying THIRD_PARTY directory to {third_party_dest}")
        if third_party_dest.exists():
            shutil.rmtree(third_party_dest)
        shutil.copytree(third_party_src, third_party_dest)

        super().run()


with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()


setup(
    name="opendataloader-pdf",
    version="0.0.0",
    description="A Python wrapper for the opendataloader-pdf Java CLI.",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/opendataloader-project/opendataloader-pdf",
    author="opendataloader-project",
    author_email="open.dataloader@hancom.com",
    license="MPL-2.0",
    classifiers=[
        "Programming Language :: Python :: 3",
        "Operating System :: OS Independent",
    ],
    python_requires=">=3.9, <4.0",
    package_dir={"": "src"},
    packages=find_packages(where="src"),
    package_data={
        "opendataloader_pdf": [
            "LICENSE",
            "NOTICE.md",
            "jar/*.jar",
            "THIRD_PARTY/**",
        ],
    },
    entry_points={
        "console_scripts": [
            "opendataloader-pdf=opendataloader_pdf.wrapper:main",
        ]
    },
    cmdclass={"build_py": CustomBuildPy},
)
