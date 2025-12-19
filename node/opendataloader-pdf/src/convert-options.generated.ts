// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run `npm run generate-options` to regenerate

/**
 * Options for the convert function.
 */
export interface ConvertOptions {
  /** Directory where output files are written. Default: input file directory */
  outputDir?: string;
  /** Password for encrypted PDF files */
  password?: string;
  /** Output formats (comma-separated). Values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images. Default: json */
  format?: string | string[];
  /** Suppress console logging output */
  quiet?: boolean;
  /** Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg */
  contentSafetyOff?: string | string[];
  /** Preserve original line breaks in extracted text */
  keepLineBreaks?: boolean;
  /** Replacement character for invalid/unrecognized characters. Default: space */
  replaceInvalidChars?: string;
  /** Use PDF structure tree (tagged PDF) for reading order and semantic structure */
  useStructTree?: boolean;
  /** Table detection method. Values: cluster */
  tableMethod?: string;
  /** Reading order algorithm. Values: none, xycut. Default: none */
  readingOrder?: string;
  /** Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none */
  markdownPageSeparator?: string;
  /** Separator between pages in text output. Use %page-number% for page numbers. Default: none */
  textPageSeparator?: string;
  /** Separator between pages in HTML output. Use %page-number% for page numbers. Default: none */
  htmlPageSeparator?: string;
  /** Embed images as Base64 data URIs instead of file path references */
  embedImages?: boolean;
  /** Output format for extracted images. Values: png, jpeg. Default: png */
  imageFormat?: string;
}

/**
 * Options as parsed from CLI (all values are strings from commander).
 */
export interface CliOptions {
  outputDir?: string;
  password?: string;
  format?: string;
  quiet?: boolean;
  contentSafetyOff?: string;
  keepLineBreaks?: boolean;
  replaceInvalidChars?: string;
  useStructTree?: boolean;
  tableMethod?: string;
  readingOrder?: string;
  markdownPageSeparator?: string;
  textPageSeparator?: string;
  htmlPageSeparator?: string;
  embedImages?: boolean;
  imageFormat?: string;
}

/**
 * Convert CLI options to ConvertOptions.
 */
export function buildConvertOptions(cliOptions: CliOptions): ConvertOptions {
  const convertOptions: ConvertOptions = {};

  if (cliOptions.outputDir) {
    convertOptions.outputDir = cliOptions.outputDir;
  }
  if (cliOptions.password) {
    convertOptions.password = cliOptions.password;
  }
  if (cliOptions.format) {
    convertOptions.format = cliOptions.format;
  }
  if (cliOptions.quiet) {
    convertOptions.quiet = true;
  }
  if (cliOptions.contentSafetyOff) {
    convertOptions.contentSafetyOff = cliOptions.contentSafetyOff;
  }
  if (cliOptions.keepLineBreaks) {
    convertOptions.keepLineBreaks = true;
  }
  if (cliOptions.replaceInvalidChars) {
    convertOptions.replaceInvalidChars = cliOptions.replaceInvalidChars;
  }
  if (cliOptions.useStructTree) {
    convertOptions.useStructTree = true;
  }
  if (cliOptions.tableMethod) {
    convertOptions.tableMethod = cliOptions.tableMethod;
  }
  if (cliOptions.readingOrder) {
    convertOptions.readingOrder = cliOptions.readingOrder;
  }
  if (cliOptions.markdownPageSeparator) {
    convertOptions.markdownPageSeparator = cliOptions.markdownPageSeparator;
  }
  if (cliOptions.textPageSeparator) {
    convertOptions.textPageSeparator = cliOptions.textPageSeparator;
  }
  if (cliOptions.htmlPageSeparator) {
    convertOptions.htmlPageSeparator = cliOptions.htmlPageSeparator;
  }
  if (cliOptions.embedImages) {
    convertOptions.embedImages = true;
  }
  if (cliOptions.imageFormat) {
    convertOptions.imageFormat = cliOptions.imageFormat;
  }

  return convertOptions;
}

/**
 * Build CLI arguments array from ConvertOptions.
 */
export function buildArgs(options: ConvertOptions): string[] {
  const args: string[] = [];

  if (options.outputDir) {
    args.push('--output-dir', options.outputDir);
  }
  if (options.password) {
    args.push('--password', options.password);
  }
  if (options.format) {
    if (Array.isArray(options.format)) {
      if (options.format.length > 0) {
        args.push('--format', options.format.join(','));
      }
    } else {
      args.push('--format', options.format);
    }
  }
  if (options.quiet) {
    args.push('--quiet');
  }
  if (options.contentSafetyOff) {
    if (Array.isArray(options.contentSafetyOff)) {
      if (options.contentSafetyOff.length > 0) {
        args.push('--content-safety-off', options.contentSafetyOff.join(','));
      }
    } else {
      args.push('--content-safety-off', options.contentSafetyOff);
    }
  }
  if (options.keepLineBreaks) {
    args.push('--keep-line-breaks');
  }
  if (options.replaceInvalidChars) {
    args.push('--replace-invalid-chars', options.replaceInvalidChars);
  }
  if (options.useStructTree) {
    args.push('--use-struct-tree');
  }
  if (options.tableMethod) {
    args.push('--table-method', options.tableMethod);
  }
  if (options.readingOrder) {
    args.push('--reading-order', options.readingOrder);
  }
  if (options.markdownPageSeparator) {
    args.push('--markdown-page-separator', options.markdownPageSeparator);
  }
  if (options.textPageSeparator) {
    args.push('--text-page-separator', options.textPageSeparator);
  }
  if (options.htmlPageSeparator) {
    args.push('--html-page-separator', options.htmlPageSeparator);
  }
  if (options.embedImages) {
    args.push('--embed-images');
  }
  if (options.imageFormat) {
    args.push('--image-format', options.imageFormat);
  }

  return args;
}
