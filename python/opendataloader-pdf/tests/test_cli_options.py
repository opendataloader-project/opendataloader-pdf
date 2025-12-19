"""Unit tests for auto-generated cli_options module"""

import pytest
from opendataloader_pdf.cli_options_generated import CLI_OPTIONS, add_options_to_parser


class TestCLIOptions:
    """Tests for CLI_OPTIONS metadata list"""

    def test_cli_options_is_list(self):
        """CLI_OPTIONS should be a list"""
        assert isinstance(CLI_OPTIONS, list)

    def test_cli_options_not_empty(self):
        """CLI_OPTIONS should not be empty"""
        assert len(CLI_OPTIONS) > 0

    def test_each_option_has_required_fields(self):
        """Each option should have all required fields"""
        required_fields = [
            "name",
            "python_name",
            "short_name",
            "type",
            "required",
            "default",
            "description",
        ]
        for opt in CLI_OPTIONS:
            for field in required_fields:
                assert field in opt, f"Option {opt.get('name', 'unknown')} missing field: {field}"

    def test_option_types_are_valid(self):
        """Option types should be 'string' or 'boolean'"""
        valid_types = {"string", "boolean"}
        for opt in CLI_OPTIONS:
            assert opt["type"] in valid_types, f"Invalid type for {opt['name']}: {opt['type']}"

    def test_python_name_is_snake_case(self):
        """Python names should be snake_case (no hyphens)"""
        for opt in CLI_OPTIONS:
            assert "-" not in opt["python_name"], f"Python name should not contain hyphen: {opt['python_name']}"

    def test_known_options_exist(self):
        """Known options should exist in the list"""
        option_names = {opt["name"] for opt in CLI_OPTIONS}
        expected_options = {
            "output-dir",
            "password",
            "format",
            "quiet",
            "content-safety-off",
            "keep-line-breaks",
            "embed-images",
            "image-format",
        }
        for expected in expected_options:
            assert expected in option_names, f"Expected option not found: {expected}"



class TestAddOptionsToParser:
    """Tests for add_options_to_parser function"""

    def test_adds_all_options(self):
        """Should add all options to argparse parser"""
        import argparse

        parser = argparse.ArgumentParser()
        add_options_to_parser(parser)

        # Parse empty args to get defaults
        args = parser.parse_args([])

        # Check that all options are added
        for opt in CLI_OPTIONS:
            python_name = opt["python_name"]
            assert hasattr(args, python_name.replace("-", "_")), f"Option {python_name} not added to parser"

    def test_boolean_options_default_to_false(self):
        """Boolean options should default to False"""
        import argparse

        parser = argparse.ArgumentParser()
        add_options_to_parser(parser)
        args = parser.parse_args([])

        for opt in CLI_OPTIONS:
            if opt["type"] == "boolean":
                python_name = opt["python_name"].replace("-", "_")
                assert getattr(args, python_name) is False, f"Boolean option {python_name} should default to False"

    def test_string_options_default_to_none(self):
        """String options should default to None"""
        import argparse

        parser = argparse.ArgumentParser()
        add_options_to_parser(parser)
        args = parser.parse_args([])

        for opt in CLI_OPTIONS:
            if opt["type"] == "string":
                python_name = opt["python_name"].replace("-", "_")
                assert getattr(args, python_name) is None, f"String option {python_name} should default to None"

    def test_short_options_work(self):
        """Short option flags should work"""
        import argparse

        parser = argparse.ArgumentParser()
        add_options_to_parser(parser)

        # Test with -o (short for --output-dir)
        args = parser.parse_args(["-o", "/output"])
        assert args.output_dir == "/output"

        # Test with -f (short for --format)
        args = parser.parse_args(["-f", "json"])
        assert args.format == "json"

        # Test with -q (short for --quiet)
        args = parser.parse_args(["-q"])
        assert args.quiet is True

    def test_long_options_work(self):
        """Long option flags should work"""
        import argparse

        parser = argparse.ArgumentParser()
        add_options_to_parser(parser)

        args = parser.parse_args(["--output-dir", "/output", "--format", "json,markdown", "--quiet"])
        assert args.output_dir == "/output"
        assert args.format == "json,markdown"
        assert args.quiet is True
