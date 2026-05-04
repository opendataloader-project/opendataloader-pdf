# OpenDataLoader PDF MCP Server

[![PyPI version](https://img.shields.io/pypi/v/opendataloader-pdf-mcp.svg)](https://pypi.org/project/opendataloader-pdf-mcp/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)

MCP (Model Context Protocol) server for [OpenDataLoader PDF](https://github.com/opendataloader-project/opendataloader-pdf).

Enables AI agents (Claude, Cursor, Codex, Windsurf, and any MCP-compatible client) to convert PDFs to Markdown, JSON, HTML, and more — without leaving the chat.

## Prerequisites

- Java 11+
- Python 3.10+

## Installation

```bash
pip install opendataloader-pdf-mcp
```

Or run without installing:

```bash
uvx opendataloader-pdf-mcp
```

## Connect to Your MCP Client

### Claude Code

```bash
claude mcp add opendataloader-pdf -- opendataloader-pdf-mcp
```

### Cursor

Add to `~/.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "opendataloader-pdf-mcp"
    }
  }
}
```

### Claude Desktop

Add to `~/.config/Claude/claude_desktop_config.json` (Linux) or `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "uvx",
      "args": ["opendataloader-pdf-mcp"]
    }
  }
}
```

### OpenAI Codex

```bash
codex --mcp-config mcp.json
```

`mcp.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "uvx",
      "args": ["opendataloader-pdf-mcp"]
    }
  }
}
```

### Windsurf

Add to `~/.codeium/windsurf/mcp_config.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "uvx",
      "args": ["opendataloader-pdf-mcp"]
    }
  }
}
```

### Other MCP Clients

Any MCP-compatible client using stdio transport works with:

```bash
opendataloader-pdf-mcp
```

## Tools

### `describe_pdf`

Get a quick structural summary of a PDF without full conversion.

**Parameters:**
- `input_path` (required): Path to the PDF file

**Returns:** title, page count, section count, equation count, figure count, reference count.

### `convert_pdf`

Convert a PDF synchronously and return the content directly.

**Parameters:**
- `input_path` (required): Path to the PDF file
- `format`: Output format — `markdown` (default), `json`, `text`, `html`, `markdown-with-html`, `markdown-with-images`, `graph-json`
- `pages`: Pages to extract, e.g. `"1,3,5-7"`. Default: all
- `password`: Password for encrypted PDFs
- `image_output`: `off`, `embedded` (Base64), or `external`
- `image_dir`: Directory to save extracted images when `image_output=external`
- `use_struct_tree`: Use native PDF structure tags for reading order
- `table_method`: Table detection — `default` or `cluster`
- `reading_order`: Reading order algorithm — `off` or `xycut`
- `sanitize`: Replace emails, URLs, phone numbers with placeholders
- `hybrid`: AI backend — `off` or `docling-fast`
- `hybrid_mode`: `auto` or `full` (required for formula/picture enrichments)

**Returns:** Converted content as text.

### `submit_pdf`

Submit a PDF for async conversion. Use this for large files or when you want triage quality scoring.

**Parameters:** Same as `convert_pdf`, plus:
- `enable_mineru_fallback`: Retry with MinerU if Java extraction scores `HARD_FAIL`

**Returns:** `{"job_id": "...", "status": "pending"}`

### `get_job_status`

Poll a submitted job's status.

**Parameters:**
- `job_id` (required): Job ID from `submit_pdf`

**Returns:** `status` (`pending` / `running` / `done` / `failed`), `triage_decision` (`PASS` / `SOFT_FAIL` / `HARD_FAIL`), quality score.

### `get_artifact`

Retrieve the converted content for a completed job.

**Parameters:**
- `job_id` (required): Job ID from `submit_pdf`
- `source`: Which artifact — `primary` (default), `java`, `mineru-json`

**Returns:** Full converted content.

### `cancel_job`

Cancel a pending or running job.

**Parameters:**
- `job_id` (required): Job ID from `submit_pdf`

## Recommended Workflow for Scientific Papers

```
1. describe_pdf("/path/to/paper.pdf")
   → title, page count, section count, equation count

2. submit_pdf("/path/to/paper.pdf", format="markdown-with-images", enable_mineru_fallback=True)
   → {"job_id": "...", "status": "pending"}

3. get_job_status("<job_id>")   ← repeat until status == "done"
   → triage_decision, quality score

4. get_artifact("<job_id>")
   → full markdown content

5. convert_pdf("/path/to/paper.pdf", format="graph-json")
   → structured JSON: sections, numbered equations, figures, bibliography
```

For scanned PDFs, `enable_mineru_fallback=True` automatically retries with MinerU if quality is `HARD_FAIL`. MinerU must be installed separately:

```bash
pip install mineru
```

## License

Apache-2.0

---

> MCP server contributed by [@hahahuy](https://github.com/hahahuy). Core library by the [OpenDataLoader project](https://github.com/opendataloader-project/opendataloader-pdf).
