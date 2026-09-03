# Hancom AI Mock Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hancom AI 서버 부재 시 transformer 개발/디버그 사이클을 유지하기 위한 fixture-replay HTTP mock 서버 구현 + 클라이언트(`HancomAIClient.java`) REQUEST_ID 규약 패치.

**Architecture:** Python(FastAPI) 로컬 서버가 200개 벤치 PDF의 SHA256을 인덱싱하고, PDF 입력은 SHA256 룩업, 이미지 입력은 클라이언트가 REQUEST_ID에 인코딩한 `(sha_short, page, obj, module)`로 룩업, pdf2img는 PyMuPDF로 동적 300DPI 렌더링 + base64 응답. 클라이언트는 `convert()` 진입 시 PDF SHA256을 캐시하고 모든 호출에 REQUEST_ID 규약 적용.

**Tech Stack:**
- Mock 서버: Python 3.10+, FastAPI, uvicorn, PyMuPDF, pytest, httpx (TestClient)
- Client 패치: Java 8+, OkHttp, Jackson, Maven, JUnit 5
- 데이터: `bundolee/kb-odl/raw/4-기술/2026-04-16_Q2-기술-ctx_hancom-ai-a11y_출력데이터-스키마/`
- PDFs: `opendataloader-project/opendataloader-bench/pdfs/`

**Spec:** `bundolee/kb-odl/raw/4-기술/2026-04-18_Q2-DEV-02-Code_hancom-ai-mock-server-design.md` (commit `dfe0671`)

---

## File Structure

### Mock 서버 (신규)

`opendataloader-project/opendataloader-pdfua/scripts/mock_server/`

| 파일 | 책임 |
|---|---|
| `mock_server/__init__.py` | 빈 패키지 마커 |
| `mock_server/__main__.py` | CLI 엔트리포인트 (`python -m mock_server ...`) |
| `mock_server/index.py` | 부팅 시 `--pdf-dir` 스캔 → `{sha256: (basename, path)}` 인덱스 |
| `mock_server/request_id.py` | REQUEST_ID 정규식 파싱 (`odl-{sha12}-p{n}-o{n}-{module}`) |
| `mock_server/lookup.py` | 모듈명/입력타입에 따른 fixture 경로 결정 + JSON 로드. `FixtureMiss` 예외. |
| `mock_server/pdf_render.py` | PyMuPDF로 PDF 페이지 → PNG 300DPI bytes |
| `mock_server/server.py` | FastAPI app: `/ping`, `/hocr/sdk`, `/support/pdf2img` (각각 `/api/v1/` 변형 별칭) |
| `mock_server/tests/__init__.py` | |
| `mock_server/tests/conftest.py` | 테스트 fixture (샘플 PDF dir, 샘플 fixture dir) |
| `mock_server/tests/test_index.py` | |
| `mock_server/tests/test_request_id.py` | |
| `mock_server/tests/test_lookup.py` | |
| `mock_server/tests/test_pdf_render.py` | |
| `mock_server/tests/test_server.py` | |
| `mock_server/pyproject.toml` | 의존성 + pytest 설정 |
| `mock_server/README.md` | 실행법 + 디버깅 + client 패치 메모 |

### Client 패치 (수정)

`opendataloader-project/opendataloader-pdf/java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HancomAIClient.java`

`opendataloader-project/opendataloader-pdf/java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/hybrid/HancomAIClientRequestIdTest.java` (신규)

---

## Phase 1 — Mock Server (10 tasks)

### Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/pyproject.toml`
- Create: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/__init__.py`
- Create: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/tests/__init__.py`
- Create: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/README.md`

- [ ] **Step 1: pyproject.toml 작성**

```toml
[project]
name = "hancom-ai-mock-server"
version = "0.1.0"
requires-python = ">=3.10"
dependencies = [
    "fastapi>=0.110",
    "uvicorn>=0.27",
    "pymupdf>=1.24",
    "python-multipart>=0.0.9",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "httpx>=0.27",
]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

- [ ] **Step 2: 빈 패키지 마커 + README 초안**

`mock_server/__init__.py`:
```python
"""Hancom AI HOCR SDK fixture-replay mock server."""
```

`mock_server/tests/__init__.py`:
```python
```

`mock_server/README.md`:
```markdown
# Hancom AI Mock Server

Fixture-replay mock for `HancomAIClient`. See spec at
`bundolee/kb-odl/raw/4-기술/2026-04-18_Q2-DEV-02-Code_hancom-ai-mock-server-design.md`.

## Run

```bash
cd opendataloader-pdfua/scripts/mock_server
pip install -e ".[dev]"
python -m mock_server \
  --pdf-dir /path/to/opendataloader-bench/pdfs \
  --fixture-dir /path/to/kb-odl/raw/4-기술/2026-04-16_Q2-기술-ctx_hancom-ai-a11y_출력데이터-스키마 \
  --port 18008
```

## Tests

```bash
pytest -v
```
```

- [ ] **Step 3: 디렉토리 구조 확인**

Run: `ls opendataloader-project/opendataloader-pdfua/scripts/mock_server/`
Expected: `pyproject.toml  README.md  __init__.py  tests/`

- [ ] **Step 4: Commit**

```bash
cd opendataloader-project/opendataloader-pdfua
git add scripts/mock_server/
git commit -m "feat(mock-server): scaffold project structure"
```

---

### Task 2: REQUEST_ID 파서 (TDD)

**Files:**
- Test: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/tests/test_request_id.py`
- Create: `opendataloader-project/opendataloader-pdfua/scripts/mock_server/mock_server/request_id.py`

- [ ] **Step 1: 실패하는 테스트 작성**

`tests/test_request_id.py`:
```python
import pytest
from mock_server.request_id import parse_request_id, RequestIdParts


def test_parse_caption():
    parts = parse_request_id("odl-a3f1c9d2e7b8-p0-o5-caption")
    assert parts == RequestIdParts(sha_short="a3f1c9d2e7b8", page=0, obj=5, module="caption")


def test_parse_chart():
    parts = parse_request_id("odl-deadbeef0000-p3-o12-chart")
    assert parts == RequestIdParts(sha_short="deadbeef0000", page=3, obj=12, module="chart")


def test_parse_tsr():
    parts = parse_request_id("odl-aabbccddeeff-p0-o0-tsr")
    assert parts.module == "tsr"


def test_parse_invalid_returns_none():
    assert parse_request_id("odl-DOCUMENT_LAYOUT_WITH_OCR") is None
    assert parse_request_id("garbage") is None
    assert parse_request_id("") is None
    assert parse_request_id("odl-a3f1-p0-o0-unknown") is None  # bad module


def test_parse_pdf_module_request_id_returns_none():
    # PDF modules use REQUEST_ID for tracing only; mock matches via SHA256 of bytes.
    # Parser returns None for these so caller falls back to PDF lookup path.
    assert parse_request_id("odl-a3f1c9d2e7b8-dla-ocr") is None
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd opendataloader-project/opendataloader-pdfua/scripts/mock_server && pytest tests/test_request_id.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'mock_server.request_id'`

- [ ] **Step 3: 최소 구현**

`mock_server/request_id.py`:
```python
"""Parse client REQUEST_ID for image-input module lookup."""
import re
from dataclasses import dataclass
from typing import Optional

_PATTERN = re.compile(
    r"^odl-(?P<sha>[0-9a-f]{12})-p(?P<page>\d+)-o(?P<obj>\d+)-(?P<module>caption|chart|tsr)$"
)


@dataclass(frozen=True)
class RequestIdParts:
    sha_short: str
    page: int
    obj: int
    module: str


def parse_request_id(request_id: str) -> Optional[RequestIdParts]:
    if not request_id:
        return None
    m = _PATTERN.match(request_id)
    if not m:
        return None
    return RequestIdParts(
        sha_short=m.group("sha"),
        page=int(m.group("page")),
        obj=int(m.group("obj")),
        module=m.group("module"),
    )
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `pytest tests/test_request_id.py -v`
Expected: 5 passed

- [ ] **Step 5: Commit**

```bash
git add scripts/mock_server/mock_server/request_id.py scripts/mock_server/tests/test_request_id.py
git commit -m "feat(mock-server): REQUEST_ID parser for image module lookup"
```

---

### Task 3: SHA256 인덱스 빌더 (TDD)

**Files:**
- Test: `scripts/mock_server/tests/test_index.py`
- Test: `scripts/mock_server/tests/conftest.py`
- Create: `scripts/mock_server/mock_server/index.py`

- [ ] **Step 1: conftest fixture 작성**

`tests/conftest.py`:
```python
import hashlib
import shutil
from pathlib import Path
import pytest


@pytest.fixture
def sample_pdf_dir(tmp_path):
    """Create a tiny PDF dir with 3 fake PDFs (just bytes, not parsed by index)."""
    d = tmp_path / "pdfs"
    d.mkdir()
    for i in range(1, 4):
        (d / f"pdf{i:03d}.pdf").write_bytes(f"FAKE_PDF_CONTENT_{i}".encode())
    return d


def sha256_hex(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()
```

- [ ] **Step 2: 실패하는 테스트 작성**

`tests/test_index.py`:
```python
from mock_server.index import build_pdf_index, PdfIndexEntry
from .conftest import sha256_hex


def test_build_index_three_pdfs(sample_pdf_dir):
    index = build_pdf_index(sample_pdf_dir)
    assert len(index) == 3
    expected_sha = sha256_hex(b"FAKE_PDF_CONTENT_1")
    assert expected_sha in index
    entry = index[expected_sha]
    assert entry.basename == "pdf001"
    assert entry.path == sample_pdf_dir / "pdf001.pdf"


def test_build_index_empty_dir(tmp_path):
    index = build_pdf_index(tmp_path)
    assert index == {}


def test_build_index_sha_short_lookup(sample_pdf_dir):
    index = build_pdf_index(sample_pdf_dir)
    full_sha = next(iter(index))
    short = full_sha[:12]
    entry = index.find_by_short(short)
    assert entry is not None
    assert entry.basename in {"pdf001", "pdf002", "pdf003"}
```

- [ ] **Step 3: 테스트 실행 (실패 확인)**

Run: `pytest tests/test_index.py -v`
Expected: FAIL — module not found

- [ ] **Step 4: 최소 구현**

`mock_server/index.py`:
```python
"""Build SHA256 index of benchmark PDFs at server boot."""
import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional


@dataclass(frozen=True)
class PdfIndexEntry:
    basename: str
    path: Path


class PdfIndex(dict):
    """Dict {full_sha256: PdfIndexEntry} with helper for short-prefix lookup."""

    def find_by_short(self, sha_short: str) -> Optional[PdfIndexEntry]:
        for full_sha, entry in self.items():
            if full_sha.startswith(sha_short):
                return entry
        return None


def build_pdf_index(pdf_dir: Path) -> PdfIndex:
    index = PdfIndex()
    for path in sorted(Path(pdf_dir).glob("*.pdf")):
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        index[sha] = PdfIndexEntry(basename=path.stem, path=path)
    return index
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `pytest tests/test_index.py -v`
Expected: 3 passed

- [ ] **Step 6: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): SHA256 PDF index with short-prefix lookup"
```

---

### Task 4: Lookup 로직 (TDD)

**Files:**
- Test: `scripts/mock_server/tests/test_lookup.py`
- Test: `scripts/mock_server/tests/conftest.py` (확장)
- Create: `scripts/mock_server/mock_server/lookup.py`

- [ ] **Step 1: conftest 확장 — 가짜 fixture dir**

`tests/conftest.py` 끝에 추가:
```python
import json


@pytest.fixture
def sample_fixture_dir(tmp_path):
    """Mimic the recorded data layout with one entry per module."""
    root = tmp_path / "fixtures"
    for sub in ["DLA_OCR", "DLA", "OCR", "TSR", "TSR_regionlist", "FIGURE"]:
        (root / sub).mkdir(parents=True)
    (root / "DLA_OCR" / "pdf001.json").write_text(json.dumps({"module": "DLA_OCR", "id": "pdf001"}))
    (root / "DLA" / "pdf001.json").write_text(json.dumps({"module": "DLA"}))
    (root / "OCR" / "pdf001.json").write_text(json.dumps({"module": "OCR"}))
    (root / "TSR" / "pdf002_p0_o0.json").write_text(json.dumps({"module": "TSR", "table": True}))
    (root / "TSR_regionlist" / "pdf003_p0_o0.json").write_text(json.dumps({"module": "TSR_regionlist"}))
    (root / "FIGURE" / "pdf001_p0_o5_caption.json").write_text(json.dumps({"caption": "test"}))
    (root / "FIGURE" / "pdf001_p0_o5_chart.json").write_text(json.dumps({"understanding": "TITLE | <0x0A> 1 | 2"}))
    return root
```

- [ ] **Step 2: 실패 테스트 작성**

`tests/test_lookup.py`:
```python
import pytest
from mock_server.lookup import (
    lookup_pdf_module, lookup_image_module, FixtureMiss,
    MODULE_TO_DIR,
)


def test_pdf_module_lookup_dla_ocr(sample_fixture_dir):
    data = lookup_pdf_module(sample_fixture_dir, "DOCUMENT_LAYOUT_WITH_OCR", "pdf001")
    assert data == {"module": "DLA_OCR", "id": "pdf001"}


def test_pdf_module_lookup_dla(sample_fixture_dir):
    data = lookup_pdf_module(sample_fixture_dir, "DOCUMENT_LAYOUT_ANALYSIS", "pdf001")
    assert data["module"] == "DLA"


def test_pdf_module_lookup_unknown_module(sample_fixture_dir):
    with pytest.raises(FixtureMiss) as exc:
        lookup_pdf_module(sample_fixture_dir, "BOGUS_MODULE", "pdf001")
    assert "BOGUS_MODULE" in str(exc.value)


def test_pdf_module_lookup_missing_basename(sample_fixture_dir):
    with pytest.raises(FixtureMiss):
        lookup_pdf_module(sample_fixture_dir, "DOCUMENT_LAYOUT_WITH_OCR", "pdf999")


def test_image_module_lookup_caption(sample_fixture_dir):
    data = lookup_image_module(sample_fixture_dir, "caption", "pdf001", page=0, obj=5)
    assert data == {"caption": "test"}


def test_image_module_lookup_chart(sample_fixture_dir):
    data = lookup_image_module(sample_fixture_dir, "chart", "pdf001", page=0, obj=5)
    assert data["understanding"].startswith("TITLE")


def test_image_module_lookup_tsr_primary(sample_fixture_dir):
    data = lookup_image_module(sample_fixture_dir, "tsr", "pdf002", page=0, obj=0)
    assert data["table"] is True


def test_image_module_lookup_tsr_regionlist_fallback(sample_fixture_dir):
    data = lookup_image_module(sample_fixture_dir, "tsr", "pdf003", page=0, obj=0)
    assert data["module"] == "TSR_regionlist"


def test_image_module_lookup_missing(sample_fixture_dir):
    with pytest.raises(FixtureMiss):
        lookup_image_module(sample_fixture_dir, "caption", "pdf001", page=99, obj=99)


def test_module_to_dir_mapping():
    assert MODULE_TO_DIR["DOCUMENT_LAYOUT_WITH_OCR"] == "DLA_OCR"
    assert MODULE_TO_DIR["TABLE_STRUCTURE_RECOGNITION"] == "TSR"
```

- [ ] **Step 3: 실패 확인**

Run: `pytest tests/test_lookup.py -v`
Expected: FAIL — module not found

- [ ] **Step 4: 구현**

`mock_server/lookup.py`:
```python
"""Map (module, key) → fixture JSON path and load. Raises FixtureMiss on miss."""
import json
from pathlib import Path
from typing import Any


class FixtureMiss(Exception):
    """Raised when no fixture file matches the request."""


MODULE_TO_DIR = {
    "DOCUMENT_LAYOUT_WITH_OCR": "DLA_OCR",
    "DOCUMENT_LAYOUT_ANALYSIS": "DLA",
    "TEXT_RECOGNITION": "OCR",
    "TABLE_STRUCTURE_RECOGNITION": "TSR",
}


def lookup_pdf_module(fixture_dir: Path, module_name: str, basename: str) -> Any:
    sub = MODULE_TO_DIR.get(module_name)
    if sub is None:
        raise FixtureMiss(f"unknown PDF-input module: {module_name}")
    path = Path(fixture_dir) / sub / f"{basename}.json"
    if not path.exists():
        raise FixtureMiss(f"no fixture at {sub}/{basename}.json")
    return json.loads(path.read_text())


def lookup_image_module(
    fixture_dir: Path, module_short: str, basename: str, page: int, obj: int
) -> Any:
    if module_short in {"caption", "chart"}:
        path = Path(fixture_dir) / "FIGURE" / f"{basename}_p{page}_o{obj}_{module_short}.json"
        if not path.exists():
            raise FixtureMiss(f"no FIGURE fixture: {path.name}")
        return json.loads(path.read_text())
    if module_short == "tsr":
        primary = Path(fixture_dir) / "TSR" / f"{basename}_p{page}_o{obj}.json"
        fallback = Path(fixture_dir) / "TSR_regionlist" / f"{basename}_p{page}_o{obj}.json"
        for candidate in (primary, fallback):
            if candidate.exists():
                return json.loads(candidate.read_text())
        raise FixtureMiss(f"no TSR fixture: {basename}_p{page}_o{obj}.json (TSR/ or TSR_regionlist/)")
    raise FixtureMiss(f"unknown image module: {module_short}")
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `pytest tests/test_lookup.py -v`
Expected: 10 passed

- [ ] **Step 6: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): lookup with TSR_regionlist fallback"
```

---

### Task 5: PDF Renderer (TDD)

**Files:**
- Test: `scripts/mock_server/tests/test_pdf_render.py`
- Create: `scripts/mock_server/mock_server/pdf_render.py`

- [ ] **Step 1: 실패 테스트**

`tests/test_pdf_render.py`:
```python
import io
import pytest
import fitz  # PyMuPDF
from mock_server.pdf_render import render_page_png


@pytest.fixture
def real_pdf(tmp_path):
    """Create a minimal 2-page PDF using PyMuPDF itself."""
    doc = fitz.open()
    doc.new_page(width=612, height=792)  # 1 page
    doc.new_page(width=612, height=792)  # 2 pages
    p = tmp_path / "minimal.pdf"
    doc.save(p)
    doc.close()
    return p


def test_render_first_page_returns_png_bytes(real_pdf):
    png_bytes = render_page_png(real_pdf, page_index=0, dpi=300)
    assert png_bytes[:8] == b"\x89PNG\r\n\x1a\n"


def test_render_dimensions_300dpi(real_pdf):
    from PIL import Image
    png_bytes = render_page_png(real_pdf, page_index=0, dpi=300)
    img = Image.open(io.BytesIO(png_bytes))
    # 612pt at 300dpi / 72pt-per-inch = 2550px (±1 for rounding)
    assert abs(img.width - 2550) <= 2
    assert abs(img.height - 3300) <= 2


def test_render_out_of_range_raises(real_pdf):
    with pytest.raises(IndexError):
        render_page_png(real_pdf, page_index=99)
```

Note: Pillow is a transitive dep of PyMuPDF; if not, add to dev deps.

- [ ] **Step 2: 실패 확인**

Run: `pytest tests/test_pdf_render.py -v`
Expected: FAIL — module not found

- [ ] **Step 3: 구현**

`mock_server/pdf_render.py`:
```python
"""Render a PDF page to PNG bytes using PyMuPDF."""
from pathlib import Path
import fitz


def render_page_png(pdf_path: Path, page_index: int, dpi: int = 300) -> bytes:
    doc = fitz.open(str(pdf_path))
    try:
        if page_index < 0 or page_index >= doc.page_count:
            raise IndexError(f"page_index {page_index} out of range (0..{doc.page_count - 1})")
        page = doc.load_page(page_index)
        zoom = dpi / 72.0
        matrix = fitz.Matrix(zoom, zoom)
        pixmap = page.get_pixmap(matrix=matrix, alpha=False)
        return pixmap.tobytes("png")
    finally:
        doc.close()
```

- [ ] **Step 4: pyproject.toml dev deps에 Pillow 추가 (테스트용)**

Edit `scripts/mock_server/pyproject.toml`, replace `[project.optional-dependencies]` block with:
```toml
[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "httpx>=0.27",
    "pillow>=10.0",
]
```

Reinstall: `pip install -e ".[dev]"`

- [ ] **Step 5: 테스트 통과 확인**

Run: `pytest tests/test_pdf_render.py -v`
Expected: 3 passed

- [ ] **Step 6: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): PyMuPDF page renderer at 300dpi"
```

---

### Task 6: FastAPI Server — /ping + /hocr/sdk PDF 분기 (TDD)

**Files:**
- Test: `scripts/mock_server/tests/test_server.py`
- Create: `scripts/mock_server/mock_server/server.py`

- [ ] **Step 1: 실패 테스트 (ping + DLA_OCR happy path + unknown PDF)**

`tests/test_server.py`:
```python
import hashlib
import json
import pytest
from fastapi.testclient import TestClient
from mock_server.server import create_app


@pytest.fixture
def client(sample_pdf_dir, sample_fixture_dir):
    # Make sample_fixture_dir match sample_pdf_dir basenames (pdf001..pdf003)
    # sample_fixture_dir already has pdf001/pdf002/pdf003 entries; OK.
    app = create_app(pdf_dir=sample_pdf_dir, fixture_dir=sample_fixture_dir)
    return TestClient(app)


def test_ping(client):
    r = client.get("/ping")
    assert r.status_code == 200


def test_ping_via_v1_alias(client):
    r = client.get("/api/v1/ping")
    assert r.status_code == 200


def test_dla_ocr_happy_path(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf001.pdf").read_bytes()
    r = client.post(
        "/hocr/sdk",
        data={
            "REQUEST_ID": "odl-anything-dla-ocr",
            "OPEN_API_NAME": "DOCUMENT_LAYOUT_WITH_OCR",
            "DATA_FORMAT": "pdf",
        },
        files={"FILE": ("document.pdf", pdf_bytes, "application/pdf")},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["SUCCESS"] is True
    assert body["MSG"] == "SUCCESS"
    assert body["RESULT"] == {"module": "DLA_OCR", "id": "pdf001"}


def test_dla_ocr_via_v1_alias(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf001.pdf").read_bytes()
    r = client.post(
        "/api/v1/hocr/sdk",
        data={"REQUEST_ID": "x", "OPEN_API_NAME": "DOCUMENT_LAYOUT_WITH_OCR", "DATA_FORMAT": "pdf"},
        files={"FILE": ("document.pdf", pdf_bytes, "application/pdf")},
    )
    assert r.status_code == 200
    assert r.json()["SUCCESS"] is True


def test_unknown_pdf_returns_fixture_miss(client):
    r = client.post(
        "/hocr/sdk",
        data={"REQUEST_ID": "x", "OPEN_API_NAME": "DOCUMENT_LAYOUT_WITH_OCR", "DATA_FORMAT": "pdf"},
        files={"FILE": ("u.pdf", b"UNKNOWN_PDF_BYTES", "application/pdf")},
    )
    assert r.status_code == 200  # mirror real server semantics
    body = r.json()
    assert body["SUCCESS"] is False
    assert body["MSG"] == "FIXTURE_MISS"
    assert "_mock_hint" in body
    assert "sha256" in body["_mock_hint"]
```

- [ ] **Step 2: 실패 확인**

Run: `pytest tests/test_server.py -v`
Expected: FAIL — module not found

- [ ] **Step 3: 구현**

`mock_server/server.py`:
```python
"""FastAPI app for hancom-ai HOCR SDK mock."""
import hashlib
import logging
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, Form, UploadFile, File, APIRouter

from .index import build_pdf_index, PdfIndex
from .lookup import lookup_pdf_module, lookup_image_module, FixtureMiss
from .request_id import parse_request_id
from .pdf_render import render_page_png

LOGGER = logging.getLogger("mock_server")


def _envelope(request_id: str, success: bool, msg: str, result, hint: Optional[str] = None):
    body = {"REQUEST_ID": request_id, "SUCCESS": success, "MSG": msg, "RESULT": result if isinstance(result, list) else [result]}
    if hint is not None:
        body["_mock_hint"] = hint
    return body


def create_app(pdf_dir: Path, fixture_dir: Path) -> FastAPI:
    pdf_index: PdfIndex = build_pdf_index(Path(pdf_dir))
    LOGGER.info("indexed %d PDFs", len(pdf_index))

    app = FastAPI()
    router = APIRouter()

    @router.get("/ping")
    def ping():
        return {"status": "ok"}

    @router.post("/hocr/sdk")
    async def hocr_sdk(
        REQUEST_ID: str = Form(""),
        OPEN_API_NAME: str = Form(...),
        DATA_FORMAT: str = Form(...),
        FILE: UploadFile = File(...),
    ):
        body = await FILE.read()
        if DATA_FORMAT == "pdf":
            sha = hashlib.sha256(body).hexdigest()
            entry = pdf_index.get(sha)
            if entry is None:
                hint = f"sha256={sha[:12]}... not in {len(pdf_index)}-PDF index"
                LOGGER.warning("FIXTURE_MISS pdf %s module=%s", sha[:12], OPEN_API_NAME)
                return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], hint)
            try:
                data = lookup_pdf_module(Path(fixture_dir), OPEN_API_NAME, entry.basename)
            except FixtureMiss as e:
                LOGGER.warning("FIXTURE_MISS pdf-lookup %s: %s", entry.basename, e)
                return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], str(e))
            return _envelope(REQUEST_ID, True, "SUCCESS", data)
        # image branch (Task 7)
        return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], "image branch not implemented yet")

    app.include_router(router)
    app.include_router(router, prefix="/api/v1")
    return app
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `pytest tests/test_server.py -v`
Expected: 5 passed (ping x2, DLA_OCR happy x2, unknown PDF)

- [ ] **Step 5: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): /ping + /hocr/sdk PDF branch with /api/v1 alias"
```

---

### Task 7: 이미지 분기 추가 (TDD)

**Files:**
- Modify: `scripts/mock_server/mock_server/server.py`
- Modify: `scripts/mock_server/tests/test_server.py`

- [ ] **Step 1: 추가 테스트 작성**

Append to `tests/test_server.py`:
```python
def test_image_caption_happy_path(client, sample_pdf_dir):
    # Compute sha_short for pdf001
    pdf_bytes = (sample_pdf_dir / "pdf001.pdf").read_bytes()
    sha_short = hashlib.sha256(pdf_bytes).hexdigest()[:12]
    r = client.post(
        "/hocr/sdk",
        data={
            "REQUEST_ID": f"odl-{sha_short}-p0-o5-caption",
            "OPEN_API_NAME": "IMAGE_CAPTIONING",
            "DATA_FORMAT": "image",
        },
        files={"FILE": ("crop.png", b"\x89PNGfake", "image/png")},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["SUCCESS"] is True
    assert body["RESULT"][0] == {"caption": "test"}


def test_image_unparsable_request_id(client):
    r = client.post(
        "/hocr/sdk",
        data={
            "REQUEST_ID": "odl-bad",
            "OPEN_API_NAME": "IMAGE_CAPTIONING",
            "DATA_FORMAT": "image",
        },
        files={"FILE": ("crop.png", b"\x89PNGfake", "image/png")},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["SUCCESS"] is False
    assert "REQUEST_ID" in body["_mock_hint"]


def test_image_unknown_pdf_short(client):
    r = client.post(
        "/hocr/sdk",
        data={
            "REQUEST_ID": "odl-ffffffffffff-p0-o0-caption",
            "OPEN_API_NAME": "IMAGE_CAPTIONING",
            "DATA_FORMAT": "image",
        },
        files={"FILE": ("crop.png", b"\x89PNGfake", "image/png")},
    )
    body = r.json()
    assert body["SUCCESS"] is False
    assert "ffffffffffff" in body["_mock_hint"]


def test_image_tsr_regionlist_fallback(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf003.pdf").read_bytes()
    sha_short = hashlib.sha256(pdf_bytes).hexdigest()[:12]
    r = client.post(
        "/hocr/sdk",
        data={
            "REQUEST_ID": f"odl-{sha_short}-p0-o0-tsr",
            "OPEN_API_NAME": "TABLE_STRUCTURE_RECOGNITION",
            "DATA_FORMAT": "image",
        },
        files={"FILE": ("crop.png", b"\x89PNGfake", "image/png")},
    )
    assert r.json()["RESULT"][0]["module"] == "TSR_regionlist"
```

- [ ] **Step 2: 실패 확인**

Run: `pytest tests/test_server.py -v -k image`
Expected: 4 failed (image branch returns FIXTURE_MISS placeholder)

- [ ] **Step 3: 이미지 분기 구현**

In `mock_server/server.py`, replace the image branch placeholder line `# image branch (Task 7)` and the line below it with:

```python
        # image branch
        parts = parse_request_id(REQUEST_ID)
        if parts is None:
            hint = f"REQUEST_ID does not match odl-<sha12>-p<n>-o<n>-<caption|chart|tsr>"
            LOGGER.warning("FIXTURE_MISS bad REQUEST_ID: %s", REQUEST_ID)
            return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], hint)
        entry = pdf_index.find_by_short(parts.sha_short)
        if entry is None:
            hint = f"sha_short={parts.sha_short} not in {len(pdf_index)}-PDF index"
            LOGGER.warning("FIXTURE_MISS unknown sha_short: %s", parts.sha_short)
            return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], hint)
        try:
            data = lookup_image_module(
                Path(fixture_dir), parts.module, entry.basename, parts.page, parts.obj
            )
        except FixtureMiss as e:
            LOGGER.warning("FIXTURE_MISS image-lookup: %s", e)
            return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], str(e))
        return _envelope(REQUEST_ID, True, "SUCCESS", data)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `pytest tests/test_server.py -v`
Expected: 9 passed (5 from Task 6 + 4 new)

- [ ] **Step 5: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): image module branch with REQUEST_ID parsing"
```

---

### Task 8: pdf2img 엔드포인트 (TDD)

**Files:**
- Modify: `scripts/mock_server/mock_server/server.py`
- Modify: `scripts/mock_server/tests/test_server.py`
- Modify: `scripts/mock_server/tests/conftest.py` (real PDF fixture)

- [ ] **Step 1: conftest에 real PDF 추가 + 인덱스에 포함**

Edit `tests/conftest.py`, replace `sample_pdf_dir` with:
```python
@pytest.fixture
def sample_pdf_dir(tmp_path):
    """Create a tiny PDF dir: 3 fake byte files + 1 real PDF for pdf2img tests."""
    import fitz
    d = tmp_path / "pdfs"
    d.mkdir()
    for i in range(1, 4):
        (d / f"pdf{i:03d}.pdf").write_bytes(f"FAKE_PDF_CONTENT_{i}".encode())
    # real 2-page PDF named pdf004.pdf
    doc = fitz.open()
    doc.new_page(width=612, height=792)
    doc.new_page(width=612, height=792)
    doc.save(d / "pdf004.pdf")
    doc.close()
    return d
```

- [ ] **Step 2: 실패 테스트 추가**

Append to `tests/test_server.py`:
```python
import base64


def test_pdf2img_returns_base64_png(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf004.pdf").read_bytes()
    r = client.post(
        "/support/pdf2img",
        data={"REQUEST_ID": "odl-pdf2img-0", "PAGE_INDEX": "0"},
        files={"FILE": ("document.pdf", pdf_bytes, "application/pdf")},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["SUCCESS"] is True
    png_b64 = body["RESULT"][0]["RESULT"]["PAGE_PNG_DATA"]
    assert png_b64
    assert base64.b64decode(png_b64)[:8] == b"\x89PNG\r\n\x1a\n"


def test_pdf2img_via_v1_alias(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf004.pdf").read_bytes()
    r = client.post(
        "/api/v1/support/pdf2img",
        data={"REQUEST_ID": "x", "PAGE_INDEX": "1"},
        files={"FILE": ("d.pdf", pdf_bytes, "application/pdf")},
    )
    assert r.json()["SUCCESS"] is True


def test_pdf2img_unknown_pdf(client):
    r = client.post(
        "/support/pdf2img",
        data={"REQUEST_ID": "x", "PAGE_INDEX": "0"},
        files={"FILE": ("u.pdf", b"NOT_INDEXED", "application/pdf")},
    )
    body = r.json()
    assert body["SUCCESS"] is False
    assert body["MSG"] == "FIXTURE_MISS"


def test_pdf2img_out_of_range(client, sample_pdf_dir):
    pdf_bytes = (sample_pdf_dir / "pdf004.pdf").read_bytes()
    r = client.post(
        "/support/pdf2img",
        data={"REQUEST_ID": "x", "PAGE_INDEX": "99"},
        files={"FILE": ("d.pdf", pdf_bytes, "application/pdf")},
    )
    body = r.json()
    assert body["SUCCESS"] is False
    assert "page_index" in body["_mock_hint"]
```

- [ ] **Step 3: 실패 확인**

Run: `pytest tests/test_server.py -v -k pdf2img`
Expected: 4 failed (404 from FastAPI — endpoint not registered)

- [ ] **Step 4: pdf2img 라우트 추가**

In `mock_server/server.py`, inside `create_app` after the `/hocr/sdk` route, add:
```python
    @router.post("/support/pdf2img")
    async def pdf2img(
        REQUEST_ID: str = Form(""),
        PAGE_INDEX: int = Form(...),
        FILE: UploadFile = File(...),
    ):
        body = await FILE.read()
        sha = hashlib.sha256(body).hexdigest()
        entry = pdf_index.get(sha)
        if entry is None:
            hint = f"sha256={sha[:12]}... not in {len(pdf_index)}-PDF index"
            return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], hint)
        try:
            png_bytes = render_page_png(entry.path, PAGE_INDEX, dpi=300)
        except IndexError as e:
            return _envelope(REQUEST_ID, False, "FIXTURE_MISS", [], str(e))
        import base64
        b64 = base64.b64encode(png_bytes).decode("ascii")
        return _envelope(
            REQUEST_ID, True, "SUCCESS",
            {"RESULT": {"PAGE_PNG_DATA": b64}},
        )
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `pytest tests/test_server.py -v`
Expected: 13 passed

- [ ] **Step 6: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): /support/pdf2img with PyMuPDF dynamic rendering"
```

---

### Task 9: CLI 엔트리포인트

**Files:**
- Create: `scripts/mock_server/mock_server/__main__.py`

- [ ] **Step 1: 구현**

`mock_server/__main__.py`:
```python
"""CLI: python -m mock_server --pdf-dir ... --fixture-dir ... --port 18008"""
import argparse
import logging
import sys
from pathlib import Path

import uvicorn

from .server import create_app


def main(argv=None):
    parser = argparse.ArgumentParser(prog="mock_server")
    parser.add_argument("--pdf-dir", required=True, type=Path)
    parser.add_argument("--fixture-dir", required=True, type=Path)
    parser.add_argument("--port", type=int, default=18008)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    if not args.pdf_dir.is_dir():
        print(f"--pdf-dir not found: {args.pdf_dir}", file=sys.stderr)
        sys.exit(2)
    if not args.fixture_dir.is_dir():
        print(f"--fixture-dir not found: {args.fixture_dir}", file=sys.stderr)
        sys.exit(2)

    app = create_app(pdf_dir=args.pdf_dir, fixture_dir=args.fixture_dir)
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: smoke test — CLI 인자 검증**

Run:
```bash
cd opendataloader-project/opendataloader-pdfua/scripts/mock_server
python -m mock_server --pdf-dir /nonexistent --fixture-dir /nonexistent
```
Expected: exit code 2, stderr `--pdf-dir not found: /nonexistent`

- [ ] **Step 3: 실제 데이터로 부팅 smoke test**

Run (in a separate terminal or background, then kill):
```bash
python -m mock_server \
  --pdf-dir /Users/benedict/Workspace/opendataloader-project/opendataloader-bench/pdfs \
  --fixture-dir /Users/benedict/Workspace/bundolee/kb-odl/raw/4-기술/2026-04-16_Q2-기술-ctx_hancom-ai-a11y_출력데이터-스키마 \
  --port 18008 &
sleep 2
curl -s http://127.0.0.1:18008/ping
kill %1
```
Expected: log `indexed 200 PDFs`, curl returns `{"status":"ok"}`.

- [ ] **Step 4: Commit**

```bash
git add scripts/mock_server/
git commit -m "feat(mock-server): CLI entrypoint with arg validation"
```

---

### Task 10: README 마무리 + E2E 절차 기록

**Files:**
- Modify: `scripts/mock_server/README.md`

- [ ] **Step 1: README 확장**

Replace `mock_server/README.md` with:
```markdown
# Hancom AI Mock Server

Fixture-replay mock for [HancomAIClient.java](../../../opendataloader-pdf/java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HancomAIClient.java).

Spec: `bundolee/kb-odl/raw/4-기술/2026-04-18_Q2-DEV-02-Code_hancom-ai-mock-server-design.md`

## Setup

```bash
cd opendataloader-pdfua/scripts/mock_server
pip install -e ".[dev]"
```

## Run

```bash
python -m mock_server \
  --pdf-dir /Users/benedict/Workspace/opendataloader-project/opendataloader-bench/pdfs \
  --fixture-dir /Users/benedict/Workspace/bundolee/kb-odl/raw/4-기술/2026-04-16_Q2-기술-ctx_hancom-ai-a11y_출력데이터-스키마 \
  --port 18008
```

Expect log: `indexed 200 PDFs, listening on :18008`.

## Endpoints

| Method | Path (and `/api/v1/` alias) | Purpose |
|---|---|---|
| GET | `/ping` | Health check (HTTP 200) |
| POST | `/hocr/sdk` | All 6 modules (DATA_FORMAT distinguishes pdf vs image) |
| POST | `/support/pdf2img` | 300dpi PNG, base64 encoded |

## REQUEST_ID Convention

Image-input modules (TSR / IMAGE_CAPTIONING / CHART_IMAGE_UNDERSTANDING) require:

```
odl-{sha_short}-p{page}-o{obj}-{module_short}
```

- `sha_short` = first 12 hex chars of source PDF SHA256
- `module_short` ∈ {`caption`, `chart`, `tsr`}

PDF-input modules (DLA_OCR / DLA / OCR / TSR-pdf / pdf2img) match via SHA256 of FILE bytes; REQUEST_ID is informational.

The Java client patch (Phase 2 of the plan) builds these IDs automatically.

## End-to-End Smoke Test

After both Phase 1 (server) and Phase 2 (client patch) are merged:

```bash
# Terminal 1
python -m mock_server --pdf-dir ... --fixture-dir ... --port 18008

# Terminal 2 — pdfua against the mock
cd opendataloader-project/opendataloader-pdfua
mvn package -q
java -jar target/*.jar \
  --input /path/to/opendataloader-bench/pdfs/01030000000001.pdf \
  --output /tmp/out \
  --hybrid hancom-ai \
  --hybrid-url http://localhost:18008
```

Expected: tagged PDF appears in `/tmp/out`, server log shows DLA_OCR + pdf2img + TSR + IMAGE_CAPTIONING calls all returning SUCCESS.

## Tests

```bash
pytest -v
```

Expected: ~22 tests pass.

## FIXTURE_MISS Debugging

When `SUCCESS:false`, response includes `_mock_hint`:

- `sha256=... not in 200-PDF index` → input PDF is not in the recorded benchmark set; either add it or use a benchmark PDF.
- `REQUEST_ID does not match odl-<sha12>-p<n>-o<n>-<...>` → client did not apply the REQUEST_ID convention; check Phase 2 patch.
- `no FIXTURE_<...>` → the recorded set lacks this specific page/object; this can happen for new combinations.
```

- [ ] **Step 2: Commit**

```bash
git add scripts/mock_server/README.md
git commit -m "docs(mock-server): README with endpoints, REQUEST_ID convention, E2E smoke"
```

---

## Phase 2 — Client Patch (4 tasks)

### Task 11: Test for REQUEST_ID building (TDD)

**Files:**
- Create: `opendataloader-project/opendataloader-pdf/java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/hybrid/HancomAIClientRequestIdTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`HancomAIClientRequestIdTest.java`:
```java
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HancomAIClientRequestIdTest {

    private MockWebServer server;
    private HancomAIClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(),
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void callModule_request_id_includes_sha_short() throws Exception {
        byte[] pdfBytes = "PDF_CONTENT_FOR_TEST".getBytes();
        String shaShort = sha256Hex(pdfBytes).substring(0, 12);

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"SUCCESS\":true,\"RESULT\":[]}"));

        // invokeCallModule is a package-private test hook added by the patch.
        client.invokeCallModule(pdfBytes, "DOCUMENT_LAYOUT_WITH_OCR");

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("odl-" + shaShort + "-dla-ocr"),
            "REQUEST_ID should include sha_short and module short name; body=" + body);
    }

    @Test
    void callImageCaptioning_request_id_includes_page_obj() throws Exception {
        byte[] pdfBytes = "PDF_CONTENT_FOR_TEST".getBytes();
        String shaShort = sha256Hex(pdfBytes).substring(0, 12);
        client.setSourcePdfShaShort(shaShort); // test hook

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"x\"}]]}"));

        client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, /*pageNum*/ 2, /*objectId*/ 7);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("odl-" + shaShort + "-p2-o7-caption"),
            "image REQUEST_ID format mismatch; body=" + body);
    }

    private static String sha256Hex(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(b));
    }
}
```

- [ ] **Step 2: pom.xml에 mockwebserver 의존성 확인**

Run: `grep -n "mockwebserver" opendataloader-project/opendataloader-pdf/java/opendataloader-pdf-core/pom.xml`
Expected: dependency line exists. If not, add to `pom.xml` `<dependencies>`:
```xml
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>mockwebserver</artifactId>
  <version>4.12.0</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: 실패 확인**

Run:
```bash
cd opendataloader-project/opendataloader-pdf/java
mvn -pl opendataloader-pdf-core test -Dtest=HancomAIClientRequestIdTest
```
Expected: COMPILE FAIL — `invokeCallModule`, `invokeCallImageCaptioning`, `setSourcePdfShaShort` not defined.

- [ ] **Step 4: Commit (test only, expecting fail)**

```bash
cd opendataloader-project/opendataloader-pdf
git add java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/hybrid/HancomAIClientRequestIdTest.java java/opendataloader-pdf-core/pom.xml
git commit -m "test(hybrid): failing test for HancomAIClient REQUEST_ID convention"
```

---

### Task 12: HancomAIClient 패치 — REQUEST_ID 빌드

**Files:**
- Modify: `opendataloader-project/opendataloader-pdf/java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HancomAIClient.java`

- [ ] **Step 1: SHA256 헬퍼 + 인스턴스 필드 + 모듈 단축명 맵 추가**

In `HancomAIClient.java` after the existing instance field declarations (after `private final HybridConfig config;`), add:
```java
    private String sourcePdfShaShort = "unknown";

    private static final java.util.Map<String, String> MODULE_SHORT;
    static {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("DOCUMENT_LAYOUT_WITH_OCR", "dla-ocr");
        m.put("DOCUMENT_LAYOUT_ANALYSIS", "dla");
        m.put("TEXT_RECOGNITION", "ocr");
        m.put("TABLE_STRUCTURE_RECOGNITION", "tsr");
        m.put("IMAGE_CAPTIONING", "caption");
        m.put("CHART_IMAGE_UNDERSTANDING", "chart");
        MODULE_SHORT = java.util.Collections.unmodifiableMap(m);
    }

    private static String sha256ShortHex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "nohash000000";
        }
    }

    // Test hook
    void setSourcePdfShaShort(String s) { this.sourcePdfShaShort = s; }
```

- [ ] **Step 2: `convert(HybridRequest)` 진입에서 SHA 계산**

In `convert(...)` method, immediately after `byte[] pdfBytes = request.getPdfBytes();`, add:
```java
        this.sourcePdfShaShort = sha256ShortHex(pdfBytes);
```

- [ ] **Step 3: `callModule` REQUEST_ID 변경**

In `callModule(byte[] pdfBytes, String moduleName)` method, replace the line:
```java
            .addFormDataPart("REQUEST_ID", "odl-" + moduleName)
```
with:
```java
            .addFormDataPart("REQUEST_ID",
                "odl-" + sourcePdfShaShort + "-" + MODULE_SHORT.getOrDefault(moduleName, moduleName))
```

- [ ] **Step 4: `callModuleImage` 시그니처 + REQUEST_ID 변경**

Replace the entire `callModuleImage` method with:
```java
    private JsonNode callModuleImage(byte[] pngBytes, String moduleName, int pageNum, int objectId) throws IOException {
        String moduleShort = MODULE_SHORT.getOrDefault(moduleName, moduleName);
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId + "-" + moduleShort;
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", moduleName)
            .addFormDataPart("DATA_FORMAT", "image")
            .addFormDataPart("FILE", "crop.png",
                RequestBody.create(pngBytes, MEDIA_TYPE_PNG))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        LOGGER.log(Level.FINE, "Calling Hancom AI module (image): {0} [{1}]",
            new Object[]{moduleName, requestId});

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody respBody = response.body();
                String errorMsg = respBody != null ? respBody.string() : "";
                LOGGER.log(Level.WARNING, "Hancom AI module {0} (image) returned HTTP {1}: {2}",
                    new Object[]{moduleName, response.code(), errorMsg});
                return objectMapper.createArrayNode();
            }

            ResponseBody respBody = response.body();
            if (respBody == null) {
                return objectMapper.createArrayNode();
            }

            JsonNode root = objectMapper.readTree(respBody.string());
            boolean success = root.has("SUCCESS") && root.get("SUCCESS").asBoolean();
            if (!success) {
                LOGGER.log(Level.WARNING, "Hancom AI module {0} (image) returned SUCCESS=false: {1}",
                    new Object[]{moduleName, root.has("MSG") ? root.get("MSG").asText() : ""});
                return objectMapper.createArrayNode();
            }

            JsonNode result = root.get("RESULT");
            return result != null ? result : objectMapper.createArrayNode();
        }
    }
```

Update the single existing call site of `callModuleImage` (line ~410 in original, inside `recognizeTableStructures`) — find:
```java
                    JsonNode tsrResult = callModuleImage(cropPng, "TABLE_STRUCTURE_RECOGNITION");
```
and replace with:
```java
                    int objId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                    JsonNode tsrResult = callModuleImage(cropPng, "TABLE_STRUCTURE_RECOGNITION", pageNum, objId);
```

- [ ] **Step 5: `callImageCaptioning` 시그니처 + REQUEST_ID 변경**

Replace the `callImageCaptioning` method signature and body with:
```java
    private String callImageCaptioning(byte[] pngBytes, int pageNum, int objectId) throws IOException {
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId + "-caption";
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", "IMAGE_CAPTIONING")
            .addFormDataPart("DATA_FORMAT", "image")
            .addFormDataPart("FILE", "figure.png",
                RequestBody.create(pngBytes, MEDIA_TYPE_PNG))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) return null;

            ResponseBody respBody = response.body();
            if (respBody == null) return null;

            JsonNode root = objectMapper.readTree(respBody.string());
            if (!root.has("SUCCESS") || !root.get("SUCCESS").asBoolean()) return null;

            JsonNode result = root.get("RESULT");
            if (result == null || !result.isArray() || result.size() == 0) return null;

            JsonNode page = result.get(0);
            if (page.isArray() && page.size() > 0) page = page.get(0);

            return page.has("caption") ? page.get("caption").asText("") : null;
        }
    }
```

Update the single existing call site (inside `captionFigures`, line ~294 in original) — find:
```java
                    String caption = callImageCaptioning(croppedPng);
```
and replace with:
```java
                    int objIdForCaption = fig.has("object_id") ? fig.get("object_id").asInt() : -1;
                    String caption = callImageCaptioning(croppedPng, pageNum, objIdForCaption);
```

- [ ] **Step 6: `fetchPageImage` REQUEST_ID 강화 (mock 매칭 무관, 디버그 일관성)**

In `fetchPageImage(...)`, replace:
```java
            .addFormDataPart("REQUEST_ID", "odl-pdf2img-" + pageIndex)
```
with:
```java
            .addFormDataPart("REQUEST_ID",
                "odl-" + sourcePdfShaShort + "-pdf2img-p" + pageIndex)
```

- [ ] **Step 7: 테스트 hook 메서드 추가 (package-private)**

At the very bottom of the class (before the closing `}`), add:
```java
    // --- Test hooks (package-private) ---

    void invokeCallModule(byte[] pdfBytes, String moduleName) throws IOException {
        this.sourcePdfShaShort = sha256ShortHex(pdfBytes);
        callModule(pdfBytes, moduleName);
    }

    void invokeCallImageCaptioning(byte[] pngBytes, int pageNum, int objectId) throws IOException {
        callImageCaptioning(pngBytes, pageNum, objectId);
    }
```

- [ ] **Step 8: 컴파일 + 새 테스트 통과 확인**

Run:
```bash
cd opendataloader-project/opendataloader-pdf/java
mvn -pl opendataloader-pdf-core test -Dtest=HancomAIClientRequestIdTest
```
Expected: 2 passed.

- [ ] **Step 9: 기존 테스트도 회귀 없는지 확인**

Run:
```bash
mvn -pl opendataloader-pdf-core test
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 10: Commit**

```bash
cd opendataloader-project/opendataloader-pdf
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HancomAIClient.java
git commit -m "feat(hybrid): HancomAIClient REQUEST_ID convention with sha-short, page, obj

For mock-server fixture lookup. Real Hancom AI server treats REQUEST_ID
as opaque client-defined string; no behavior change there.

Spec: bundolee/kb-odl/.../2026-04-18_Q2-DEV-02-Code_hancom-ai-mock-server-design.md"
```

---

### Task 13: 통합 smoke — pdfua → mock 전체 호출 체인

**Files:** (코드 변경 없음, 검증만)

- [ ] **Step 1: pdf-core 로컬 install**

Run:
```bash
cd opendataloader-project/opendataloader-pdf/java
mvn -pl opendataloader-pdf-core install -DskipTests
```
Expected: BUILD SUCCESS, artifact installed to `~/.m2`.

- [ ] **Step 2: pdfua 빌드**

Run:
```bash
cd opendataloader-project/opendataloader-pdfua
mvn package -DskipTests
```
Expected: BUILD SUCCESS, jar at `target/`.

- [ ] **Step 3: mock 서버 백그라운드 기동**

Run:
```bash
cd opendataloader-project/opendataloader-pdfua/scripts/mock_server
python -m mock_server \
  --pdf-dir /Users/benedict/Workspace/opendataloader-project/opendataloader-bench/pdfs \
  --fixture-dir /Users/benedict/Workspace/bundolee/kb-odl/raw/4-기술/2026-04-16_Q2-기술-ctx_hancom-ai-a11y_출력데이터-스키마 \
  --port 18008 > /tmp/mock-server.log 2>&1 &
sleep 2
curl -sf http://127.0.0.1:18008/ping
```
Expected: `{"status":"ok"}`, log contains `indexed 200 PDFs`.

- [ ] **Step 4: pdfua 실행**

Run:
```bash
mkdir -p /tmp/mock-out
cd opendataloader-project/opendataloader-pdfua
java -jar target/*.jar \
  --input /Users/benedict/Workspace/opendataloader-project/opendataloader-bench/pdfs/01030000000001.pdf \
  --output /tmp/mock-out \
  --hybrid hancom-ai \
  --hybrid-url http://localhost:18008
```
Expected: exit 0, output PDF in `/tmp/mock-out/`, server log shows DLA_OCR + pdf2img + (TSR or IMAGE_CAPTIONING based on document) all SUCCESS.

- [ ] **Step 5: FIXTURE_MISS 의도적 트리거 — 외부 PDF**

Run:
```bash
echo "%PDF-1.4 fake" > /tmp/notindexed.pdf
java -jar opendataloader-project/opendataloader-pdfua/target/*.jar \
  --input /tmp/notindexed.pdf \
  --output /tmp/mock-out2 \
  --hybrid hancom-ai \
  --hybrid-url http://localhost:18008
```
Expected: server log contains `FIXTURE_MISS pdf` warning with sha prefix; pdfua may either fail loud or fall back gracefully — record actual behavior in commit message.

- [ ] **Step 6: 서버 종료 + 로그 보존**

Run:
```bash
kill %1 || pkill -f "python -m mock_server"
cp /tmp/mock-server.log opendataloader-project/opendataloader-pdfua/scripts/mock_server/e2e-smoke.log
```

- [ ] **Step 7: 결과 검토 + commit**

Run:
```bash
ls -la /tmp/mock-out/
head -50 opendataloader-project/opendataloader-pdfua/scripts/mock_server/e2e-smoke.log
```

```bash
cd opendataloader-project/opendataloader-pdfua
git add scripts/mock_server/e2e-smoke.log
git commit -m "chore(mock-server): record e2e smoke log against benchmark PDF #01030000000001"
```

---

### Task 14: 회의록에 완료 보고 추가

**Files:**
- Modify: `bundolee/kb-odl/raw/운영/회의록/2026-04-16_hancom-ai-a11y_2차회의-매핑표-리뷰.md` (다음 액션 표 업데이트) 또는 별도 진행 보고

- [ ] **Step 1: 회의록 다음 액션 표 갱신**

In the meeting notes' "다음 액션" table (around line 63), find rows referencing the blocked development and append a status note. If unsure where, add a new row below the existing table:

```markdown
| 8 | hancom-ai mock 서버 + client REQUEST_ID 패치 완료 | 기술 | 2026-04-18 — transformer 재설계 막힘 해소 |
```

- [ ] **Step 2: Commit (in kb-odl)**

```bash
cd /Users/benedict/Workspace/bundolee/kb-odl
git add "raw/운영/회의록/2026-04-16_hancom-ai-a11y_2차회의-매핑표-리뷰.md"
git commit -m "회의록 갱신: hancom-ai mock 서버 구축 완료, transformer 재설계 unblock"
```

---

## Self-Review Checklist (작성자 자체 검증)

**Spec coverage:**
- ✅ 6개 모듈 (DLA_OCR/DLA/OCR/TSR/IMAGE_CAPTIONING/CHART) — Task 4 lookup, Task 6/7 server
- ✅ pdf2img 동적 변환 — Task 5 renderer, Task 8 endpoint
- ✅ /ping 엔드포인트 — Task 6
- ✅ /api/v1 alias — Task 6
- ✅ DATA_FORMAT 분기 — Task 6/7
- ✅ TSR_regionlist 폴백 — Task 4
- ✅ Unknown PDF FIXTURE_MISS — Task 6
- ✅ Client 패치 (REQUEST_ID 규약 6개 모듈 단축명, sha_short, page, obj) — Task 11/12
- ✅ E2E smoke — Task 13

**Placeholder scan:** TBD/TODO/"구현 나중에" 없음. 모든 코드 step에 완전한 코드 블록 첨부.

**Type consistency:** `RequestIdParts(sha_short, page, obj, module)` 일관, `MODULE_TO_DIR`(server-side mapping) vs `MODULE_SHORT`(client-side mapping) 분리 명확. `lookup_image_module(..., module_short, basename, page, obj)` 시그니처 일관.

**Java 컴파일 위험요소:**
- `HexFormat`은 Java 17+. 만약 빌드 타깃이 Java 8/11이면 테스트에서 직접 hex 변환 헬퍼로 대체 필요. Task 11 Step 1 컴파일 실패 시 즉시 발견.
- mockwebserver 의존성 추가 필요 (Task 11 Step 2에서 검증).

---

## Execution Handoff

Plan complete and saved to `opendataloader-project/opendataloader-pdf/docs/superpowers/plans/2026-04-18-hancom-ai-mock-server.md`.

Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task with two-stage review between tasks. Best for this plan since it spans two repos (Python + Java) and has a clear TDD rhythm.

2. **Inline Execution** — execute tasks in this session with checkpoints. Faster turnaround but heavier on the main context.

Which approach?
