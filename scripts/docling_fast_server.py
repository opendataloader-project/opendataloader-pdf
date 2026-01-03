#!/usr/bin/env python3
"""Fast docling server using DocumentConverter singleton.

A lightweight FastAPI server that provides the same API as docling-serve
but with significantly better performance (3.3x faster) by:
1. Using a DocumentConverter singleton (no per-request initialization)
2. Minimal HTTP overhead

Usage:
    python scripts/docling_fast_server.py [--port PORT] [--host HOST]

    # Default: http://localhost:5002
    python scripts/docling_fast_server.py

    # Custom port
    python scripts/docling_fast_server.py --port 5003

API Endpoints:
    GET  /health              - Health check
    POST /v1/convert/file     - Convert PDF (docling-serve compatible)

The /v1/convert/file endpoint accepts the same parameters as docling-serve:
    - files: PDF file (multipart/form-data)
    - to_formats: Output formats (md, json, html)
    - page_range: Page range to process (optional)

Note: OCR and table structure detection are always enabled (fixed at startup).
"""

import argparse
import os
import sys
import tempfile
import time
from typing import List, Optional

import uvicorn
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

# Docling imports
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import (
    EasyOcrOptions,
    PdfPipelineOptions,
    TableFormerMode,
    TableStructureOptions,
)
from docling.document_converter import DocumentConverter, PdfFormatOption

# Configuration
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 5002

app = FastAPI(
    title="Docling Fast Server",
    description="Fast PDF conversion using docling SDK with singleton pattern",
    version="1.0.0",
)

# Global converter instance (initialized on startup)
converter: Optional[DocumentConverter] = None


def create_converter(do_ocr: bool = True, do_table_structure: bool = True) -> DocumentConverter:
    """Create a DocumentConverter with the specified options."""
    pipeline_options = PdfPipelineOptions(
        do_ocr=do_ocr,
        do_table_structure=do_table_structure,
        ocr_options=EasyOcrOptions(force_full_page_ocr=False),
        table_structure_options=TableStructureOptions(
            mode=TableFormerMode.ACCURATE
        ),
    )

    return DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
        }
    )


@app.on_event("startup")
async def startup_event():
    """Initialize DocumentConverter on server startup."""
    global converter
    print("Initializing DocumentConverter...", flush=True)
    start = time.perf_counter()
    converter = create_converter()
    elapsed = time.perf_counter() - start
    print(f"DocumentConverter initialized in {elapsed:.2f}s", flush=True)


@app.get("/health")
def health():
    """Health check endpoint."""
    return {"status": "ok"}


@app.post("/v1/convert/file")
async def convert_file(
    files: UploadFile = File(...),
    to_formats: List[str] = Form(default=["md", "json"]),
    page_ranges: Optional[str] = Form(default=None),
):
    """Convert PDF file to markdown, JSON, and/or HTML.

    This endpoint is compatible with docling-serve API.

    Note: OCR and table structure detection are always enabled.
    The DocumentConverter is initialized once at startup with fixed options
    for optimal performance.

    Args:
        files: The PDF file to convert
        to_formats: Output formats (md, json, html)
        page_ranges: Page range string "start-end" (e.g., "1-5") (optional)

    Returns:
        JSON response with document content in the requested formats.
    """
    global converter

    if converter is None:
        return JSONResponse(
            {"status": "failure", "errors": ["Server not initialized"]},
            status_code=503,
        )

    # Parse page_ranges string to tuple
    page_range_tuple = None
    if page_ranges:
        try:
            parts = page_ranges.split("-")
            if len(parts) == 2:
                page_range_tuple = (int(parts[0]), int(parts[1]))
        except ValueError:
            pass

    # Save uploaded file to temp location
    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        content = await files.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        start = time.perf_counter()
        if page_range_tuple:
            result = converter.convert(tmp_path, page_range=page_range_tuple)
        else:
            result = converter.convert(tmp_path)
        processing_time = time.perf_counter() - start

        # Export to requested formats
        md_content = ""
        json_content = None
        html_content = ""

        if "md" in to_formats:
            md_content = result.document.export_to_markdown()

        if "json" in to_formats:
            # Export to dict for JSON response
            json_content = result.document.export_to_dict()

        if "html" in to_formats:
            html_content = result.document.export_to_html()

        # Build response compatible with docling-serve format
        response = {
            "status": "success",
            "document": {
                "md_content": md_content,
                "json_content": json_content,
                "html_content": html_content,
            },
            "processing_time": processing_time,
        }

        return JSONResponse(response)

    except Exception as e:
        return JSONResponse(
            {
                "status": "failure",
                "errors": [str(e)],
            },
            status_code=500,
        )
    finally:
        os.unlink(tmp_path)


def main():
    """Run the server."""
    parser = argparse.ArgumentParser(description="Docling Fast Server")
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help=f"Host to bind to (default: {DEFAULT_HOST})",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"Port to bind to (default: {DEFAULT_PORT})",
    )
    parser.add_argument(
        "--log-level",
        default="info",
        choices=["debug", "info", "warning", "error"],
        help="Log level (default: info)",
    )
    args = parser.parse_args()

    print(f"Starting Docling Fast Server on http://{args.host}:{args.port}", flush=True)
    uvicorn.run(
        app,
        host=args.host,
        port=args.port,
        log_level=args.log_level,
    )


if __name__ == "__main__":
    main()
