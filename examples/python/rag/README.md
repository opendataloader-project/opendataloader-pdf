# RAG Examples for OpenDataLoader PDF

Working examples demonstrating how to use OpenDataLoader PDF in RAG (Retrieval-Augmented Generation) pipelines.

## Prerequisites

- Python 3.9+
- Java 11+ (on PATH)

## Sample PDF

Examples use `samples/pdf/1901.03003.pdf` - a multi-page academic paper (arXiv:1901.03003) with:
- Two-column layout
- Multiple sections and headings
- Tables and figures
- Complex reading order

## Examples

### 1. Basic Chunking (No External Dependencies)

[`basic_chunking.py`](basic_chunking.py) demonstrates PDF-to-chunks conversion using only `opendataloader-pdf` and Python standard library. No external embedding or vector store dependencies.

**Features:**
- PDF to JSON conversion with reading order
- Three chunking strategies:
  1. By element (paragraph, heading, list)
  2. By section (grouped under headings)
  3. Merged chunks (minimum size threshold)
- Bounding box metadata for citations

**Run:**
```bash
pip install opendataloader-pdf
python basic_chunking.py
```

### 2. LangChain Integration

[`langchain_example.py`](langchain_example.py) shows integration with the official LangChain loader.

**Features:**
- OpenDataLoaderPDFLoader usage
- Returns LangChain Document objects
- Ready for any LangChain pipeline

**Run:**
```bash
pip install -r requirements.txt
python langchain_example.py
```

## Sample Output

```
Processing: 1901.03003.pdf
==================================================
Document: 1901.03003.pdf
Pages: 9
Elements: 187

--- Strategy 1: Chunk by Element ---
Created 156 chunks
  [1] RoBERTa: A Robustly Optimized BERT Pretraining Approach
      Source: 1901.03003.pdf, Page 1, Position (108, 655)
  [2] Yinhan Liu† Myle Ott† Naman Goyal† Jingfei Du† ...
      Source: 1901.03003.pdf, Page 1, Position (142, 603)

--- Strategy 2: Chunk by Section ---
Created 12 chunks
  Section: RoBERTa: A Robustly Optimized BERT Pretraining Approach
  Section: 1 Introduction
  Section: 2 Background
  ...
```

## Next Steps

After chunking, integrate with your preferred:
- **Embedding model**: OpenAI, Cohere, HuggingFace, etc.
- **Vector store**: Chroma, FAISS, Pinecone, Weaviate, etc.

Each chunk includes `text` and `metadata` ready for embedding:

```python
{
  "text": "Language model pretraining has led to significant...",
  "metadata": {
    "type": "paragraph",
    "page": 1,
    "bbox": [108.0, 526.2, 286.5, 592.8],
    "source": "1901.03003.pdf"
  }
}
```
