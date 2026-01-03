"""PDF parser using opendataloader-pdf with hybrid mode (docling-fast backend).

This module runs the local opendataloader-pdf JAR with --hybrid option,
which routes complex pages (tables, etc.) to a backend server for
improved accuracy.

Requirements:
- docling-fast server running: pip install opendataloader-pdf[hybrid] && opendataloader-pdf-hybrid
- Local JAR built: ./scripts/build-java.sh

Usage:
    opendataloader-pdf-hybrid &
    ./scripts/bench.sh --hybrid docling-fast
"""

import os
import subprocess
import sys
from pathlib import Path


DEFAULT_URL = "http://localhost:5002"


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
    """Convert PDF to Markdown using hybrid mode with docling-fast backend.

    Args:
        _: Unused (for compatibility with engine dispatch signature).
        input_path: Input directory or single PDF file path.
        output_dir: Output directory for markdown files.

    Environment Variables:
        DOCLING_URL: Override URL for the backend server. Default: http://localhost:5002
        HYBRID_TIMEOUT: Request timeout in milliseconds. Default: 600000
    """
    jar_path = _find_local_jar()

    backend_url = os.environ.get("DOCLING_URL", DEFAULT_URL)
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
        "--hybrid", "docling-fast",
        "--hybrid-url", backend_url,
        "--hybrid-timeout", timeout_ms,
        "--hybrid-fallback",
    ]

    # Run conversion
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"Error converting {input_path} (hybrid mode):", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        # Don't raise - continue with other files
