# 🚀 OpenDataLoader PDF — Developer Guide & Setup Instructions

This project is configured for high-precision parsing of complex PDF documents (such as academic research papers, textbooks, and technical reports) into structured **Markdown** and **JSON** with bounding boxes, extracting mathematical formulas as **LaTeX**, and generating semantic figure descriptions (**Alt-Text via SmolVLM**).

---

## 📌 1. System Prerequisites

* **Java 17+** (JDK):
  * **macOS:** `brew install openjdk@17`
  * **Linux (Ubuntu/Debian):** `sudo apt update && sudo apt install -y openjdk-17-jdk`
  * **Windows:** Download the installer from [Adoptium (Eclipse Temurin 17+)](https://adoptium.net/) or via terminal:
    ```powershell
    winget install EclipseAdoptium.Temurin.17.JDK
    ```
    *(Ensure the "Set JAVA_HOME variable" and "Add to PATH" options are checked during setup).*
* **Python 3.10+** (Recommended: 3.10 – 3.12). On Windows, make sure to check **"Add python.exe to PATH"** during installation.
* **Maven & Node.js** (Only needed if you intend to modify and rebuild the Java core engine from source).

---

## ⚡ 2. Quick Start & Installation

### Option A: macOS / Linux

```bash
# 1. Create and activate a Python virtual environment
python3 -m venv venv
source venv/bin/activate

# 2. Install parser dependencies and ML/AI backend
pip install -U "opendataloader-pdf[hybrid]"
```

### Option B: Windows (PowerShell / Command Prompt)

```powershell
# 1. Create a Python virtual environment
python -m venv venv

# 2. Activate the virtual environment (in PowerShell)
.\venv\Scripts\Activate.ps1
# (If PowerShell blocks script execution, run: Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass)

# Or in classic Command Prompt (cmd.exe):
# venv\Scripts\activate.bat

# 3. Install parser dependencies and ML/AI backend
pip install -U "opendataloader-pdf[hybrid]"
```

> **Note on rebuilding Java core (Optional):** If you edit the Java source code and want to recompile the `.jar` CLI file:
> ```bash
> npm run build-java
> ```

---

## 🛠️ 3. Running the Parser

Turnkey runner scripts are provided for single-command batch conversion across all operating systems:

### 🍏 On macOS / Linux:
```bash
# Parse all files in the data directory
./run_parser.sh data data/output_perfect

# Or parse an individual PDF file
./run_parser.sh path/to/document.pdf path/to/output_dir
```

### 🪟 On Windows (CMD / PowerShell):
```cmd
:: Parse all files in the data directory
run_parser.bat data data\output_perfect

:: Or invoke the Python pipeline directly
python run_pipeline.py data data/output_perfect
```

---

## 🧠 4. Pipeline Architecture & Settings (`run_pipeline.py`)

The [`run_pipeline.py`](./run_pipeline.py) script automatically manages the lifecycle of the local AI backend server and runs batch conversions configured for **maximal extraction quality**:

1. **AI Hybrid Server (`opendataloader-pdf-hybrid`):**
   * `--enrich-formula`: Converts complex mathematical equations into clean LaTeX markup ($$ \int ... $$).
   * `--enrich-picture-description`: Employs a Vision-Language Model (SmolVLM) to generate semantic descriptions (Alt-Text) for charts, plots, and figures.
   * `--no-ocr`: Disables redundant OCR on born-digital PDFs, preventing duplicate text fragments and false image artifacts.
   * `--device cpu`: Provides stable, cross-platform inference across Windows, macOS, and Linux.

2. **Client Conversion (`opendataloader_pdf.convert`):**
   * `--hybrid-mode full`: Directs every page to the AI backend to ensure no formulas or figures are skipped by triage heuristics.
   * `--markdown-with-html`: Preserves multi-column and multi-row merged table structures using HTML `<table>` blocks inside Markdown.
   * `--table-method cluster`: Coordinates clustering to detect borderless and complex tables.
   * `--image-resolution 300.0`: High-resolution 300 DPI image extraction for crisp diagrams.
   * `--hybrid-timeout 0`: Disables client timeout limits for long-running textbook conversions.
   * `--hybrid-fallback`: Gracefully falls back to local layout analysis if any page encounters an unexpected error.

---

## 📂 5. Project Directory Structure

```text
├── data/                                 # Input PDFs and extraction results
│   ├── output_perfect/                   # Enriched outputs (Markdown, JSON, 300 DPI Images)
│   ├── Trans_Conceptual_Sampling...pdf   # Scientific article
│   └── ensemble_data_assimilation...pdf  # Comprehensive 428-page textbook
├── run_parser.sh                         # 1-click execution script for macOS / Linux
├── run_parser.bat                        # 1-click execution script for Windows
├── run_pipeline.py                       # Core Python pipeline with automated server lifecycle
├── DEVELOPER_GUIDE.md                    # This developer documentation
├── java/                                 # Java engine source code
├── python/                               # Python SDK source code
└── README.md                             # Original project documentation
```
