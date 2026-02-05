"""PDF parser using opendataloader-pdf with hybrid mode (hancom backend).

This module runs the local opendataloader-pdf JAR with --hybrid hancom option,
which routes pages to the Hancom Document AI backend for processing.

Requirements:
- Hancom API access (default: https://dataloader.cloud.hancom.com/studio-lite/api)
- Local JAR built: ./scripts/build-java.sh

Usage:
    ./scripts/bench.sh --engine opendataloader-hybrid-hancom

Environment Variables:
    HANCOM_URL: Override URL for the Hancom API backend
    HYBRID_TIMEOUT: Request timeout in milliseconds (default: 600000)
"""

import os
import subprocess
import sys
from pathlib import Path


DEFAULT_URL = "https://dataloader.cloud.hancom.com/studio-lite/api"


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
    """Convert PDF to Markdown using hybrid mode with hancom backend.

    Args:
        _: Unused (for compatibility with engine dispatch signature).
        input_path: Input directory or single PDF file path.
        output_dir: Output directory for markdown files.

    Environment Variables:
        HANCOM_URL: Override URL for the Hancom API. Default: https://dataloader.cloud.hancom.com/studio-lite/api
        HYBRID_TIMEOUT: Request timeout in milliseconds. Default: 600000
    """
    jar_path = _find_local_jar()

    backend_url = os.environ.get("HANCOM_URL", DEFAULT_URL)
    timeout_ms = os.environ.get("HYBRID_TIMEOUT", "600000")

    # Build command - pass input path directly (directory or file)
    command = [
        "java", "-jar", str(jar_path),
        str(input_path),
        "--output-dir", str(output_dir),
        "--format", "markdown",
        "--image-output", "off",
        "--quiet",
        # Hybrid mode options
        "--hybrid", "hancom",
        "--hybrid-url", backend_url,
        "--hybrid-timeout", timeout_ms,
        "--hybrid-fallback",
        # Hancom uses full mode (no triage, all pages to backend)
        "--hybrid-mode", "full",
    ]

    # Run conversion
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"Error converting {input_path} (hancom hybrid mode):", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        # Don't raise - continue with other files
