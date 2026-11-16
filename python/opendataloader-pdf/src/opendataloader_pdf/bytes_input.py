"""
Convert the bytes of a PDF and return the output as a string.
"""

import os
import tempfile
from pathlib import Path
from typing import Union

from .convert_generated import convert

# The output formats that convert_bytes() returns, and the extension of their output file.
_FORMATS = {"markdown": "md", "text": "txt", "json": "json", "html": "html"}
# The options that convert_bytes() sets itself.
_SET_BY_BYTES_INPUT = ("input_path", "output_dir", "to_stdout", "image_dir")
_IMAGE_OUTPUTS = ("off", "embedded")


def convert_bytes(
    data: Union[bytes, bytearray, memoryview],
    format: str = "markdown",
    name: str = "document",
    image_output: str = "off",
    **options,
) -> str:
    """Convert the bytes of one PDF, and return the output as a string.

    The bytes go to ``<name>.pdf`` in a new temporary folder that only the current
    user can open. convert() runs on that file, and convert_bytes() returns the
    content of the output file. The folder is removed after the run, also after an
    error. Each call starts one Java process, as each call of convert() does.

    Args:
        data: The bytes of the PDF.
        format: One output format: "markdown", "text", "json", or "html".
        name: The base name of the PDF and of its output, for example the
            "file name" in the JSON. A file name, without a folder.
        image_output: "off" (no images) or "embedded" (Base64 data URIs).
            "external" is not possible: the temporary folder is removed.
        **options: The other options of convert(), for example password, pages,
            or quiet.

    Returns:
        The output in the given format.

    Raises:
        TypeError: If data is not bytes, a bytearray, or a memoryview.
        ValueError: If data is empty, or an option cannot be used with bytes input.
        subprocess.CalledProcessError: If the conversion fails, as convert() raises it.
        RuntimeError: If the conversion writes no output.
    """
    if not isinstance(data, (bytes, bytearray, memoryview)):
        raise TypeError(
            f"data must be bytes, a bytearray, or a memoryview, not {type(data).__name__}"
        )
    view = memoryview(data)
    if view.nbytes == 0:
        raise ValueError("data is empty")
    for key in _SET_BY_BYTES_INPUT:
        if key in options:
            raise ValueError(f"convert_bytes() sets {key} itself: remove the {key} option")
    if not isinstance(format, str) or format not in _FORMATS:
        raise ValueError(
            f"format must be one of {', '.join(_FORMATS)} (one format), not {format!r}"
        )
    if image_output not in _IMAGE_OUTPUTS:
        raise ValueError(
            f"image_output must be off or embedded with bytes input, not {image_output!r}"
        )
    if not isinstance(name, str) or not name or Path(name).name != name or name in (".", ".."):
        raise ValueError(f"name must be a file name without a folder, not {name!r}")

    with tempfile.TemporaryDirectory(prefix="opendataloader-pdf-") as folder:
        pdf = Path(folder) / f"{name}.pdf"
        descriptor = os.open(
            pdf, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0), 0o600
        )
        with os.fdopen(descriptor, "wb") as file:
            # A strided view (for example view[::2]) is not one block of memory: copy its bytes.
            file.write(view if view.c_contiguous else view.tobytes())
        out = Path(folder) / "out"
        convert(
            input_path=str(pdf),
            output_dir=str(out),
            format=format,
            image_output=image_output,
            **options,
        )
        result = out / f"{name}.{_FORMATS[format]}"
        if not result.is_file():
            raise RuntimeError(f"the conversion wrote no output ({result.name})")
        return result.read_text(encoding="utf-8")
