import unittest
import shutil
from pathlib import Path
import opendataloader_pdf


class TestWrapper(unittest.TestCase):
    def setUp(self):
        # Define paths
        self.project_root = Path(__file__).resolve().parents[3]
        self.input_pdf = self.project_root / "resources" / "1901.03003.pdf"

        # Create a temporary directory
        self.output_dir = (
            Path(self.project_root) / "python" / "opendataloader-pdf" / "tests" / "temp"
        )
        self.output_dir.mkdir(exist_ok=True)

    def tearDown(self):
        # Remove the temporary directory
        shutil.rmtree(self.output_dir)

    def test_run_generates_markdown(self):
        # Ensure input file exists
        self.assertTrue(
            self.input_pdf.exists(), f"Input PDF not found at {self.input_pdf}"
        )

        # Run the wrapper to generate markdown
        opendataloader_pdf.run(
            input_path=str(self.input_pdf),
            output_folder=str(self.output_dir),
            generate_markdown=True,
        )

        # Check if the output markdown file was created
        expected_output = self.output_dir / "1901.03003.md"
        self.assertTrue(
            expected_output.exists(),
            f"Expected output file not found at {expected_output}",
        )

        # Check if the output file has content
        self.assertGreater(expected_output.stat().st_size, 0, "Output file is empty")

    def test_convert_to_multi_format(self):
        # Ensure input file exists
        self.assertTrue(
            self.input_pdf.exists(), f"Input PDF not found at {self.input_pdf}"
        )

        # Convert to multiple formats
        opendataloader_pdf.convert(
            input_path=[str(self.input_pdf)],
            output_dir=str(self.output_dir),
            format="json,text,html,pdf,markdown",
        )

        # Json
        json = self.output_dir / "1901.03003.json"
        self.assertTrue(
            json.exists(),
            f"JSON file not found at {json}",
        )
        self.assertGreater(json.stat().st_size, 0, "JSON file is empty")

        # Text
        text = self.output_dir / "1901.03003.txt"
        self.assertTrue(
            text.exists(),
            f"Text file not found at {text}",
        )
        self.assertGreater(text.stat().st_size, 0, "Text file is empty")

        # Html
        html = self.output_dir / "1901.03003.html"
        self.assertTrue(
            html.exists(),
            f"HTML not found at {html}",
        )
        self.assertGreater(html.stat().st_size, 0, "HTML is empty")

        # Annotated PDF
        annotated_pdf = self.output_dir / "1901.03003_annotated.pdf"
        self.assertTrue(
            annotated_pdf.exists(),
            f"Annotated PDF not found at {annotated_pdf}",
        )
        self.assertGreater(annotated_pdf.stat().st_size, 0, "Annotated PDF is empty")

        # Markdown
        markdown = self.output_dir / "1901.03003.md"
        self.assertTrue(
            markdown.exists(),
            f"Markdown not found at {markdown}",
        )
        self.assertGreater(markdown.stat().st_size, 0, "Markdown is empty")


if __name__ == "__main__":
    unittest.main()
