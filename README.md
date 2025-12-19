# OpenDataLoader PDF


[![License](https://img.shields.io/pypi/l/opendataloader-pdf.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)
![Java](https://img.shields.io/badge/Java-11+-blue.svg)
![Python](https://img.shields.io/badge/Python-3.9+-blue.svg)
[![Maven Central](https://img.shields.io/maven-central/v/org.opendataloader/opendataloader-pdf-core.svg)](https://search.maven.org/artifact/org.opendataloader/opendataloader-pdf-core)
[![PyPI version](https://img.shields.io/pypi/v/opendataloader-pdf.svg)](https://pypi.org/project/opendataloader-pdf/)
[![npm version](https://img.shields.io/npm/v/@opendataloader/pdf.svg)](https://www.npmjs.com/package/@opendataloader/pdf)
[![GHCR Version](https://ghcr-badge.egpl.dev/opendataloader-project/opendataloader-pdf-cli/latest_tag?trim=major&label=docker-image)](https://github.com/opendataloader-project/opendataloader-pdf/pkgs/container/opendataloader-pdf-cli)
[![Coverage](https://codecov.io/gh/opendataloader-project/opendataloader-pdf/branch/main/graph/badge.svg)](https://app.codecov.io/gh/opendataloader-project/opendataloader-pdf)
[![CLA assistant](https://cla-assistant.io/readme/badge/opendataloader-project/opendataloader-pdf)](https://cla-assistant.io/opendataloader-project/opendataloader-pdf)

<br/>

**Safe, Open, High-Performance — PDF for AI**

OpenDataLoader-PDF converts PDFs into JSON, Markdown or Html — ready to feed into modern AI stacks (LLMs, vector search, and RAG).

It reconstructs document layout (headings, lists, tables, and reading order) so the content is easier to chunk, index, and query.
Powered by fast, heuristic, rule-based inference, it runs entirely on your local machine and delivers high-throughput processing for large document sets.
AI-safety is enabled by default and automatically filters likely prompt-injection content embedded in PDFs to reduce downstream risk.

<br/>

## 🌟 Key Features

- 🧾 **Rich, Structured Output** — JSON, Markdown or Html
- 🧩 **Layout Reconstruction** — Headings, Lists, Tables, Images, Reading Order
- ⚡ **Fast & Lightweight** — Rule-Based Heuristic, High-Throughput, No GPU
- 🔒 **Local-First Privacy** — Runs fully on your machine
- 🏷️ **Tagged PDF** — Advanced data extraction technology based on Tagged PDF - [Learn more](https://opendataloader.org/docs/tagged-pdf)
- 🛡️ **AI-Safety** — Auto-Filters likely prompt-injection content - [Learn more](https://opendataloader.org/docs/ai-safety)
- 🖍️ **Annotated PDF Visualization** — See detected structures overlaid on the original - [See examples](https://opendataloader.org/demo/samples)

[![Annotated PDF Preview](https://github.com/opendataloader-project/opendataloader-pdf/raw/refs/heads/main/samples/pdf/example_annotated_pdf.png)](https://opendataloader.org/demo/samples/01030000000000?view1=annot&view2=json)

<br/>

- 📊 **Benchmark** — Continuously researched to deliver High-Performance & Quality - [GitHub](https://github.com/opendataloader-project/opendataloader-bench)

[![Benchmark Preview](https://github.com/opendataloader-project/opendataloader-bench/raw/refs/heads/main/charts/benchmark.png)](https://github.com/opendataloader-project/opendataloader-bench)

<br/>

### 🚀 Upcoming Features

**Scheduled for December**
- 🖨️ **OCR for scanned PDFs** — Extract data from image-only pages. 
- 🧠 **Table AI option** — Higher accuracy for tables with borderless or merged cells.

<br/>

## Quick Start with Python

### Prerequisites

- Java 11 or higher must be installed and available in your system's PATH.
- Python 3.9+

### Installation

```sh
pip install -U opendataloader-pdf
```

### Usage

input_path can be either the path to a single document or the path to a folder.

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["path/to/document.pdf", "path/to/folder"],
    output_dir="path/to/output",
    format="json,html,pdf,markdown"
)
```

<br/>

## Quick Start with more languages & tools

- [Quick Start with Python](https://opendataloader.org/docs/quick-start-python)
- [Quick Start with Java](https://opendataloader.org/docs/quick-start-java)
- [Quick Start with Node.js](https://opendataloader.org/docs/quick-start-nodejs)
- [Quick Start with Docker](https://opendataloader.org/docs/quick-start-docker)

<br/>

## Developing with OpenDataLoader

### Build & Test

**Prerequisites**: Java 11+, Python 3.9+, Node.js 20+, pnpm

```sh
# Run tests (for local development)
./scripts/test-java.sh
./scripts/test-python.sh
./scripts/test-node.sh

# Full CI build (all packages)
./scripts/build-all.sh
```

### Syncing CLI Options

CLI options are defined in Java and auto-generated for Node.js, Python, and documentation.

```sh
# After modifying Java CLI options, regenerate all bindings:
pnpm run sync-options
```

This generates:
- `node/opendataloader-pdf/src/cli-options.generated.ts`
- `node/opendataloader-pdf/src/convert-options.generated.ts`
- `python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py`
- `python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py`
- `content/docs/cli-options-reference.mdx`

### Resources

- [CLI Options Reference](https://opendataloader.org/docs/cli-options-reference)
- [Development](https://opendataloader.org/docs/development-workflow)
- [Json Schema](https://opendataloader.org/docs/json-schema)
- [Javadoc](https://javadoc.io/doc/org.opendataloader/opendataloader-pdf-core/latest/index.html)

<br/>

## 🤝 Contributing

We believe that great software is built together.

Your contributions are vital to the success of this project.

Please read [CONTRIBUTING.md](https://github.com/hancom-inc/opendataloader-pdf/blob/main/CONTRIBUTING.md) for details on how to contribute.

<br/>

## 💖 Community & Support

Have questions or need a little help? We're here for you!🤗

- [GitHub Discussions](https://github.com/hancom-inc/opendataloader-pdf/discussions): For Q&A and general chats. Let's talk! 🗣️
- [GitHub Issues](https://github.com/hancom-inc/opendataloader-pdf/issues): Found a bug? 🐛 Please report it here so we can fix it.
- [SUPPORT.md](SUPPORT.md): Learn about our issue guidelines and AI-powered issue processing system.

<br/>

## ✨ Our Branding and Trademarks

We love our brand and want to protect it!

This project may contain trademarks, logos, or brand names for our products and services.

To ensure everyone is on the same page, please remember these simple rules:

- **Authorized Use**: You're welcome to use our logos and trademarks, but you must follow our official brand guidelines.
- **No Confusion**: When you use our trademarks in a modified version of this project, it should never cause confusion or imply that Hancom officially sponsors or endorses your version.
- **Third-Party Brands**: Any use of trademarks or logos from other companies must follow that company’s specific policies.

<br/>

## ⚖️ License

This project is licensed under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/).

For the full license text, see [LICENSE](LICENSE).

For information on third-party libraries and components, see:
- [THIRD_PARTY_LICENSES](./THIRD_PARTY/THIRD_PARTY_LICENSES.md)
- [THIRD_PARTY_NOTICES](./THIRD_PARTY/THIRD_PARTY_NOTICES.md)
- [licenses/](./THIRD_PARTY/licenses/)
