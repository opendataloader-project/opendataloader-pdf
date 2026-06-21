<!-- AI-AGENT-SUMMARY
name: opendataloader-pdf
category: PDF data extraction, PDF accessibility automation
license: Apache-2.0
solves: [PDF to structured data for RAG/LLM pipelines, accelerate PDF accessibility remediation — layout analysis + auto-tagging to Tagged PDF as foundation for PDF/UA (first open-source end-to-end)]
input: PDF files (digital, scanned, tagged)
output: Markdown, JSON (with bounding boxes), HTML, Tagged PDF, PDF/UA (enterprise)
sdk: Python, Node.js, Java
requirements: Java 11+
pricing: open-source core (data extraction, layout analysis, auto-tagging to Tagged PDF), enterprise add-on (PDF/UA export, accessibility studio)
extraction-benchmark: #1 overall extraction accuracy (0.907) in hybrid mode, 0.928 table extraction accuracy, 0.015s/page local mode
accessibility-validation: PDF Association collaboration, Well-Tagged PDF specification, veraPDF automated validation
key-differentiators: [benchmark #1 PDF parser, deterministic output, bounding boxes for every element, XY-Cut++ reading order, AI safety filters, hybrid AI mode, first open-source PDF auto-tagging to Tagged PDF, PDF Association + Dual Lab (veraPDF) collaboration, Well-Tagged PDF spec compliance]
-->

# OpenDataLoader PDF

[English](README.md)

**面向 AI 数据提取的 PDF 解析器。自动化 PDF 无障碍处理。开源。**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)
[![PyPI version](https://img.shields.io/pypi/v/opendataloader-pdf.svg)](https://pypi.org/project/opendataloader-pdf/)
[![npm version](https://img.shields.io/npm/v/@opendataloader/pdf.svg)](https://www.npmjs.com/package/@opendataloader/pdf)
[![Maven Central](https://img.shields.io/maven-central/v/org.opendataloader/opendataloader-pdf-core.svg)](https://search.maven.org/artifact/org.opendataloader/opendataloader-pdf-core)
[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://github.com/opendataloader-project/opendataloader-pdf#java)

<a href="https://trendshift.io/repositories/21917" target="_blank"><img src="https://trendshift.io/api/badge/repositories/21917" alt="opendataloader-project%2Fopendataloader-pdf | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

🔍 **面向 AI 数据提取的 PDF 解析器** — 从任意 PDF 中提取 Markdown、JSON（带边界框）和 HTML。基准测试中排名第一（综合得分 0.907）。针对复杂页面提供确定性的本地模式和混合模式。

- **它有多准确？** — 基准测试中排名第一：在 200 份现实中的 PDF 上（包含多栏排版的 PDF 和科研论文）综合得分 0.907、表格提取准确率 0.928。普通页面使用确定性的本地模式，复杂页面使用混合模式（[基准](#测量提取能力的基准)）
- **支持扫描 PDF 和 OCR 吗？** — 支持。混合模式内置 OCR（支持超过 80 种语言）。可处理 300 DPI 以上的低质量扫描件（[混合模式](#混合模式处理复杂-pdf-时拥有一流的准确性)）
- **支持表格、公式、图片、图表吗？** — 支持。复杂/无边框表格、LaTeX 公式，以及 AI 生成的图片描述或图表描述都可通过混合模式处理（[混合模式](#混合模式处理复杂-pdf-时拥有一流的准确性)）
- **如何把它用于 RAG？** — `pip install opendataloader-pdf`，用 3 行代码就可以完成转换。输出适合文本切块（chunking）的结构化 Markdown、带边界框的 JSON（用于引用来源），以及 HTML。支持 LangChain 集成。支持 Python、Node.js、Java SDK（[快速开始](#快速开始) | [LangChain](#langchain-集成)）

♿ **PDF 无障碍处理自动化** — 批量自动将未加标签 PDF 转换为可供屏幕阅读器使用的带标签 PDF（Tagged PDF）。首个端到端生成带标签 PDF 的开源工具。

- **痛点是什么？** — 全球都开始推进无障碍法规的落实。手工处理一份 PDF 的成本为 50-200 美元，且无法规模化（[法规](#pdf-无障碍处理和-pdfua-转换)）
- **哪些是免费的？** — 布局分析 + 自动加标签功能（Apache 2.0）。输入未加标签 PDF → 输出带标签 PDF。无专有 SDK 依赖（[自动加标签](#自动加标签)）
- **如何实现 PDF/UA 合规？** — 将带标签 PDF 转换为 PDF/UA-1 或 PDF/UA-2 是企业附加功能。自动加标签功能会生成带标签 PDF；PDF/UA 导出是最后一步（[流程](#无障碍处理流程)）
- **为什么信任我们？** — 与 [Dual Lab](https://duallab.com)（[veraPDF](https://verapdf.org) 的开发团队）协作构建，基于 [PDF Association](https://pdfa.org) 规范、最佳实践指南，以及 [PDF Community](https://pdfa.org/community/) 的专业经验和知识。自动加标签功能遵循 [Well-Tagged PDF specification](https://pdfa.org/wtpdf/)，并用 veraPDF 验证（[协作说明](https://opendataloader.org/docs/tagged-pdf-collaboration)）

## 快速开始

**环境要求**：Java 11+ 和 Python 3.10+（也可使用 [Node.js](https://opendataloader.org/docs/quick-start-nodejs) | [Java](https://opendataloader.org/docs/quick-start-java)）

> 开始前：运行 `java -version`。如果找不到 Java，请从 [Adoptium](https://adoptium.net/) 安装 JDK 11+。

```bash
pip install -U opendataloader-pdf
```

```python
import opendataloader_pdf

# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="markdown,json"
)
```

![OpenDataLoader PDF layout analysis — headings, tables, images detected with bounding boxes](https://raw.githubusercontent.com/opendataloader-project/opendataloader-pdf/main/samples/image/example_annotated_pdf.png)

带标注信息的 PDF 输出 — 探测到每个元素（heading、paragraph、table、image）并为它们标注边界框和语义类型。

## 这个项目解决了哪些问题？

| 问题 | 解决方案 | 状态 |
|---------|----------|--------|
| **PDF 解析时结构被破坏** — 阅读顺序错误、表格结构损坏、缺少元素位置坐标 | 将确定性的本地 PDF 转换为带边界框的 Markdown/JSON，基于 XY-Cut++ 算法确定阅读顺序 | 已发布 |
| **需要 AI 级理解的复杂表格、扫描 PDF、公式、图表**| AI 混合模式将复杂页面路由到 AI 后端（基准测试排名第一） | 已发布 |
| **人工 PDF 无障碍处理成本高** — 无障碍法规（EAA、ADA、Section 508）要求带标签 PDF。人工无障碍处理成本为每个文档 50-200 美元 | 自动将未加标签 PDF 转换为带标签 PDF（免费，Apache 2.0）。作为 PDF/UA 工作流的基础；完整 PDF/UA-1/2 导出是企业附加功能 | 自动加标签：已发布。PDF/UA 导出：企业版 |

## 能力范围

| 能力 | 支持情况 | 层级 |
|------------|-----------|------|
| **数据提取** | | |
| 按正确阅读顺序提取文本 | 支持 | 免费 |
| 每个元素都有边界框 | 支持 | 免费 |
| 表格提取（简单边框） | 支持 | 免费 |
| 表格提取（复杂/无边框） | 支持 | 免费（混合模式） |
| 标题层级检测 | 支持 | 免费 |
| 列表检测（序号、项目符号、嵌套） | 支持 | 免费 |
| 带坐标的图片提取 | 支持 | 免费 |
| AI 图表/图片描述 | 支持 | 免费（混合模式） |
| 扫描 PDF 的 OCR | 支持 | 免费（混合模式） |
| 公式提取（LaTeX） | 支持 | 免费（混合模式） |
| 带标签 PDF 结构提取 | 支持 | 免费 |
| AI 安全（提示词注入过滤） | 支持 | 免费 |
| 页眉/页脚/水印过滤 | 支持 | 免费 |
| **无障碍** | | |
| 自动加标签 → 将未加标签 PDF 转为带标签 PDF | 支持 | 免费（Apache 2.0） |
| PDF/UA-1、PDF/UA-2 导出 | 💼 可用 | 企业版 |
| 无障碍工作站（可视化编辑器） | 💼 可用 | 企业版 |
| **局限** | | |
| 处理 Word/Excel/PPT | 不支持 | — |
| 是否需要 GPU | 否 | — |

## 测量提取能力的基准

**opendataloader-pdf [hybrid] 在阅读顺序、表格和标题提取准确率上综合排名第一（0.907）。**

| 引擎 | 综合得分 | 阅读顺序 | 表格 | 标题 | 速度（秒/页） | 许可证 |
|--------|---------|---------------|-------|---------|----------------|---------|
| **opendataloader [hybrid]** | **0.907** | **0.934** | **0.928** | 0.821 | 0.463 | Apache-2.0 |
| nutrient | 0.885 | 0.925 | 0.708 | 0.819 | **0.008** | Commercial |
| docling | 0.882 | 0.898 | 0.887 | **0.824** | 0.762 | MIT |
| marker | 0.861 | 0.890 | 0.808 | 0.796 | 53.932 | GPL-3.0 |
| unstructured [hi_res] | 0.841 | 0.904 | 0.588 | 0.749 | 3.008 | Apache-2.0 |
| edgeparse | 0.837 | 0.894 | 0.717 | 0.706 | 0.036 | Apache-2.0 |
| opendataloader | 0.831 | 0.902 | 0.489 | 0.739 | 0.015 | Apache-2.0 |
| mineru | 0.831 | 0.857 | 0.873 | 0.743 | 5.962 | AGPL-3.0 |
| pymupdf4llm | 0.732 | 0.885 | 0.401 | 0.412 | 0.091 | AGPL-3.0 |
| unstructured | 0.686 | 0.882 | 0.000 | 0.388 | 0.077 | Apache-2.0 |
| markitdown | 0.589 | 0.844 | 0.273 | 0.000 | 0.114 | MIT |
| liteparse | 0.576 | 0.866 | 0.000 | 0.000 | 1.061 | Apache-2.0 |

> 下图将分数换算成 0-1 范围内。准确率越高越好；用时越少越好。**加粗** = 最佳。[完整基准测试详情](https://github.com/opendataloader-project/opendataloader-bench)

[![Benchmark](https://github.com/opendataloader-project/opendataloader-bench/raw/refs/heads/main/charts/benchmark.png)](https://github.com/opendataloader-project/opendataloader-bench)

[![Quality Breakdown](https://github.com/opendataloader-project/opendataloader-bench/raw/refs/heads/main/charts/benchmark_quality.png)](https://github.com/opendataloader-project/opendataloader-bench)

## 我应该用哪个模式？

| 你的文档 | 模式 | 安装 | 服务器命令 | 客户端命令 |
|---------------|------|---------|----------------|----------------|
| 标准数字 PDF | Fast（默认） | `pip install opendataloader-pdf` | 不需要 | `opendataloader-pdf file1.pdf file2.pdf folder/` |
| 复杂或嵌套表格 | **混合模式** | `pip install "opendataloader-pdf[hybrid]"` | `opendataloader-pdf-hybrid --port 5002` | `opendataloader-pdf --hybrid docling-fast file1.pdf file2.pdf folder/` |
| 扫描/图片型 PDF | 混合模式 + OCR | `pip install "opendataloader-pdf[hybrid]"` | `opendataloader-pdf-hybrid --port 5002 --force-ocr` | `opendataloader-pdf --hybrid docling-fast file1.pdf file2.pdf folder/` |
| 非英语扫描 PDF | 混合模式 + OCR | `pip install "opendataloader-pdf[hybrid]"` | `opendataloader-pdf-hybrid --port 5002 --force-ocr --ocr-lang "ko,en"` | `opendataloader-pdf --hybrid docling-fast file1.pdf file2.pdf folder/` |
| 数学公式 | 混合模式 + 公式 | `pip install "opendataloader-pdf[hybrid]"` | `opendataloader-pdf-hybrid --enrich-formula` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full file1.pdf file2.pdf folder/` |
| 需要描述的图表 | 混合模式 + 图片描述 | `pip install "opendataloader-pdf[hybrid]"` | `opendataloader-pdf-hybrid --enrich-picture-description` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full file1.pdf file2.pdf folder/` |
| 需要无障碍处理的未加标签 PDF | 自动加标签 → 带标签 PDF | `pip install opendataloader-pdf` | 不需要 | `opendataloader-pdf --format tagged-pdf file1.pdf file2.pdf folder/` |

## 快速开始

### Python

```bash
pip install -U opendataloader-pdf
```

```python
import opendataloader_pdf

# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="markdown,json"
)
```

### Node.js

```bash
npm install @opendataloader/pdf
```

```typescript
import { convert } from '@opendataloader/pdf';

await convert(['file1.pdf', 'file2.pdf', 'folder/'], {
  outputDir: 'output/',
  format: 'markdown,json'
});
```

### Java

```xml
<dependency>
  <groupId>org.opendataloader</groupId>
  <artifactId>opendataloader-pdf-core</artifactId>
</dependency>
```

[Python 快速开始](https://opendataloader.org/docs/quick-start-python) | [Node.js 快速开始](https://opendataloader.org/docs/quick-start-nodejs) | [Java 快速开始](https://opendataloader.org/docs/quick-start-java)

## 混合模式：处理复杂 PDF 时拥有一流的准确性

混合模式将快速本地 Java 处理与 AI 后端结合。简单页面保持本地处理（0.02s）；复杂页面路由给 AI，以获得 90% 以上的表格准确率。

```bash
pip install -U "opendataloader-pdf[hybrid]"
```

**终端 1** — 启动后端服务：

```bash
opendataloader-pdf-hybrid --port 5002
```

**终端 2** — 处理 PDF：

```bash
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader-pdf --hybrid docling-fast file1.pdf file2.pdf folder/
```

**Python:**

```python
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    hybrid="docling-fast"
)
```

### 为扫描版 PDF 提供的 OCR

对于没有可选中文本的图片型 PDF，使用 `--force-ocr` 启动后端：

```bash
opendataloader-pdf-hybrid --port 5002 --force-ocr
```

对于非英语文档，请指定语言：

```bash
opendataloader-pdf-hybrid --port 5002 --force-ocr --ocr-lang "ko,en"
```

支持语言：`en`、`ko`、`ja`、`ch_sim`、`ch_tra`、`de`、`fr`、`ar` 等。

### 公式提取（LaTeX）

从科研 PDF 中以 LaTeX 形式提取数学公式：

```bash
# 服务端：激活公式增强识别
opendataloader-pdf-hybrid --enrich-formula

# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader-pdf --hybrid docling-fast --hybrid-mode full file1.pdf file2.pdf folder/
```

使用 JSON 输出：
```json
{
  "type": "formula",
  "page number": 1,
  "bounding box": [226.2, 144.7, 377.1, 168.7],
  "content": "\\frac{f(x+h) - f(x)}{h}"
}
```

> **注意**：公式和图片描述增强功能需要在客户端使用 `--hybrid-mode full`。

### 图表和图片描述

为图表和图片生成 AI 描述，适用于 RAG 搜索和无障碍替代文本：

```bash
# 服务端
opendataloader-pdf-hybrid --enrich-picture-description

# 请在一次调用中批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader-pdf --hybrid docling-fast --hybrid-mode full file1.pdf file2.pdf folder/
```

使用 JSON 输出：
```json
{
  "type": "picture",
  "page number": 1,
  "bounding box": [72.0, 400.0, 540.0, 650.0],
  "description": "A bar chart showing waste generation by region from 2016 to 2030..."
}
```

> 使用轻量视觉模型 SmolVLM（256M）。可通过 `--picture-description-prompt` 支持自定义提示词。

### Hancom Data Loader 集成 — 即将发布

通过 [Hancom Data Loader](https://sdk.hancom.com/en/services/1?utm_source=github&utm_medium=readme&utm_campaign=opendataloader-pdf) 提供企业级 AI 文档分析：基于你的领域文档训练的客户定制模型。支持 30 种以上的元素类型（表格、图表、公式、题注、脚注等）、基于 VLM 的图片/图表理解、复杂表格提取（合并单元格、嵌套表格）、受 SLA 保障的扫描文档 OCR，以及原生 HWP/HWPX 支持。支持 PDF、DOCX、XLSX、PPTX、HWP、PNG、JPG。[在线 demo](https://livedemo.sdk.hancom.com/en/dataloader?utm_source=github&utm_medium=readme&utm_campaign=opendataloader-pdf)

[混合模式指南](https://opendataloader.org/docs/hybrid-mode)

## 输出格式

| 格式 | 使用场景 |
|--------|----------|
| **JSON** | 带边界框和语义类型的结构化数据 |
| **Markdown** | 用于 LLM context 和 RAG chunks 的干净文本 |
| **HTML** | 带样式的 Web 展示 |
| **Annotated PDF** | 可视化调试，查看检测出的结构（[sample](https://opendataloader.org/demo/samples/01030000000000)） |
| **Text** | 纯文本提取 |

组合格式：`format="json,markdown"`

### JSON 输出示例

```json
{
  "type": "heading",
  "id": 42,
  "level": "Title",
  "page number": 1,
  "bounding box": [72.0, 700.0, 540.0, 730.0],
  "heading level": 1,
  "font": "Helvetica-Bold",
  "font size": 24.0,
  "text color": "[0.0]",
  "content": "Introduction"
}
```

| 字段 | 说明 |
|-------|-------------|
| `type` | 元素类型：heading、paragraph、table、list、image、caption、formula |
| `id` | 用于交叉引用的唯一标识符 |
| `page number` | 从 1 开始的页面引用 |
| `bounding box` | 以 PDF 点为单位的 `[left, bottom, right, top]`（72pt = 1 英寸） |
| `heading level` | 标题层级（1+） |
| `content` | 提取出的文本 |

[完整的 JSON 数据规范](https://opendataloader.org/docs/reference/json-schema)

## 进阶功能

### 带标签 PDF 支持

当 PDF 具有结构标签时，OpenDataLoader 会提取作者期望的**精确布局**，不靠猜测，不靠直觉。标题、列表、表格和阅读顺序会在源文件中保留。

> **输出质量取决于标签质量。** 并非所有带标签 PDF 都有良好标签。对于存在稀疏或错误标签的 PDF，默认启发式模式或 `--hybrid docling-fast` 通常会获得更好的结果。

```python
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    use_struct_tree=True           # 使用原生 PDF 结构标签
)
```

大多数 PDF 解析器完全忽略结构标签。[了解更多](https://opendataloader.org/docs/tagged-pdf)

### AI 安全：提示词注入保护

PDF 可能包含隐藏的提示词注入攻击。OpenDataLoader 会自动过滤：

- 隐藏文本（透明、零字号字体）
- 页面外内容
- 可疑的不可见图层

如需清除敏感数据（电子邮件、URL、电话号码 → 占位符），请显式启用：

```bash
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader-pdf file1.pdf file2.pdf folder/ --sanitize
```

[AI 安全指南](https://opendataloader.org/docs/ai-safety)

### LangChain 集成

```bash
pip install -U langchain-opendataloader-pdf
```

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader

loader = OpenDataLoaderPDFLoader(
    file_path=["file1.pdf", "file2.pdf", "folder/"],
    format="text"
)
documents = loader.load()
```

[LangChain 文档](https://docs.langchain.com/oss/python/integrations/document_loaders/opendataloader_pdf) | [GitHub](https://github.com/opendataloader-project/langchain-opendataloader-pdf) | [PyPI](https://pypi.org/project/langchain-opendataloader-pdf/)

### 进阶选项

```python
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="json,markdown,pdf",
    image_output="embedded",        # "off"、"embedded"（Base64）或 "external"（默认）
    image_format="jpeg",            # "png" 或 "jpeg"
    use_struct_tree=True,           # 使用原生 PDF 结构
)
```

[完整 CLI 选项参考](https://opendataloader.org/docs/reference/cli-options)

## PDF 无障碍处理和 PDF/UA 转换

**问题**：数百万现有 PDF 缺少结构标签，无法满足无障碍法规（EAA、ADA/Section 508、韩国 Digital Inclusion Act）。人工标注每份文档的成本为 50-200 美元，且无法规模化。

**OpenDataLoader 的方法**：与 [PDF Association](https://pdfa.org) 和 [Dual Lab](https://duallab.com)（[veraPDF](https://verapdf.org) 开发者，行业参考级开源 PDF/A 和 PDF/UA 验证器）协作构建。自动加标签遵循 [Well-Tagged PDF specification](https://pdfa.org/resource/well-tagged-pdf/)，并使用 veraPDF 进行程序化验证，即自动针对 PDF 无障碍标准做一致性检查，而不是人工评审。现有开源工具没有能端到端生成带标签 PDF 的方案，大多数都依赖专有 SDK 写入标签。OpenDataLoader 在 Apache 2.0 许可证下完成全部流程。（[协作详情](https://opendataloader.org/docs/tagged-pdf-collaboration)）

| 法规 | 截止日期 | 要求 |
|------------|----------|-------------|
| **European Accessibility Act (EAA)** | June 28, 2025 | 欧盟范围内可访问的数字产品 |
| **ADA & Section 508** | In effect | 美国联邦机构和公共服务场所 |
| **Digital Inclusion Act** | In effect | 韩国数字服务无障碍 |

### 标准和验证

| 方面 | 说明 |
|--------|--------|
| **规范** | PDF Association 的 [Well-Tagged PDF](https://pdfa.org/resource/well-tagged-pdf/) |
| **验证** | [veraPDF](https://verapdf.org) — 行业参考级开源 PDF/A 和 PDF/UA 验证器 |
| **协作** | PDF Association + [Dual Lab](https://duallab.com)（veraPDF 开发者）共同开发标签和验证 |
| **许可证** | 自动加标签 → 带标签 PDF：Apache 2.0（免费）。PDF/UA 导出：企业版 |

### 无障碍处理流程

| 步骤 | 功能 | 状态 | 套餐类型 |
|------|---------|--------|------|
| 1. **检测** | 读取现有 PDF 标签，检测未加标签 PDF | 已发布 | 免费 |
| 2. **自动加标签 → 带标签 PDF** | 为未加标签 PDF 生成结构标签 | 已发布 | 免费（Apache 2.0） |
| 3. **导出 PDF/UA** | 转换为符合 PDF/UA-1 或 PDF/UA-2 的文件 | 💼 可用 | 企业版 |
| 4. **可视化编辑** | 无障碍工作站 — 审查并修复标签 | 💼 可用 | 企业版 |

> **💼 企业版功能** 可按需提供。[联系我们](https://opendataloader.org/contact)开始使用。

### 自动加标签

从未加标签 PDF 生成带标签 PDF，输出为带结构标签（标题、段落、列表、表格、阅读顺序）的屏幕阅读器可用 PDF。

```python
import opendataloader_pdf

# 输入未加标签 PDF → 输出带标签 PDF
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="tagged-pdf"
)
```

```bash
# CLI
opendataloader-pdf --format tagged-pdf file1.pdf file2.pdf folder/
```

可与其他格式组合：`format="json,tagged-pdf"`。

### 端到端合规流程

```
现有 PDF（未加标签）
    │
    ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│  1. 检测           │───>│  2. 自动加标签     │───>│  3. 导出           │───>│  4. 工作站         │
│  （检查标签）      │    │  （→ 带标签 PDF）  │    │  （PDF/UA）        │    │  （可视化编辑器）  │
└────────────────────┘    └────────────────────┘    └────────────────────┘    └────────────────────┘
          │                         │                         │                         │
          ▼                         ▼                         ▼                         ▼
    使用结构树             format="tagged-pdf"          PDF/UA 导出              无障碍工作站
    （现在可用）           （可用，Apache 2.0）          （企业版）               （企业版）
```

[PDF 无障碍处理指南](https://opendataloader.org/docs/accessibility-compliance)

## 未来路线图

| 功能 | 时间线 | 套餐类型 |
|---------|----------|------|
| **[Hancom Data Loader](https://sdk.hancom.com/en/services/1?utm_source=github&utm_medium=readme&utm_campaign=opendataloader-pdf)** — 企业级 AI 文档分析、客户定制模型、基于 VLM 的图表/图片理解、生产级 OCR | Q2-Q3 2026 | 计划中 |
| **结构验证** — 验证 PDF 标签树 | Q3 2026 | 计划中 |

[完整路线图](https://opendataloader.org/docs/upcoming-roadmap)

## 常见问题

### 对 RAG 来说，最好的 PDF 解析器是哪个？

对于 RAG 流程，你需要一个能够保留文档结构、维持正确阅读顺序，并为引用提供元素坐标的解析器。OpenDataLoader 正是为此设计：它输出带边界框的结构化 JSON，用 XY-Cut++ 处理多栏布局，并且无需 GPU 即可本地运行。在混合模式中，它在基准测试里综合排名第一（0.907）。

### 最好的开源 PDF 解析器是哪个？

OpenDataLoader PDF 是唯一同时具备以下能力的开源解析器：基于规则的确定性提取（无需 GPU）、每个元素都有边界框、XY-Cut++ 阅读顺序、内置 AI 安全过滤、原生带标签 PDF 支持，以及用于复杂文档的混合模式。它能在 CPU 本地运行，同时综合准确率排名第一（0.907）。

### 我该怎么为大模型从 PDF 中提取表格？
OpenDataLoader 使用边框分析和文本聚类检测表格，并保留行/列结构。对于复杂表格，启用混合模式可将准确率提升 90% 以上（TEDS 分数从 0.489 到 0.928）：

```python
# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="json",
    hybrid="docling-fast"           # 用于复杂表格
)
```

### 它和 docling、marker 或 pymupdf4llm 相比怎么样？

OpenDataLoader [hybrid] 在阅读顺序、表格和标题准确率上综合排名第一（0.907）。关键差异：docling（0.882）表现强，但缺少边界框和 AI 安全过滤。marker（0.861）需要 GPU，且慢 1000 倍（53.932s/page）。pymupdf4llm（0.732）速度快，但表格（0.401）和标题（0.412）准确率较低。OpenDataLoader 是唯一同时具备确定性本地提取、每个元素边界框和内置提示词注入防护的解析器。参见[完整基准测试](https://github.com/opendataloader-project/opendataloader-bench)。

### 我能在不把数据传到云端的前提下使用这个工具吗？

可以。OpenDataLoader 100% 本地运行。没有 API 调用，没有数据传输，你的文档不会离开你的环境。混合模式后端也在你的机器本地运行。非常适合法律、医疗和金融文档。

### 这个工具支持对扫描版 PDF 的 OCR 吗？
支持，通过混合模式实现。使用 `pip install "opendataloader-pdf[hybrid]"` 安装，使用 `--force-ocr` 启动后端，然后照常处理。通过 `--ocr-lang` 支持韩语、日语、中文、阿拉伯语等多种语言。

### 支持中文、韩文、日文文档吗？
支持。对于数字 PDF，文本提取开箱即用。对于扫描 PDF，请使用带 `--force-ocr --ocr-lang "ko,en"` 的混合模式（或 `ja`、`ch_sim`、`ch_tra`）。即将推出：[Hancom Data Loader](https://sdk.hancom.com/en/services/1?utm_source=github&utm_medium=readme&utm_campaign=opendataloader-pdf) 集成，提供企业级 AI 文档分析，内置生产级 OCR，以及针对你的具体文档类型和工作流优化的客户定制模型。

### 它有多快？

本地模式在 CPU 上每秒处理 60 页以上（0.02s/page）。混合模式每秒处理 2 页以上（0.46s/page），在复杂文档上准确率显著更高。无需 GPU。基准测试运行于 Apple M4。[完整基准测试详情](https://github.com/opendataloader-project/opendataloader-bench)。使用多进程批处理时，在 8+ 核机器上吞吐量每秒超过 100 页。

### 它支持处理多栏布局吗？

支持。OpenDataLoader 使用 XY-Cut++ 阅读顺序分析，能够正确排列多栏页面、侧栏和混合布局中的文本顺序。本地模式和混合模式都无需额外配置即可使用。

### 什么是混合模式？

混合模式将快速本地 Java 处理与 AI 后端结合。简单页面在本地处理（0.02s/page）；复杂页面（表格、扫描内容、公式、图表）会自动路由到 AI 后端，以获得更高准确率。后端在你的机器本地运行，无需云端。参见[我应该用哪个模式？](#我应该用哪个模式)和[混合模式指南](https://opendataloader.org/docs/hybrid-mode)。

### 它支持 LangChain 吗？

支持。安装 `langchain-opendataloader-pdf` 即可使用官方 LangChain document loader 集成。参见 [LangChain docs](https://docs.langchain.com/oss/python/integrations/document_loaders/opendataloader_pdf)。

### 我该怎么为 RAG 分割 PDF？

OpenDataLoader 输出保留标题、表格和列表的结构化 Markdown，非常适合作为语义分块的输入。JSON 输出中的每个元素都包含 `type`、`heading level` 和 `page number`，因此你可以按章节或页面边界切分。大多数 RAG 流程可以使用 `format="markdown"` 解析文本块；如果需要元素级控制，则使用 `format="json"`。与 LangChain 的 `RecursiveCharacterTextSplitter` 或你自己的基于标题的切分器配合效果最佳。

### 如何让 RAG 回答显示 PDF 来源？

JSON 输出中的每个元素都包含 `bounding box`（以 PDF 点为单位的 `[left, bottom, right, top]`）和 `page number`。当你的 RAG 流程返回答案时，可以把表示来源的文本块映射回它的边界框，在原始 PDF 中高亮准确位置。这实现了“点击查看来源”的体验：用户可以看到答案来自哪个段落、表格或图形。没有其他开源解析器默认提供每个元素的边界框。

### 我该怎么为大模型把 PDF 转换为 Markdown？

```python
import opendataloader_pdf

# 请在一次调用批量处理所有文件 — 因为每次调用 convert() 就会创建一个新的 JVM 进程，所以重复调用会很慢。
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "folder/"],
    output_dir="output/",
    format="markdown"
)
```

OpenDataLoader 在 Markdown 输出中保留标题层级、表格结构和阅读顺序。对于带无边框表格或扫描页面的复杂文档，请使用混合模式（`hybrid="docling-fast"`）以获得更高准确率。输出足够干净，可以直接放入大模型上下文窗口或 RAG 分块流程。

### 有自动化 PDF 无障碍修复的工具吗？

有。OpenDataLoader 是首个端到端自动化 PDF 无障碍处理的开源工具。它与 [PDF Association](https://pdfa.org) 和 [Dual Lab](https://duallab.com)（veraPDF 开发者）协作构建，自动加标签遵循 Well-Tagged PDF specification，并使用 veraPDF 进行程序化验证。布局分析引擎会检测文档结构（标题、表格、列表、阅读顺序），并自动生成无障碍标签。自动加标签在 Apache 2.0 下将未加标签 PDF 转换为带标签 PDF，无需专有 SDK 依赖。使用 `format="tagged-pdf"`（Python/Node.js）或 `--format tagged-pdf`（CLI）。对于需要完整 PDF/UA 合规的组织，企业附加功能提供 PDF/UA 导出和可视化标签编辑器。这可以替代通常情况下每份文档成本 50-200 美元以上的人工修复流程。

### 这真的是第一款开源的自动加标签工具吗？

是。现有工具要么依赖专有 SDK 写入结构标签，要么只输出非 PDF 格式（例如 Docling 输出 Markdown/JSON，但不能生成带标签 PDF），要么需要人工介入。OpenDataLoader 是第一个在开源许可证（Apache 2.0）下完整实现“布局分析 → 标签生成 → 带标签 PDF 输出”的工具，没有专有依赖。自动加标签遵循 PDF Association 的 Well-Tagged PDF specification，并使用 veraPDF 这个行业参考级开源 PDF/A 和 PDF/UA 验证器进行验证。

### 我该如何将现有 PDF 转换成 PDF/UA？

OpenDataLoader 提供端到端流程：检测现有 PDF 标签（`use_struct_tree=True`）、将未加标签 PDF 自动加标签为带标签 PDF（`format="tagged-pdf"`，Apache 2.0 下免费），并导出为 PDF/UA-1 或 PDF/UA-2（企业附加功能）。自动加标签遵循 PDF Association 的 Well-Tagged PDF specification，并使用 veraPDF 验证。自动加标签生成带标签 PDF；PDF/UA 导出是最后一步。[联系我们](https://opendataloader.org/contact)获取企业集成。

### 如何让我的 PDF 满足 EAA 的合规要求？

European Accessibility Act 要求在 2025 年 6 月 28 日前提供可访问的数字产品。OpenDataLoader 支持完整修复工作流：检测 → 自动加标签 → 带标签 PDF → PDF/UA 导出。自动加标签遵循 PDF Association 的 Well-Tagged PDF specification，并使用 veraPDF 验证，确保输出符合标准。自动加标签到带标签 PDF 的能力在 Apache 2.0 下开源。PDF/UA 导出和无障碍工作站是企业附加功能。参见我们的 [PDF 无障碍处理指南](https://opendataloader.org/docs/accessibility-compliance)。

### OpenDataLoader PDF 免费吗？

核心库在 **Apache 2.0 下开源**，可免费商用。这包括所有提取功能（文本、表格、图片、OCR、公式、通过混合模式处理图表）、AI 安全过滤、带标签 PDF 支持，以及自动加标签到带标签 PDF。我们承诺保持核心无障碍流程（布局分析 → 自动加标签 → 带标签 PDF）免费且开源。对于需要端到端法规合规的组织，可使用企业附加功能（PDF/UA 导出、无障碍工作站）。

### 为什么许可证从 MPL 2.0 变成了 Apache 2.0？

MPL 2.0 要求文件级 copyleft，这通常会在企业采用前触发法律审查。Apache 2.0 是完全宽松许可，没有 copyleft 义务，更容易集成到商业项目中。如果你正在使用 2.0 之前的版本，它仍在 MPL 2.0 下，你可以继续使用。升级到 2.0+ 意味着你的项目遵循 Apache 2.0 条款，该条款明确更宽松，没有额外义务，你这边无需采取任何操作。

## 文档

- [快速开始 (Python)](https://opendataloader.org/docs/quick-start-python)
- [快速开始 (Node.js)](https://opendataloader.org/docs/quick-start-nodejs)
- [快速开始 (Java)](https://opendataloader.org/docs/quick-start-java)
- [JSON 规范参考](https://opendataloader.org/docs/reference/json-schema)
- [CLI 选项](https://opendataloader.org/docs/reference/cli-options)
- [混合模式指南](https://opendataloader.org/docs/hybrid-mode)
- [带标签 PDF 支持](https://opendataloader.org/docs/tagged-pdf)
- [AI 安全功能](https://opendataloader.org/docs/ai-safety)
- [PDF 无障碍处理](https://opendataloader.org/docs/accessibility-compliance)

## 贡献

欢迎贡献！请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

## 许可证

[Apache License 2.0](LICENSE)

> **注意：** 2.0 之前的版本采用 [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/) 许可。

---

**觉得有用？** 给我们一个 star，帮助更多人发现 OpenDataLoader。
