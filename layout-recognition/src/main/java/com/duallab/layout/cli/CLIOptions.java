package com.duallab.layout.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class CLIOptions {
    
    public static final String HIDDEN_TEXT_OPTION = "ht";
    private static final String HIDDEN_TEXT_LONG_OPTION = "findhiddentext";

    public static final String PASSWORD_OPTION = "p";
    private static final String PASSWORD_LONG_OPTION = "password";

    public static final String HTML_OPTION = "html";
    private static final String HTML_LONG_OPTION = "htmlinmarkdown";

    public static final String KEEP_LINE_BREAKS_OPTION = "klb";
    private static final String KEEP_LINE_BREAKS_LONG_OPTION = "keeplinebreaks";

    public static final String PDF_REPORT_OPTION = "pdf";
    public static final String MARKDOWN_REPORT_OPTION = "markdown";

    public static final String MARKDOWN_IMAGE_OPTION = "im";
    private static final String MARKDOWN_IMAGE_LONG_OPTION = "addimagetomarkdown";

    public static final String FOLDER_OPTION = "f";
    private static final String FOLDER_LONG_OPTION = "folder";

    public static Options defineOptions() {
        Options options = new Options();
        Option findHiddenText = new Option(HIDDEN_TEXT_OPTION, HIDDEN_TEXT_LONG_OPTION, false, "Find hidden text");
        findHiddenText.setRequired(false);
        options.addOption(findHiddenText);
        Option password = new Option(PASSWORD_OPTION, PASSWORD_LONG_OPTION, true, "Specifies password");
        password.setRequired(false);
        options.addOption(password);
        Option html = new Option(HTML_OPTION, HTML_LONG_OPTION, false, "Use html in markdown");
        html.setRequired(false);
        options.addOption(html);
        Option keepLineBreaks = new Option(KEEP_LINE_BREAKS_OPTION, KEEP_LINE_BREAKS_LONG_OPTION, false, "keep line breaks");
        keepLineBreaks.setRequired(false);
        options.addOption(keepLineBreaks);
        Option pdfOutput = new Option(PDF_REPORT_OPTION, PDF_REPORT_OPTION, false, "Generates pdf output");
        pdfOutput.setRequired(false);
        options.addOption(pdfOutput);
        Option markdownOutput = new Option(MARKDOWN_REPORT_OPTION, MARKDOWN_REPORT_OPTION, false, "Generates markdown output");
        markdownOutput.setRequired(false);
        options.addOption(markdownOutput);
        Option imageSupport = new Option(MARKDOWN_IMAGE_OPTION, MARKDOWN_IMAGE_LONG_OPTION, false, "Add images to markdown");
        imageSupport.setRequired(false);
        options.addOption(imageSupport);
        Option folder = new Option(FOLDER_OPTION, FOLDER_LONG_OPTION, true, "Specify output folder (default the folder of source PDF file)");
        folder.setRequired(false);
        options.addOption(folder);
        return options;
    }
}
