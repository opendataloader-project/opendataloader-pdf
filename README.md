# open-pdf-dataloader

A Fast, Accurate and Secure PDF Extraction Engine — Built for AI Understanding

Open PDF Dataloader is a high-performance document parsing engine that combines the speed of rule-based processing,
the accuracy of structured extraction, and the security of local execution 
— making it the perfect foundation for AI-driven document workflows.

## Key Features

- ⚡ Fast – Efficient batch processing for thousands of documents
- ✅ Accurate – Extracts text, tables, images, and layout with high precision
- 🔐 Secure – Runs fully offline, ideal for sensitive or regulated environments
- 🧠 AI-Ready – Structured outputs in Markdown or JSON, optimized for LLMs
- 🔧 Customizable – Rule sets can be adapted for domain-specific structures
- 🧬 AI Add-on Compatible – Seamlessly upgrade to AI-powered modules when needed

## Quick Start

### Prerequisites

Java 8+ runtime environment.

### Build instructions

Build and install using Maven command

> mvn clean install

If the build is successful, the resulting `jar` file will be created in the path below.

```
open-pdf-dataloader/target
```

### Install script

Build and install using `install_ocr.bat` script.

Prerequisites for using this script:

* Git
* Java 8+
* Maven v3+
* cURL

This script download and build `tesseract_ocr` branch of repo 
[opendataloader-pdf](https://github.com/opendataloader-project/opendataloader-pdf), and also download language data 
files for OCR Tesseract library from https://github.com/tesseract-ocr/tessdata/raw/main/. 
By default, only english language is downloaded:

> install_ocr.bar

It is possible to download other languages by specifying language codes (space-separated):

> install_ocr.bat eng rus spa

Common Language Codes:
- eng - English
- kor - Korean
- rus - Russian
- spa - Spanish
- deu - German
- fra - French 
- chi_sim - Simplified Chinese 
- jpn - Japanese

### CLI usage

```
java -jar ... [options] <INPUT FILE OR FOLDER>
```

This generates a JSON file with layout recognition results in the specified output folder. Additionally, annotated PDF
with recognized structures and Markdown file are generated if options `--pdf` and `--markdown` are specified.

By default all line breaks and hyphenation characters are removed, the Markdown does not include any images and does not
use any HTML.

The option `--keeplinebreaks` to preserve the original line breaks text content in JSON and Markdown output.

The option `--html` enables use of HTML in Markdown, which may improve Markdown preview in processors that support HTML
tags. The option `--addimagetomarkdown` enables inclusion of image references into the output Markdown. The images are
extracted from PDF as individual files and stored in a subfolder next to the Markdown output.

The complete set of options:

```
Options:
-f,--folder <arg>          Specify output folder (default the folder of the input PDF)
-klb,--keeplinebreaks      Keep line breaks
-ht,--findhiddentext       Find hidden text
-html,--htmlinmarkdown     Use html in markdown
-im,--addimagetomarkdown   Add images to markdown
-markdown,--markdown       Generates markdown output
-p,--password <arg>        Specifies password
-pdf,--pdf                 Generates pdf output
--ocrlanguages             Specifies `plus-separated` language codes for OCR Tesseract library (eng by default)
```

## Java code integration

To integrate Layout recognition API into Java code, one can follow the sample code below.

```java
import com.hancom.opendataloader.utils.Config;
import com.hancom.opendataloader.DocumentProcessor;

import java.io.IOException;

public class Sample {

    public static void main(String[] args) {
        //create default config
        Config config = new Config();

        //set output folder relative to the input PDF
        //if the output folder is not set, the current folder of the input PDF is used
        config.setOutputFolder("output");

        //generating pdf output file
        config.setGeneratePDF(true);

        //set password of input pdf file
        config.setPassword("password");

        //generate markdown output file
        config.setGenerateMarkdown(true);

        //enable html in markdown output file
        config.setUseHTMLInMarkdown(true);

        //add images to markdown output file
        config.setAddImageToMarkdown(true);

        //disable json output file
        config.setGenerateJSON(false);

        //keep line breaks
        config.setKeepLineBreaks(true);

        //find hidden text
        config.setFindHiddenText(true);

        //specify `plus-separated` languages for OCR Tesseract library
        config.setOcrLanguages("eng+kor");

        try {
            //process pdf file
            DocumentProcessor.processFile("input.pdf",config);
        } catch (Exception exception) {
            //exception during processing
        }
    }
}
```

## Schema of the JSON output

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

## Contributing

Please
read [CONTRIBUTING.md](https://github.com/hancom-open-dataloader/open-pdf-dataloader/blob/develop/CONTRIBUTING.md) for
details.
