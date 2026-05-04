# Local Usage & Publishing Guide — opendataloader-pdf-mcp

**Date:** 2026-05-03  
**Package:** `opendataloader-pdf-mcp` v0.2.0  
**Location:** `python/opendataloader-pdf-mcp/`

---

## Using the MCP Server Locally

The server speaks JSON-RPC over **stdin/stdout** — it does not open a network port. Your MCP client (Cursor, Claude Desktop, Claude Code, etc.) starts it as a subprocess automatically.

### Step 1 — Install from source

```bash
cd /home/hahuy/Documents/github/opendataloader-pdf/python/opendataloader-pdf-mcp
pip install -e .
```

This installs the `opendataloader-pdf-mcp` command globally (in your active Python environment).

### Step 2 — Verify it works

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}}' \
  | opendataloader-pdf-mcp 2>/dev/null \
  | python3 -c "import sys,json; d=json.loads(sys.stdin.readline()); print('OK — server responding')"
```

Expected output: `OK — server responding`

### Step 3 — Connect to your MCP client

#### Cursor

Edit `~/.cursor/mcp.json` (create if not present):

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "opendataloader-pdf-mcp"
    }
  }
}
```

Restart Cursor. The tools `describe_pdf`, `convert_pdf`, `submit_pdf`, `get_job_status`, `cancel_job`, `get_artifact` will appear in the MCP tool panel.

If you want to run directly from source without installing:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "python",
      "args": ["-m", "opendataloader_pdf_mcp.server"],
      "cwd": "/home/hahuy/Documents/github/opendataloader-pdf/python/opendataloader-pdf-mcp"
    }
  }
}
```

#### Claude Desktop (Linux)

Edit `~/.config/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "opendataloader-pdf-mcp"
    }
  }
}
```

Restart Claude Desktop.

#### Claude Code

```bash
claude mcp add opendataloader-pdf -- opendataloader-pdf-mcp
```

#### Other MCP clients (OpenAI Codex, Windsurf, etc.)

Use `opendataloader-pdf-mcp` as the server command. All clients that support MCP stdio transport work the same way.

### Using the tools for scientific papers

Recommended workflow once connected:

```
1. describe_pdf("/path/to/paper.pdf")
   → returns title, page count, section count, equation count, reference count

2. submit_pdf("/path/to/paper.pdf", format="markdown-with-images", enable_mineru_fallback=True)
   → returns {"job_id": "...", "status": "pending"}

3. get_job_status("<job_id>")   [repeat until status == "done"]
   → returns triage_decision (PASS / SOFT_FAIL / HARD_FAIL) and quality score

4. get_artifact("<job_id>")
   → returns full markdown content

5. convert_pdf("/path/to/paper.pdf", format="graph-json")
   → returns JSON with structured sections, numbered equations, figures, bibliography
```

For scanned PDFs, `enable_mineru_fallback=True` on `submit_pdf` will automatically retry with MinerU if Java extraction quality is classified as `HARD_FAIL`. MinerU must be installed separately:

```bash
pip install mineru
```

---

## Publishing to PyPI

### Prerequisites

```bash
pip install build twine
```

You need:
- A [PyPI account](https://pypi.org/account/register/)
- An API token from [https://pypi.org/manage/account/token/](https://pypi.org/manage/account/token/)

### Step 1 — Pre-publish checklist

Before publishing, verify:

1. **`opendataloader-pdf` is on PyPI** — this is listed as a dependency. Check:
   ```bash
   pip index versions opendataloader-pdf 2>&1 | head -3
   ```
   If not published, publish `python/opendataloader-pdf/` first (same process below applies to it).

2. **Java requirement** — end users need Java 11+. The package cannot ship the JVM. The README documents this under Prerequisites.

3. **Version is correct** — `pyproject.toml` should have `version = "0.2.0"`. Check:
   ```bash
   grep version python/opendataloader-pdf-mcp/pyproject.toml
   ```

4. **Tests pass**:
   ```bash
   cd python/opendataloader-pdf-mcp
   python -m pytest tests/ -q
   ```

### Step 2 — Build the distribution

```bash
cd /home/hahuy/Documents/github/opendataloader-pdf/python/opendataloader-pdf-mcp
python -m build
```

This creates:
- `dist/opendataloader_pdf_mcp-0.2.0-py3-none-any.whl`
- `dist/opendataloader_pdf_mcp-0.2.0.tar.gz`

### Step 3 — Test on TestPyPI first

```bash
twine upload --repository testpypi dist/*
```

Enter your TestPyPI token when prompted. Then verify the install:

```bash
pip install --index-url https://test.pypi.org/simple/ \
    --extra-index-url https://pypi.org/simple/ \
    opendataloader-pdf-mcp==0.2.0
opendataloader-pdf-mcp --help
```

### Step 4 — Publish to PyPI

```bash
twine upload dist/*
```

Enter your PyPI API token. After upload, anyone can install:

```bash
pip install opendataloader-pdf-mcp
# or run without installing:
uvx opendataloader-pdf-mcp
```

### Step 5 — Tag the release

```bash
cd /home/hahuy/Documents/github/opendataloader-pdf
git tag mcp-v0.2.0
git push origin mcp-v0.2.0
```

---

## Upgrading the version

For future releases:

1. Update `version` in `python/opendataloader-pdf-mcp/pyproject.toml`
2. Run tests
3. `python -m build` in that directory
4. `twine upload dist/*`
5. Git tag the release
