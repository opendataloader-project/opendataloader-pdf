"""Integration tests that actually run the JAR (slow)"""

import opendataloader_pdf


def test_convert_generates_output(input_pdf, output_dir):
    """Verify that convert() actually generates output files"""
    opendataloader_pdf.convert(
        input_path=str(input_pdf),
        output_dir=str(output_dir),
        format="json",
        quiet=True,
    )
    output = output_dir / "1901.03003.json"
    assert output.exists(), f"Output file not found at {output}"
    assert output.stat().st_size > 0, "Output file is empty"
