# OpenDataLoader PDF 
![Pre-release](https://img.shields.io/badge/Pre--release-FFA500?style=for-the-badge&logo=github)
<p></p>
A Fast, Accurate and Secure PDF Extraction Engine — Built for AI Understanding.
<p></p>
Hancom OpenDataLoader PDF is a high-performance document parsing engine that simplifies document processing, 
combining the speed of rule-based processing, the accuracy of structured extraction, and the security of local execution. 
It's the perfect foundation for AI-driven document workflows and seamless integrations with the generative AI ecosystem.

<br>

## 🌟 Key Features
- ⚡ Fast – Efficient batch processing for thousands of documents.
- ✅ Accurate – Extracts text, tables, images, and layout with high precision.
- 🔐 Secure – Runs fully offline, making it ideal for sensitive or regulated environments.
- 🧠 AI-Ready – Provides structured outputs in Markdown or JSON, optimized for LLMs.
- 🔧 Customizable – Rule sets can be adapted for domain-specific structures.
- 🧬 AI Add-on Compatible – Seamlessly upgrade to AI-powered modules when needed.

<br>

## 🚀 Upcoming 
Below are the planned features for an early September open-source release.
- Table AI Prototypes
- OCR AI Prototypes

<br>

<br>

## ▶️ Installation
To get started with OpenDataLoader-PDF, 
you need a Java 8+ runtime environment.


### Build instructions 
Build and install using Maven command:
```
mvn clean install
```

If the build is successful, the resulting `jar` file will be created in the path below.

```
opendataloader-pdf/target
```

<br>

## 💻 Getting Started
### CLI usage

```
java -jar ... [options] <INPUT FILE OR FOLDER>
```

This generates a JSON file with layout recognition results in the specified output folder. 
Additionally, annotated PDF with recognized structures and Markdown file are generated if options `--pdf` and `--markdown` are specified.

By default all line breaks and hyphenation characters are removed, the Markdown does not include any images and does not use any HTML.

The option `--keeplinebreaks` to preserve the original line breaks text content in JSON and Markdown output.

The option `--html`` enables use of HTML in Markdown, which may improve Markdown preview in processors that support HTML tags. 
The option `--addimagetomarkdown` enables inclusion of image references into the output Markdown. 
The images are extracted from PDF as individual files and stored in a subfolder next to the Markdown output.

### Available options:

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
```

### Java code integration

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

        try {
            //process pdf file
            DocumentProcessor.processFile("input.pdf",config);
        } catch (Exception exception) {
            //exception during processing
        }
    }
}
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


<br><br>

## 🤝 Contributing
We believe that great software is built together.<br>
Your contributions are vital to the success of this project.<br>
Please read [CONTRIBUTING.md](https://github.com/hancom-inc/opendataloader-pdf/blob/main/CONTRIBUTING.md) for details on how to contribute.

<br>

## 💖 Community & Support 
Have questions or need a little help? We're here for you!🤗
- [GitHub Discussions](https://github.com/hancom-inc/opendataloader-pdf/discussions): For Q&A and general chats. Let's talk! 🗣️
- [GitHub Issues](https://github.com/hancom-inc/opendataloader-pdf/issues): Found a bug? 🐛 Please report it here so we can fix it.


<br>

## ✨ Our Branding and Trademarks 
We love our brand and want to protect it! 
This project may contain trademarks, logos, or brand names for our products and services. 
To ensure everyone is on the same page, please remember these simple rules:

- **Authorized Use**: You're welcome to use our logos and trademarks, but you must follow our official brand guidelines.
- **No Confusion**: When you use our trademarks in a modified version of this project, it should never cause confusion or imply that Hancom officially sponsors or endorses your version.
- **Third-Party Brands**: Any use of trademarks or logos from other companies must follow that company’s specific policies.


<br>

## ⚖️ License

This project is licensed under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/).

For the full license text, see [LICENSE](LICENSE).

For information on third-party libraries and components, see:
- [THIRD_PARTY_LICENSES](./THIRD_PARTY/THIRD_PARTY_LICENSES.md)
- [THIRD_PARTY_NOTICES](./THIRD_PARTY/THIRD_PARTY_NOTICES.md)
- [licenses/](./THIRD_PARTY/licenses/)
