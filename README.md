# OpenDataLoader PDF

![Pre-release](https://img.shields.io/badge/Pre--release-FFA500&logo=github)
[![License](https://img.shields.io/pypi/l/opendataloader-pdf.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)
![Java](https://img.shields.io/badge/Java-11+-blue.svg)
![Python](https://img.shields.io/badge/Python-3.8+-blue.svg)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.opendataloader-project/opendataloader-pdf-core.svg)](https://search.maven.org/artifact/io.github.opendataloader-project/opendataloader-pdf-core)
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

- 🖨️ **OCR for scanned PDFs** — Extract data from image-only pages
- 🧠 **Table AI option** — Higher accuracy for tables with borderless or merged cells
- ⚡ **Performance Benchmarks** — Transparent evaluations with open datasets and metrics, reported regularly
- 🛡️ **AI Red Teaming** — Transparent adversarial benchmarks with datasets and metrics, reported regularly

<br/>

## Prerequisites

- Java 11 or higher must be installed and available in your system's PATH.
- Python 3.8+

<br/>

## Python

### Installation

```sh
pip install -U opendataloader-pdf
```

### Usage

- input_path can be either the path to a single document or the path to a folder.
- If you don’t specify an output_folder, the output data will be saved in the same directory as the input document.

```python
import opendataloader_pdf

opendataloader_pdf.run(
    input_path="path-to-document.pdf",
    output_folder="path-to-output",
    generate_markdown=True,
    generate_html=True,
    generate_annotated_pdf=True,
)
```

### Function: run()

The main function to process PDFs.

| Parameter                | Type   | Required | Default      | Description                                                                                                                         |
|--------------------------| ------ | -------- |--------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `input_path`             | `str`  | ✅ Yes    | —            | Path to the input PDF file or folder.                                                                                               |
| `output_folder`          | `str`  | No       | input folder | Path to the output folder.                                                                                                          |
| `password`               | `str`  | No       | `None`       | Password for the PDF file.                                                                                                          |
| `replace_invalid_chars`  | `str`  | No       | `" "`       | Character to replace invalid or unrecognized characters (e.g., �, \u0000)                                                           |
| `content_safety_off`     | `str`  | No       | `None`       | Disables one or more content safety filters. Accepts a comma-separated list of filter names. Arguments: all, hidden-text, off-page. |
| `generate_markdown`      | `bool` | No       | `False`      | If `True`, generates a Markdown output file.                                                                                        |
| `generate_html`          | `bool` | No       | `False`      | If `True`, generates an HTML output file.                                                                                           |
| `generate_annotated_pdf` | `bool` | No       | `False`      | If `True`, generates an annotated PDF output file.                                                                                  |
| `keep_line_breaks`       | `bool` | No       | `False`      | If `True`, keeps line breaks in the output.                                                                                         |
| `html_in_markdown`       | `bool` | No       | `False`      | If `True`, uses HTML in the Markdown output.                                                                                        |
| `add_image_to_markdown`  | `bool` | No       | `False`      | If `True`, adds images to the Markdown output.                                                                                      |
| `debug`                  | `bool` | No       | `False`      | If `True`, prints CLI messages to the console during execution.                                                                     |

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

- `inputPath` can be either the path to a single document or the path to a folder.
- If you don’t specify an `outputFolder`, the output data will be saved in the same directory as the input document.

```typescript
import { run } from '@opendataloader/pdf';

async function main() {
  try {
    const output = await run('path-to-document.pdf', {
      outputFolder: 'path-to-output',
      generateMarkdown: true,
      generateHtml: true,
      generateAnnotatedPdf: true,
      debug: true,
    });
    console.log('PDF processing complete.', output);
  } catch (error) {
    console.error('Error processing PDF:', error);
  }
}

main();
```

### Function: run()

`run(inputPath: string, options?: RunOptions): Promise<string>`

The main function to process PDFs.

**Parameters**

| Parameter   | Type     | Required | Description                           |
| ----------- | -------- | -------- | ------------------------------------- |
| `inputPath` | `string` | ✅ Yes    | Path to the input PDF file or folder. |
| `options`   | `RunOptions` | No       | Configuration options for the run.    |

**RunOptions**

| Property                | Type      | Default       | Description                                                                 |
| ----------------------- | --------- | ------------- | --------------------------------------------------------------------------- |
| `outputFolder`          | `string`  | `undefined`   | Path to the output folder. If not set, output is saved next to the input.   |
| `password`              | `string`  | `undefined`   | Password for the PDF file.                                                  |
| `replaceInvalidChars`   | `string`  | `" "`         | Character to replace invalid or unrecognized characters (e.g., , \u0000).  |
| `content_safety_off`     | `string`  | `undefined`   | Disables one or more content safety filters. Accepts a comma-separated list of filter names. Arguments: all, hidden-text, off-page. |
| `generateMarkdown`      | `boolean` | `false`       | If `true`, generates a Markdown output file.                                |
| `generateHtml`          | `boolean` | `false`       | If `true`, generates an HTML output file.                                   |
| `generateAnnotatedPdf`  | `boolean` | `false`       | If `true`, generates an annotated PDF output file.                          |
| `keepLineBreaks`        | `boolean` | `false`       | If `true`, keeps line breaks in the output.                                 |
| `htmlInMarkdown`        | `boolean` | `false`       | If `true`, uses HTML in the Markdown output.                                |
| `addImageToMarkdown`    | `boolean` | `false`       | If `true`, adds images to the Markdown output.                              |
| `debug`                 | `boolean` | `false`       | If `true`, prints CLI messages to the console during execution.             |

<br/>

## Java

For various example templates, including Gradle and Maven, please refer to https://github.com/opendataloader-project/opendataloader-pdf/tree/main/examples/java.

### Dependency

To include OpenDataLoader PDF in your Maven project, add the dependency below to your `pom.xml` file.

Check for the latest version on [Maven Central](https://search.maven.org/artifact/io.github.opendataloader-project/opendataloader-pdf-core).

```xml
<project>
    <!-- other configurations... -->

    <dependencies>
        <dependency>
            <groupId>io.github.opendataloader-project</groupId>
            <artifactId>opendataloader-pdf-core</artifactId>
            <version>0.0.15</version>
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

The full API documentation is available at [javadoc](https://javadoc.io/doc/io.github.opendataloader-project/opendataloader-pdf-core/latest/)

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

Build and package using Maven command:

```sh
mvn clean package -f java/pom.xml
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
The images are extracted from PDF as individual files and stored in a subfolder next to the Markdown output.

#### Available options:

```
Options:
-o,--output-dir <arg>           Specifies the output directory for generated files
--keep-line-breaks              Preserves original line breaks in the extracted text
--content-safety-off <arg>      Disables one or more content safety filters. Accepts a comma-separated list of filter names. Arguments: all, hidden-text, off-page
--markdown-with-html            Sets the data extraction output format to Markdown with rendering complex elements like tables as HTML for better structure
--markdown-with-images          Sets the data extraction output format to Markdown with extracting images from the PDF and includes them as links
--markdown                      Sets the data extraction output format to Markdown
--html                          Sets the data extraction output format to HTML
-p,--password <arg>             Specifies the password for an encrypted PDF
--pdf                           Generates a new PDF file where the extracted layout data is visualized as annotations
--replace-invalid-chars <arg>   Replaces invalid or unrecognized characters (e.g., �, \u0000) with the specified character
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
