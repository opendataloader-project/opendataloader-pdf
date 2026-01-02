"""PDF parser using local opendataloader-pdf build."""

import subprocess
import sys
from pathlib import Path


def _find_local_jar() -> Path:
    """Find the locally built JAR file."""
    # Navigate from tests/benchmark/src to project root
    project_root = Path(__file__).parent.parent.parent.parent.resolve()

    # Look for JAR in java/opendataloader-pdf-cli/target
    cli_target = project_root / "java" / "opendataloader-pdf-cli" / "target"

    # Find the shaded JAR (avoid *-sources.jar, *-javadoc.jar)
    jar_pattern = "opendataloader-pdf-cli-*-shaded.jar"
    jars = list(cli_target.glob(jar_pattern))

    if not jars:
        # Try non-shaded JAR as fallback
        jar_pattern = "opendataloader-pdf-cli-*.jar"
        jars = [j for j in cli_target.glob(jar_pattern)
                if "-sources" not in j.name and "-javadoc" not in j.name]

    if not jars:
        raise FileNotFoundError(
            f"No JAR found in {cli_target}. Run ./scripts/build-java.sh first."
        )

    # Return the most recently modified JAR
    return max(jars, key=lambda p: p.stat().st_mtime)


def to_markdown(_, input_path, output_dir):
    """Convert PDF to Markdown using local JAR build."""
    jar_path = _find_local_jar()

    # Build command
    command = [
        "java", "-jar", str(jar_path),
        str(input_path),
        "--output-dir", str(output_dir),
        "--format", "markdown",
        "--table-method", "cluster",
        "--image-output", "off",
        "--quiet",
    ]

    # Run conversion
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"Error converting {input_path}:", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        # Don't raise - continue with other files
