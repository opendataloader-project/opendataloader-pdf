"""Tests for PARTIAL_SUCCESS handling in hybrid server responses.

Validates that when Docling encounters errors during PDF preprocessing
(e.g., Invalid code point), the hybrid server correctly reports:
- partial_success status instead of success
- list of failed page numbers
- error messages from Docling
"""

from opendataloader_pdf.hybrid_server import (
    _extract_failed_pages_from_errors,
    build_conversion_response,
)


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
        """json_content without 'pages' key should mark all requested pages as failed."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"body": {"text": "hello"}},
            processing_time=1.0,
            errors=["error"],
            requested_pages=(1, 3),
        )
        assert response["status"] == "partial_success"
        assert response["failed_pages"] == [1, 2, 3]


class TestExtractFailedPagesFromErrors:
    """Tests for error message-based failed page detection."""

    def test_std_bad_alloc_errors(self):
        """Page numbers should be extracted from 'Page N: std::bad_alloc' messages."""
        errors = [
            "Page 26: std::bad_alloc",
            "Page 27: std::bad_alloc",
            "Page 28: std::bad_alloc",
        ]
        assert _extract_failed_pages_from_errors(errors) == [26, 27, 28]

    def test_mixed_error_formats(self):
        """Only 'Page N:' prefixed messages should be matched."""
        errors = [
            "Page 5: Invalid code point",
            "Unknown page: pipeline terminated early",
            "Page 10: std::bad_alloc",
        ]
        assert _extract_failed_pages_from_errors(errors) == [5, 10]

    def test_no_page_errors(self):
        """Non-page errors should return empty list."""
        errors = [
            "Unknown page: pipeline terminated early",
            "General error occurred",
        ]
        assert _extract_failed_pages_from_errors(errors) == []

    def test_bare_page_no_colon(self):
        """Bare 'Page N' (no colon) should be matched when error_msg is empty."""
        assert _extract_failed_pages_from_errors(["Page 26"]) == [26]


class TestBuildConversionResponseErrorParsing:
    """Tests for failed page detection via error message parsing.

    When docling includes failed pages as empty entries in the pages dict,
    gap detection fails. Error message parsing handles this case.
    """

    def test_failed_pages_with_empty_entries_in_pages_dict(self):
        """Failed pages present as empty entries should still be detected via errors."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "2": {}, "3": {}, "4": {}, "5": {}}},
            processing_time=2.0,
            errors=["Page 4: std::bad_alloc", "Page 5: std::bad_alloc"],
            requested_pages=(1, 5),
        )
        assert response["failed_pages"] == [4, 5]

    def test_boundary_pages_detected_via_errors(self):
        """Boundary page failures should be detected even without page range."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "2": {}, "3": {}, "4": {}, "5": {}}},
            processing_time=2.0,
            errors=["Page 4: std::bad_alloc", "Page 5: std::bad_alloc"],
            requested_pages=None,
            total_pages=None,
        )
        assert response["failed_pages"] == [4, 5]

    def test_both_strategies_combined(self):
        """Union of error-parsed and gap-detected pages should be reported.

        Page 2 is missing from dict (gap), pages 4-5 have error messages
        but are present as empty entries. All three must appear.
        """
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "3": {}, "4": {}, "5": {}}},
            processing_time=2.0,
            errors=["Page 4: std::bad_alloc", "Page 5: std::bad_alloc"],
            requested_pages=(1, 5),
        )
        assert response["failed_pages"] == [2, 4, 5]

    def test_overlap_between_gap_and_error_is_deduplicated(self):
        """Same page from gap and error parsing should appear once."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "3": {}}},
            processing_time=1.0,
            errors=["Page 2: std::bad_alloc"],
            requested_pages=(1, 3),
        )
        assert response["failed_pages"] == [2]

    def test_duplicate_page_in_errors(self):
        """Duplicate page numbers in error messages should be deduplicated."""
        errors = [
            "Page 3: std::bad_alloc",
            "Page 3: Invalid code point",
        ]
        assert _extract_failed_pages_from_errors(errors) == [3]

    def test_no_page_pattern_errors_falls_back_to_gap(self):
        """When errors lack 'Page N:' pattern, gap detection should still work."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "3": {}}},
            processing_time=1.0,
            errors=["Unknown page: pipeline terminated early"],
            requested_pages=(1, 3),
        )
        assert response["failed_pages"] == [2]

    def test_empty_errors_with_partial_success(self):
        """Partial success with no error messages should still detect gaps."""
        response = build_conversion_response(
            status_value="partial_success",
            json_content={"pages": {"1": {}, "3": {}}},
            processing_time=1.0,
            errors=[],
            requested_pages=(1, 3),
        )
        assert response["failed_pages"] == [2]
