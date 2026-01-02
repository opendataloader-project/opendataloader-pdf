"""Centralised definitions for available PDF parsing engines."""

from __future__ import annotations

from typing import Callable, Dict

import pdf_parser_opendataloader as opendataloader

EngineHandler = Callable[..., None]


ENGINES: Dict[str, str] = {
    "opendataloader": "local",
}


ENGINE_DISPATCH: Dict[str, EngineHandler] = {
    "opendataloader": opendataloader.to_markdown,
}
