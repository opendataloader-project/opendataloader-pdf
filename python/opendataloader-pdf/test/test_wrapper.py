import unittest
import tempfile
import shutil
from pathlib import Path
from opendataloader_pdf import run

class TestWrapper(unittest.TestCase):
    def setUp(self):
        # Create a temporary directory
        self.test_dir = tempfile.mkdtemp()
        self.output_dir = Path(self.test_dir) / "output"
        self.output_dir.mkdir()

        # Define paths
        self.project_root = Path(__file__).resolve().parents[3]
        self.input_pdf = self.project_root / "resources" / "1901.03003.pdf"

    def tearDown(self):
        # Remove the temporary directory
        shutil.rmtree(self.test_dir)

    def test_run_generates_markdown(self):
        # Ensure input file exists
        self.assertTrue(self.input_pdf.exists(), f"Input PDF not found at {self.input_pdf}")

        # Run the wrapper to generate markdown
        run(
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

if __name__ == "__main__":
    unittest.main()
