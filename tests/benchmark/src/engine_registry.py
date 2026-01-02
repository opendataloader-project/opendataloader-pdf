"""Centralised definitions for available PDF parsing engines."""

from __future__ import annotations

from typing import Callable, Dict

import pdf_parser_opendataloader as opendataloader
import pdf_parser_opendataloader_hybrid as opendataloader_hybrid

EngineHandler = Callable[..., None]


ENGINES: Dict[str, str] = {
    "opendataloader": "local",
    "opendataloader-hybrid-docling": "local+docling",
}


ENGINE_DISPATCH: Dict[str, EngineHandler] = {
    "opendataloader": opendataloader.to_markdown,
    "opendataloader-hybrid-docling": opendataloader_hybrid.to_markdown,
}
