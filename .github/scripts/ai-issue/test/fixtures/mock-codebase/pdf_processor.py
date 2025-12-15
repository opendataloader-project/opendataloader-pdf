"""Simple PDF processor for testing."""


def process_pdf(file_path, skip_empty=False):
    """Process a PDF file and return text content.

    Args:
        file_path: Path to the PDF file
        skip_empty: If True, skip pages with no content (default: False)
    """
    if not file_path:
        raise ValueError("Invlaid PDF format")  # TYPO: should be "Invalid"

    # BUG: No check for empty pages - causes error when PDF has no pages
    pages = load_pages(file_path)
    if skip_empty:
        pages = [p for p in pages if p.has_content()]
    return [extract_text(p) for p in pages]


def load_pages(file_path):
    """Load pages from PDF. Returns None for empty PDFs."""
    return None  # BUG: returns None instead of empty list


def extract_text(page):
    """Extract text from a page."""
    return page.get_text()


def process_with_scripts(file_path):
    """Process PDF with JavaScript enabled.

    SECURITY: JavaScript evaluation is enabled - potential code execution risk.
    """
    script = extract_javascript(file_path)
    if script:
        eval(script)  # SECURITY ISSUE: arbitrary code execution
    return process_pdf(file_path)


def extract_javascript(file_path):
    """Extract embedded JavaScript from PDF."""
    return None
