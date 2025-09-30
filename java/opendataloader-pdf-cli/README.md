# opendataloader-pdf-cli

## CLI usage

```
java -jar ... [options] <INPUT FILE OR FOLDER>
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

The complete set of options:

```
Options:
-o,--output-dir <arg>           Specifies the output directory for generated files
-p,--password <arg>             Specifies the password for an encrypted PDF
-f,--format <arg>               List of output formats to generate (json, text, html, pdf, markdown, markdown-with-html, markdown-with-images). Default: json
-q,--quiet                      Suppresses console logging output
--content-safety-off <arg>      Disables one or more content safety filters. Accepts a list of filter names. Arguments: all, hidden-text, off-page, tiny, hidden-ocg
--keep-line-breaks              Preserves original line breaks in the extracted text
--replace-invalid-chars <arg>   Replaces invalid or unrecognized characters (e.g., �, \u0000) with the specified character
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

---

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

| Field             | Type   | Optional | Description       |
|-------------------|--------|----------|-------------------|
| font              | string | no       | Font name of text |
| font size         | double | no       | Font size of text |
| text color        | array  | no       | Color of text     |
| content           | string | no       | Text value        |

Specific fields of `table` json nodes

| Field             | Type     | Optional | Description                    |
|-------------------|----------|----------|--------------------------------|
| number of rows    | integer  | no       | Number of table rows           |
| number of columns | integer  | no       | Number of table columns        |
| rows              | array    | no       | Array of table rows            |
| previous table id | integer  | yes      | Id of previous connected table |
| next table id     | integer  | yes      | Id of next connected table     |

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

| Field  | Type   | Optional | Description                         |
|--------|--------|----------|-------------------------------------|
| kids   | array  | no       | Array of list item content elements |


Specific fields of `header` and `footer` json nodes

| Field  | Type   | Optional | Description                             |
|--------|--------|----------|-----------------------------------------|
| kids   | array  | no       | Array of header/footer content elements |

Specific fields of `text block` json nodes

| Field  | Type   | Optional | Description                          |
|--------|--------|----------|--------------------------------------|
| kids   | array  | no       | Array of text block content elements |
