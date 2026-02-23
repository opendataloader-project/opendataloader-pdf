"""Tests for PARTIAL_SUCCESS handling in hybrid server responses.

Validates that when Docling encounters errors during PDF preprocessing
(e.g., Invalid code point), the hybrid server correctly reports:
- partial_success status instead of success
- list of failed page numbers
- error messages from Docling
"""

from opendataloader_pdf.hybrid_server import build_conversion_response


class TestBuildConversionResponse:
    """Tests for the build_conversion_response function."""

    def test_success_status(self):
        """Fully successful conversion should return status=success."""
        response = build_conversion_response(
            status_value="success",
            json_content={"pages": {"1": {}, "2": {}, "3": {}}},
            processing_time=1.5,
            errors=[],
            requested_pages=None,
        )
        assert response["status"] == "success"
        assert response["failed_pages"] == []
        assert response["processing_time"] == 1.5

    def test_partial_success_status(self):
        """PARTIAL_SUCCESS should return status=partial_success with failed pages."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "2": {}, "4": {}, "5": {}}},
            processing_time=2.0,
            errors=["Unknown page: pipeline terminated early"],
            requested_pages=(1, 5),
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [3]
        assert response["errors"] == ["Unknown page: pipeline terminated early"]

    def test_partial_success_multiple_failed_pages(self):
        """Multiple failed pages should all be reported."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "3": {}, "5": {}}},
            processing_time=3.0,
            errors=[
                "Unknown page: pipeline terminated early",
                "Unknown page: pipeline terminated early",
            ],
            requested_pages=(1, 5),
        )
        assert response["status"] == "partial_success"
        assert sorted(response["failed_pages"]) == [2, 4]

    def test_partial_success_no_page_range_with_total_pages(self):
        """When total_pages is provided, boundary page failures are detected."""
        # 5-page document, page 1 (first) and page 5 (last) failed
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"2": {}, "3": {}, "4": {}}},
            processing_time=2.0,
            errors=["error1", "error2"],
            requested_pages=None,
            total_pages=5,
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [1, 5]

    def test_partial_success_no_page_range_fallback(self):
        """When no page range or total_pages, interior gaps are still detected."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "2": {}, "4": {}, "5": {}}},
            processing_time=2.0,
            errors=["Unknown page: pipeline terminated early"],
            requested_pages=None,
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [3]

    def test_success_no_errors_field(self):
        """Successful conversion should have empty errors list."""
        response = build_conversion_response(
            status_value="success",
            json_content={"pages": {"1": {}, "2": {}}},
            processing_time=1.0,
            errors=[],
            requested_pages=None,
        )
        assert response["errors"] == []

    def test_document_field_present(self):
        """Response should contain document.json_content."""
        json_content = {"pages": {"1": {}}, "body": {"text": "hello"}}
        response = build_conversion_response(
            status_value="success",
            json_content=json_content,
            processing_time=1.0,
            errors=[],
            requested_pages=None,
        )
        assert response["document"]["json_content"] == json_content

    def test_partial_success_first_page_failed_with_page_range(self):
        """First page failure should be detected when page range is specified."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"2": {}, "3": {}}},
            processing_time=1.0,
            errors=["error"],
            requested_pages=(1, 3),
        )
        assert response["failed_pages"] == [1]

    def test_partial_success_last_page_failed_with_page_range(self):
        """Last page failure should be detected when page range is specified."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "2": {}}},
            processing_time=1.0,
            errors=["error"],
            requested_pages=(1, 3),
        )
        assert response["failed_pages"] == [3]

    def test_partial_success_all_pages_failed(self):
        """All pages failing should report every page in failed_pages."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {}},
            processing_time=2.0,
            errors=["error1", "error2", "error3"],
            requested_pages=(1, 3),
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [1, 2, 3]

    def test_partial_success_all_pages_failed_with_total_pages(self):
        """All pages failing with total_pages should report every page."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {}},
            processing_time=2.0,
            errors=["error1", "error2"],
            requested_pages=None,
            total_pages=3,
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [1, 2, 3]

    def test_failure_status_no_failed_pages_detection(self):
        """Failure status should not trigger failed page detection."""
        response = build_conversion_response(
            status_value="failure",
            json_content={"pages": {"1": {}}},
            processing_time=1.0,
            errors=["PDF conversion failed"],
            requested_pages=(1, 3),
        )
        assert response["status"] == "failure"
        assert response["failed_pages"] == []

    def test_partial_success_missing_pages_key(self):
        """json_content without 'pages' key should produce empty failed_pages."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"body": {"text": "hello"}},
            processing_time=1.0,
            errors=["error"],
            requested_pages=(1, 3),
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [1, 2, 3]
