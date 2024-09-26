# CLI usage

java -jar ... [options] <INPUT FILE OR FOLDER>

This will generate JSON file with layout recognition results in the specified output folder. Additionally, annotated PDF with recognized structures and Markdown file will be generated if options --pdf and --markdown are specified.

By default the Markdown will not include any images and will not use any HTML. All line breaks and hyphenation signed will be removed.

The --html option enables use of HTML in Markdown, with may improve Markdown preview in processors that support HTML tags in Markdown. The option --addimagetomarkdown enables inclusion of image references into the output Markdown. The images are extracted from PDF as individual files and stored in a subfolder next to the Markdown output. Finally, the options --keeplinebreaks causes Markdown output to preserve the original line breaks from the PDF.

Options:
-f,--folder <arg>          Specify output folder (default the folder of source PDF file)
-klb,--keeplinebreaks      Keep line breaks
-ht,--findhiddentext       Find hidden text
-html,--htmlinmarkdown     Use html in markdown
-im,--addimagetomarkdown   Add images to markdown
-markdown,--markdown       Generates markdown output
-p,--password <arg>        Specifies password
-pdf,--pdf                 Generates pdf output

# Java code usage

To integrate Layout recognition API into Java code, one can follow the sample code below.

```java
import com.duallab.layout.processors.Config;
import com.duallab.layout.processors.DocumentProcessor;

import java.io.IOException;

        //create default config
        Config config = new Config();
        
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

        //find hidden text
        config.setOutputFolder("output");
        
        try {
                //process pdf file
                DocumentProcessor.processFile("input.pdf", config);
        } catch (IOException exception) {
                //exception during processing       
        }
```

# Schema of the JSON output

Root json node

| Field             | Type    | Description                        |
|-------------------|---------|------------------------------------|
| file name         | string  | Name of processed pdf file         |
| number of pages   | integer | Number of pages in pdf file        |
| author            | string  | Author of pdf file                 |
| title             | string  | Title of pdf file                  |
| creation date     | string  | Creation date of pdf file          |
| modification date | string  | Modification date of pdf file      |
| kids              | array   | Array of detected content elements |

Common fields of content json nodes

| Field             | Type    | Description                                                                                                                                                                             |
|-------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id                | integer | Unique id of content element                                                                                                                                                            |
| type              | string  | Type of content element<br/>Possible types: `footer`, `header`, `heading`, `line`, `table`, `table row`, `table cell`, `paragraph`, `list`, `list item`, `image`, `line art`, `caption` |
| page number       | integer | Page number of content element                                                                                                                                                          |
| bounding box      | array   | Bounding box of content element                                                                                                                                                         |

Specific fields of text content json nodes (`list item`, `caption`, `heading`, `paragraph`)

| Field             | Type   | Description       |
|-------------------|--------|-------------------|
| font              | string | Font name of text |
| font size         | double | Font size of text |
| text color        | array  | Color of text     |
| content           | string | Text value        |

Specific fields of `table` json nodes

| Field              | Type     | Description             |
|--------------------|----------|-------------------------|
| number of rows     | integer  | Number of table rows    |
| number of columns  | integer  | Number of table columns |
| rows               | array    | Array of table rows     |

Specific fields of `table row` json nodes

| Field      | Type    | Description          |
|------------|---------|----------------------|
| row number | integer | Number of table row  |
| cells      | array   | Array of table cells |

Specific fields of `table cell` json nodes

| Field         | Type    | Description                          |
|---------------|---------|--------------------------------------|
| row number    | integer | Row number of table cell             |
| column number | integer | Column number of table cell          |
| row span      | integer | Row span of table cell               |
| column span   | integer | Column span of table cell            |
| kids          | array   | Array of table cell content elements |

Specific fields of `heading` json nodes

| Field         | Type    | Description              |
|---------------|---------|--------------------------|
| heading level | integer | Heading level of heading |

Specific fields of `list json` nodes

| Field                | Type    | Description                         |
|----------------------|---------|-------------------------------------|
| number of list items | integer | Number of list items                |
| numbering style      | string  | Numbering style of this list        |
| previous list id     | integer | Id of previous connected list       |
| next list id         | integer | Id of next connected list           |
| list items           | array   | Array of list item content elements |

Specific fields of `list item` json nodes

| Field  | Type   | Description                         |
|--------|--------|-------------------------------------|
| kids   | array  | Array of list item content elements |


Specific fields of `header` and `footer` json nodes

| Field  | Type   | Description                             |
|--------|--------|-----------------------------------------|
| kids   | array  | Array of header/footer content elements |