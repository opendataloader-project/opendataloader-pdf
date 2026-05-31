# OpenDataLoader PDF MCP Server

MCP (Model Context Protocol) server for [OpenDataLoader PDF](https://github.com/opendataloader-project/opendataloader-pdf).

Enables AI agents to convert PDFs to Markdown, JSON, HTML, and more via MCP.

## Prerequisites

- Java 11+
- Python 3.10+

## Installation

```bash
pip install opendataloader-pdf-mcp
```

## Usage

### Claude Desktop

Add to your Claude Desktop config (`claude_desktop_config.json`):

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

### Claude Code

```bash
claude mcp add opendataloader-pdf -- uvx opendataloader-pdf-mcp
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

### Cursor

Add to `.cursor/mcp.json` in your project:

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

Any MCP-compatible client can use this server. The command is:

```bash
uvx opendataloader-pdf-mcp
```

## Tools

### convert_pdf

Convert a PDF file to the specified format.

**Parameters:**

- `input_path` (required): Path to the input PDF file
- `format`: Output file kind — `json`, `text`, `html`, `markdown` (default)
- `markdown_with_html`: Allow HTML tags inside Markdown for complex tables (`format="markdown"` only)
- `image_output`: Image output mode — `off`, `embedded`, `external` (defaults to `embedded` for `markdown`/`html` so images survive transport)
- `pages`: Pages to extract (e.g., `"1,3,5-7"`)
- `password`: Password for encrypted PDFs
- All other [OpenDataLoader PDF options](https://opendataloader.org/docs/options) are supported

> The legacy `--format markdown-with-html` / `--format markdown-with-images` values were removed from this MCP server. Use the explicit `markdown_with_html` and `image_output` parameters instead.

## License

Apache-2.0
