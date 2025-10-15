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
- 🛡️ **AI-Safety** — Auto-Filters likely prompt-injection content - [Learn more about AI-Safety](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/docs/AI_SAFETY.md)
- 🖍️ **Annotated PDF Visualization** — See detected structures overlaid on the original

[Download Annotated PDF Sample](https://raw.githubusercontent.com/opendataloader-project/opendataloader-pdf/main/resources/1901.03003_annotated.pdf)

![Annotated PDF Preview](https://raw.githubusercontent.com/opendataloader-project/opendataloader-pdf/main/resources/example_annotated_pdf.png)

<br/>

## 🚀 Upcoming Features

**Scheduled for November**
- 📄 **Tagged PDF** — Develop advanced data extraction technology based on Tagged PDF
- ⚡ **Performance Improvement** — Enhance the inference skill for greater accuracy and speed. 
- 📊 **Benchmarks & Datasets** — Publish transparent evaluations using open datasets and standardized metrics. 
- 🎯 **Metrics** — Publish the calculation methods to transparently share benchmark results.

<br/>

**Scheduled for December**
- 🖨️ **OCR for scanned PDFs** — Extract data from image-only pages. 
- 🧠 **Table AI option** — Higher accuracy for tables with borderless or merged cells. 
<br/>

**Scheduled for 2026**
- 🛡️ **AI Red Teaming** — Transparent adversarial benchmarks with datasets and metrics, then reported regularly.
  
<br/>

## Prerequisites

- Java 11 or higher must be installed and available in your system's PATH.
- Python 3.9+

<br/>

## Python

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
    format=["json", "html", "pdf", "markdown"]
)
```

If you want to run it via CLI, you can use the following command on the terminal:

```bash
opendataloader-pdf path/to/document.pdf path/to/folder -o path/to/output -f json html pdf markdown
```

### Function: convert()

The main function to process PDFs.

| Parameter               | Type                  | Required | Default      | Description                                                                                                                              |
|-------------------------|-----------------------| -------- |--------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `input_path`            | `List[str]`           | ✅ Yes    | —            | One or more PDF file paths or directories to process.                                                                                    |
| `output_dir`            | `Optional[str]`       | No       | input folder | Directory where outputs are written.                                                                                                     |
| `password`              | `Optional[str]`       | No       | `None`       | Password used for encrypted PDFs.                                                                                                        |
| `format`                | `Optional[List[str]]` | No | `None`       | Output formats to generate (e.g. `"json"`, `"html"`, `"pdf"`, `"text"`, `"markdown"`, `"markdown-with-html"`, `"markdown-with-images"`). |
| `quiet`                 | `bool`                | No       | `False`      | Suppresses CLI logging output when `True`.                                                                                               |
| `content_safety_off`    | `Optional[List[str]]` | No | `None`       | List of content safety filters to disable (e.g. `"all"`, `"hidden-text"`, `"off-page"`, `"tiny"`, `"hidden-ocg"`).                       |
| `keep_line_breaks`      | `bool`                | No       | `False`      | Preserves line breaks in text output when `True`.                                                                                        |
| `replace_invalid_chars` | `Optional[str]`       | No       | `None`       | Replacement character for invalid or unrecognized characters (e.g., �, `\u0000`).                                                        |
| `use_struct_tree`       | `bool `               | No       | `False`      | Enable processing structure tree (disabled by default).                                                                                  |

### Function: run()

Deprecated.

<br/>

## Node.js / NPM

**Note:** This package is a wrapper around a Java CLI and is intended for use in a Node.js backend environment. It cannot be used in a browser-based frontend.

### Prerequisites

- Java 11 or higher must be installed and available in your system's PATH.

### Installation

```sh
npm install @opendataloader/pdf
```

### Usage

`inputPath` can be either the path to a single document or the path to a folder.

```typescript
import { convert } from '@opendataloader/pdf';

async function main() {
  try {
    await convert(['path/to/document.pdf', 'path/to/folder'], {
      outputDir: 'path/to/output',
      format: ['json', 'html', 'pdf', 'markdown'],
    });
    console.log('convert() complete');
  } catch (error) {
    console.error('Error processing PDF:', error);
  }
}

main();
```
### Function: convert()

`convert(inputPaths: string[], options?: ConvertOptions): Promise<string>`

Multi-input helper matching the Python wrapper.

| Property                       | Type       | Default     | Description                                                                                                                  |
|--------------------------------| ---------- | ----------- |------------------------------------------------------------------------------------------------------------------------------|
| `inputPaths`                   | `string[]` | —           | One or more file paths or directories to process.                                                                            |
| `options.outputDir`            | `string`   | `undefined` | Directory where outputs are written.                                                                                         |
| `options.password`             | `string`   | `undefined` | Password for encrypted PDFs.                                                                                                 |
| `options.format`               | `string[]` | `undefined` | Output formats (any combination of `json`, `text`, `html`, `pdf`, `markdown`, `markdown-with-html`, `markdown-with-images`). |
| `options.quiet`                | `boolean`  | `false`     | Suppress CLI logging output and prevent streaming.                                                                           |
| `options.contentSafetyOff`     | `string[]` | `undefined` | Disable one or more content safety filters (`all`, `hidden-text`, `off-page`, `tiny`, `hidden-ocg`).                         |
| `options.keepLineBreaks`       | `boolean`  | `false`     | Preserve line breaks in text output.                                                                                         |
| `options.replaceInvalidChars`  | `string`   | `undefined` | Replacement character for invalid or unrecognized characters.                                                                |
| `options.useStructTree`        | `boolean`  | `false`     | Enable processing structure tree (disabled by default).                                                                      |

### Function: run()

Deprecated.

### CLI

```bash
npx @opendataloader/pdf path/to/document.pdf path/to/folder -o path/to/output -f json html pdf markdown
```

Or install globally:

```bash
npm install -g @opendataloader/pdf
```

Then run:

```bash
opendataloader-pdf path/to/document.pdf path/to/folder -o path/to/output -f json html pdf markdown
```

#### Available options

```
  -o, --output-dir <path>             Directory where outputs are written
  -p, --password <password>           Password for encrypted PDFs
  -f, --format <value...>             Output formats to generate (json, text, html, pdf, markdown, markdown-with-html, markdown-with-images)
  -q, --quiet                         Suppress CLI logging output
      --content-safety-off <mode...>  Disable one or more content safety filters (all, hidden-text, off-page, tiny, hidden-ocg)
      --keep-line-breaks              Preserve line breaks in text output
      --replace-invalid-chars <c>     Replacement character for invalid or unrecognized characters
  -h, --help                          Show usage information
      --use-struct-tree               Enable processing structure tree (disabled by default)
```

<br/>

## Java

For various example templates, including Gradle and Maven, please refer to [Examples](https://github.com/opendataloader-project/opendataloader-pdf-examples).

### Dependency

To include OpenDataLoader PDF in your Maven project, add the dependency below to your `pom.xml` file.

Check for the latest version on [Maven Central](https://search.maven.org/artifact/org.opendataloader/opendataloader-pdf-core).

```xml
<project>
    <!-- other configurations... -->

    <dependencies>
        <dependency>
            <groupId>org.opendataloader</groupId>
            <artifactId>opendataloader-pdf-core</artifactId>
            <version>1.1.2</version>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
            <id>vera-dev</id>
            <name>Vera development</name>
            <url>https://artifactory.openpreservation.org/artifactory/vera-dev</url>
        </repository>
    </repositories>
    <pluginRepositories>
        <pluginRepository>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
            <id>vera-dev</id>
            <name>Vera development</name>
            <url>https://artifactory.openpreservation.org/artifactory/vera-dev</url>
        </pluginRepository>
    </pluginRepositories>

    <!-- other configurations... -->
</project>
```


### Java code integration

To integrate Layout recognition API into Java code, one can follow the sample code below.

```java
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

import java.io.IOException;

public class Sample {

    public static void main(String[] args) {
        Config config = new Config();
        config.setOutputFolder("path/to/output");
        config.setGeneratePDF(true);
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);

        try {
            OpenDataLoaderPDF.processFile("path/to/document.pdf", config);
        } catch (Exception exception) {
            //exception during processing
        }
    }
}
```

### API Documentation

The full API documentation is available at [javadoc](https://javadoc.io/doc/org.opendataloader/opendataloader-pdf-core/latest/)

<br/>

## Docker

Download sample PDF

```sh
curl -L -o 1901.03003.pdf https://arxiv.org/pdf/1901.03003
```

Run opendataloader-pdf in Docker container

```
docker run --rm -v "$PWD":/work ghcr.io/opendataloader-project/opendataloader-pdf-cli:latest /work/1901.03003.pdf --markdown --html --pdf
```

<br/>

## Developing with OpenDataLoader PDF

### Build

Build and install using Maven command:

```sh
mvn clean install -f java/pom.xml
```

If the build is successful, the resulting `jar` file will be created in the path below.

```sh
java/opendataloader-pdf-cli/target
```

### CLI usage

```sh
java -jar opendataloader-pdf-cli-<VERSION>.jar [options] <INPUT FILE OR FOLDER>
```

This generates a JSON file with layout recognition results in the specified output folder. 
Additionally, annotated PDF with recognized structures, Markdown and Html are generated if options `--pdf`, `--markdown` and `--html` are specified.

By default all line breaks and hyphenation characters are removed, the Markdown does not include any images and does not use any HTML.

The option `--keep-line-breaks` to preserve the original line breaks text content in JSON and Markdown output.
The option `--content-safety-off` disables one or more content safety filters. Accepts a comma-separated list of filter names.
The option `--markdown-with-html` enables use of HTML in Markdown, which may improve Markdown preview in processors that support HTML tags. 
The option `--markdown-with-images` enables inclusion of image references into the output Markdown. 
The option `--replace-invalid-chars` replaces invalid or unrecognized characters (e.g., �, \u0000) with the specified character.
The option `--use-struct-tree` enables processing structure tree (disabled by default).
The images are extracted from PDF as individual files and stored in a subfolder next to the Markdown output.

#### Available options:

```
Options:
-o,--output-dir <arg>           Specifies the output directory for generated files
-p,--password <arg>             Specifies the password for an encrypted PDF
-f,--format <arg>               List of output formats to generate (json, text, html, pdf, markdown, markdown-with-html, markdown-with-images). Default: json
-q,--quiet                      Suppresses console logging output
--content-safety-off <arg>      Disables one or more content safety filters. Accepts a list of filter names. Arguments: all, hidden-text, off-page, tiny, hidden-ocg
--keep-line-breaks              Preserves original line breaks in the extracted text
--replace-invalid-chars <arg>   Replaces invalid or unrecognized characters (e.g., �, \u0000) with the specified character
--use-struct-tree               Enables processing structure tree (disabled by default)
```

The legacy options (for backward compatibility):

```
--no-json                       Disables the JSON output format
--html                          Sets the data extraction output format to HTML
--pdf                           Generates a new PDF file where the extracted layout data is visualized as annotations
--markdown                      Sets the data extraction output format to Markdown
--markdown-with-html            Sets the data extraction output format to Markdown with rendering complex elements like tables as HTML for better structure
--markdown-with-images          Sets the data extraction output format to Markdown with extracting images from the PDF and includes them as links
```

### Schema of the JSON output

Root json node

| Field             | Type    | Optional | Description                        |
|-------------------|---------|----------|------------------------------------|
| file name         | string  | no       | Name of processed pdf file         |
| number of pages   | integer | no       | Number of pages in pdf file        |
| author            | string  | no       | Author of pdf file                 |
| title             | string  | no       | Title of pdf file                  |
| creation date     | string  | no       | Creation date of pdf file          |
| modification date | string  | no       | Modification date of pdf file      |
| kids              | array   | no       | Array of detected content elements |

Common fields of content json nodes

| Field        | Type    | Optional | Description                                                                                                                                                                                           |
|--------------|---------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id           | integer | yes      | Unique id of content element                                                                                                                                                                          |
| level        | string  | yes      | Level of content element                                                                                                                                                                              |
| type         | string  | no       | Type of content element<br/>Possible types: `footer`, `header`, `heading`, `line`, `table`, `table row`, `table cell`, `paragraph`, `list`, `list item`, `image`, `line art`, `caption`, `text block` |
| page number  | integer | no       | Page number of content element                                                                                                                                                                        |
| bounding box | array   | no       | Bounding box of content element                                                                                                                                                                       |

Specific fields of text content json nodes (`caption`, `heading`, `paragraph`)

| Field      | Type   | Optional | Description       |
|------------|--------|----------|-------------------|
| font       | string | no       | Font name of text |
| font size  | double | no       | Font size of text |
| text color | array  | no       | Color of text     |
| content    | string | no       | Text value        |

Specific fields of `table` json nodes

| Field             | Type    | Optional | Description                    |
|-------------------|---------|----------|--------------------------------|
| number of rows    | integer | no       | Number of table rows           |
| number of columns | integer | no       | Number of table columns        |
| rows              | array   | no       | Array of table rows            |
| previous table id | integer | yes      | Id of previous connected table |
| next table id     | integer | yes      | Id of next connected table     |

Specific fields of `table row` json nodes

| Field      | Type    | Optional | Description          |
|------------|---------|----------|----------------------|
| row number | integer | no       | Number of table row  |
| cells      | array   | no       | Array of table cells |

Specific fields of `table cell` json nodes

| Field         | Type    | Optional | Description                          |
|---------------|---------|----------|--------------------------------------|
| row number    | integer | no       | Row number of table cell             |
| column number | integer | no       | Column number of table cell          |
| row span      | integer | no       | Row span of table cell               |
| column span   | integer | no       | Column span of table cell            |
| kids          | array   | no       | Array of table cell content elements |

Specific fields of `heading` json nodes

| Field         | Type    | Optional | Description              |
|---------------|---------|----------|--------------------------|
| heading level | integer | no       | Heading level of heading |

Specific fields of `list` json nodes

| Field                | Type    | Optional | Description                         |
|----------------------|---------|----------|-------------------------------------|
| number of list items | integer | no       | Number of list items                |
| numbering style      | string  | no       | Numbering style of this list        |
| previous list id     | integer | yes      | Id of previous connected list       |
| next list id         | integer | yes      | Id of next connected list           |
| list items           | array   | no       | Array of list item content elements |

Specific fields of `list item` json nodes

| Field | Type  | Optional | Description                         |
|-------|-------|----------|-------------------------------------|
| kids  | array | no       | Array of list item content elements |

Specific fields of `header` and `footer` json nodes

| Field | Type  | Optional | Description                             |
|-------|-------|----------|-----------------------------------------|
| kids  | array | no       | Array of header/footer content elements |

Specific fields of `text block` json nodes

| Field | Type  | Optional | Description                          |
|-------|-------|----------|--------------------------------------|
| kids  | array | no       | Array of text block content elements |


## 🤝 Contributing

We believe that great software is built together.

Your contributions are vital to the success of this project.

Please read [CONTRIBUTING.md](https://github.com/hancom-inc/opendataloader-pdf/blob/main/CONTRIBUTING.md) for details on how to contribute.

## 💖 Community & Support
Have questions or need a little help? We're here for you!🤗

- [GitHub Discussions](https://github.com/hancom-inc/opendataloader-pdf/discussions): For Q&A and general chats. Let's talk! 🗣️
- [GitHub Issues](https://github.com/hancom-inc/opendataloader-pdf/issues): Found a bug? 🐛 Please report it here so we can fix it.

## ✨ Our Branding and Trademarks 

We love our brand and want to protect it!

This project may contain trademarks, logos, or brand names for our products and services.

To ensure everyone is on the same page, please remember these simple rules:

- **Authorized Use**: You're welcome to use our logos and trademarks, but you must follow our official brand guidelines.
- **No Confusion**: When you use our trademarks in a modified version of this project, it should never cause confusion or imply that Hancom officially sponsors or endorses your version.
- **Third-Party Brands**: Any use of trademarks or logos from other companies must follow that company’s specific policies.

## ⚖️ License

This project is licensed under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/).

For the full license text, see [LICENSE](LICENSE).

For information on third-party libraries and components, see:
- [THIRD_PARTY_LICENSES](./THIRD_PARTY/THIRD_PARTY_LICENSES.md)
- [THIRD_PARTY_NOTICES](./THIRD_PARTY/THIRD_PARTY_NOTICES.md)
- [licenses/](./THIRD_PARTY/licenses/)
