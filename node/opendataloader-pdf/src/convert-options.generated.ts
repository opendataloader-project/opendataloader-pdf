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
  /** Output formats (comma-separated). Values: json, text, html, pdf, markdown, tagged-pdf. Default: json. For HTML inside Markdown use --markdown-with-html. For image extraction control use --image-output. */
  format?: string | string[];
  /** Suppress console logging output */
  quiet?: boolean;
  /** Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg, background */
  contentSafetyOff?: string | string[];
  /** Filter hidden (low-contrast) text via per-page rendering. Values: on, off. Default: off (opt-in; expensive, runs as sequential post-processing) */
  filterHiddenText?: string;
  /** Enable sensitive data sanitization. Replaces emails, phone numbers, IPs, credit cards, and URLs with placeholders */
  sanitize?: boolean;
  /** Preserve original line breaks in extracted text */
  keepLineBreaks?: boolean;
  /** Replacement character for invalid/unrecognized characters. Default: space */
  replaceInvalidChars?: string;
  /** Use PDF structure tree (tagged PDF) for reading order and semantic structure. Output quality depends on tag quality. Takes precedence over --hybrid: when both are set on a tagged PDF, the structure tree is used and the hybrid backend is not called */
  useStructTree?: boolean;
  /** Table detection method. Values: default (border-based), cluster (border + cluster). Default: default */
  tableMethod?: string;
  /** Reading order algorithm. Values: off, xycut. Default: xycut */
  readingOrder?: string;
  /** Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none */
  markdownPageSeparator?: string;
  /** Allow HTML tags inside Markdown output for complex structures such as multi-row-span tables. Implies --format markdown. */
  markdownWithHtml?: boolean;
  /** Separator between pages in text output. Use %page-number% for page numbers. Default: none */
  textPageSeparator?: string;
  /** Separator between pages in HTML output. Use %page-number% for page numbers. Default: none */
  htmlPageSeparator?: string;
  /** Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external */
  imageOutput?: string;
  /** Output format for extracted images. Values: png, jpeg. Default: png */
  imageFormat?: string;
  /** Directory for extracted images (applies only with --image-output external) */
  imageDir?: string;
  /** Pages to extract (e.g., "1,3,5-7"). Default: all pages */
  pages?: string;
  /** Include page headers and footers in output */
  includeHeaderFooter?: boolean;
  /** Detect strikethrough text and wrap with ~~ in Markdown output or <del></del> tag in HTML output (experimental) */
  detectStrikethrough?: boolean;
  /** Hybrid backend (requires a running server). Quick start: pip install "opendataloader-pdf[hybrid]" && opendataloader-pdf-hybrid --port 5002. For remote servers use --hybrid-url. Values: off (default), docling-fast. Ignored when --use-struct-tree is set on a tagged PDF (structure tree takes precedence) */
  hybrid?: string;
  /** Hybrid triage mode. Values: auto (default, dynamic triage), full (skip triage, all pages to backend) */
  hybridMode?: string;
  /** Hybrid backend server URL (overrides default) */
  hybridUrl?: string;
  /** Hybrid backend request timeout in milliseconds (0 = use the backend's own default). Default: 0 */
  hybridTimeout?: string;
  /** Opt in to Java fallback on hybrid backend error (default: disabled) */
  hybridFallback?: boolean;
  /** Write output to stdout instead of file (single format only) */
  toStdout?: boolean;
  /** Number of worker threads for per-page processing. Default: 1 (sequential, stable). Values >1 (experimental) run pages in parallel for faster throughput; output may vary slightly on some PDFs. Capped at the number of available CPU cores. Applies to the native Java pipeline only; ignored in --hybrid mode */
  threads?: string;
  /** Set the rendering resolution for images in DPI. Higher values improve image quality but increase memory consumption; lower values reduce memory usage at the cost of detail. Accepts positive decimal DPI values (e.g., 144.0). Default: 144.0. */
  imageResolution?: string;
  /** Set the ratio used to calculate the automatic space-insertion threshold (threshold = space-ratio * font size). If the horizontal gap between two adjacent symbols exceeds this threshold, an extra space is inserted to text value. Accepts decimals (e.g., 0.17). Default: 0.17 */
  spaceRatio?: string;
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
  filterHiddenText?: string;
  sanitize?: boolean;
  keepLineBreaks?: boolean;
  replaceInvalidChars?: string;
  useStructTree?: boolean;
  tableMethod?: string;
  readingOrder?: string;
  markdownPageSeparator?: string;
  markdownWithHtml?: boolean;
  textPageSeparator?: string;
  htmlPageSeparator?: string;
  imageOutput?: string;
  imageFormat?: string;
  imageDir?: string;
  pages?: string;
  includeHeaderFooter?: boolean;
  detectStrikethrough?: boolean;
  hybrid?: string;
  hybridMode?: string;
  hybridUrl?: string;
  hybridTimeout?: string;
  hybridFallback?: boolean;
  toStdout?: boolean;
  threads?: string;
  imageResolution?: string;
  spaceRatio?: string;
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
  if (cliOptions.filterHiddenText) {
    convertOptions.filterHiddenText = cliOptions.filterHiddenText;
  }
  if (cliOptions.sanitize) {
    convertOptions.sanitize = true;
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
  if (cliOptions.markdownWithHtml) {
    convertOptions.markdownWithHtml = true;
  }
  if (cliOptions.textPageSeparator) {
    convertOptions.textPageSeparator = cliOptions.textPageSeparator;
  }
  if (cliOptions.htmlPageSeparator) {
    convertOptions.htmlPageSeparator = cliOptions.htmlPageSeparator;
  }
  if (cliOptions.imageOutput) {
    convertOptions.imageOutput = cliOptions.imageOutput;
  }
  if (cliOptions.imageFormat) {
    convertOptions.imageFormat = cliOptions.imageFormat;
  }
  if (cliOptions.imageDir) {
    convertOptions.imageDir = cliOptions.imageDir;
  }
  if (cliOptions.pages) {
    convertOptions.pages = cliOptions.pages;
  }
  if (cliOptions.includeHeaderFooter) {
    convertOptions.includeHeaderFooter = true;
  }
  if (cliOptions.detectStrikethrough) {
    convertOptions.detectStrikethrough = true;
  }
  if (cliOptions.hybrid) {
    convertOptions.hybrid = cliOptions.hybrid;
  }
  if (cliOptions.hybridMode) {
    convertOptions.hybridMode = cliOptions.hybridMode;
  }
  if (cliOptions.hybridUrl) {
    convertOptions.hybridUrl = cliOptions.hybridUrl;
  }
  if (cliOptions.hybridTimeout) {
    convertOptions.hybridTimeout = cliOptions.hybridTimeout;
  }
  if (cliOptions.hybridFallback) {
    convertOptions.hybridFallback = true;
  }
  if (cliOptions.toStdout) {
    convertOptions.toStdout = true;
  }
  if (cliOptions.threads) {
    convertOptions.threads = cliOptions.threads;
  }
  if (cliOptions.imageResolution) {
    convertOptions.imageResolution = cliOptions.imageResolution;
  }
  if (cliOptions.spaceRatio) {
    convertOptions.spaceRatio = cliOptions.spaceRatio;
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
  if (options.filterHiddenText) {
    args.push('--filter-hidden-text', options.filterHiddenText);
  }
  if (options.sanitize) {
    args.push('--sanitize');
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
  if (options.markdownWithHtml) {
    args.push('--markdown-with-html');
  }
  if (options.textPageSeparator) {
    args.push('--text-page-separator', options.textPageSeparator);
  }
  if (options.htmlPageSeparator) {
    args.push('--html-page-separator', options.htmlPageSeparator);
  }
  if (options.imageOutput) {
    args.push('--image-output', options.imageOutput);
  }
  if (options.imageFormat) {
    args.push('--image-format', options.imageFormat);
  }
  if (options.imageDir) {
    args.push('--image-dir', options.imageDir);
  }
  if (options.pages) {
    args.push('--pages', options.pages);
  }
  if (options.includeHeaderFooter) {
    args.push('--include-header-footer');
  }
  if (options.detectStrikethrough) {
    args.push('--detect-strikethrough');
  }
  if (options.hybrid) {
    args.push('--hybrid', options.hybrid);
  }
  if (options.hybridMode) {
    args.push('--hybrid-mode', options.hybridMode);
  }
  if (options.hybridUrl) {
    args.push('--hybrid-url', options.hybridUrl);
  }
  if (options.hybridTimeout) {
    args.push('--hybrid-timeout', options.hybridTimeout);
  }
  if (options.hybridFallback) {
    args.push('--hybrid-fallback');
  }
  if (options.toStdout) {
    args.push('--to-stdout');
  }
  if (options.threads) {
    args.push('--threads', options.threads);
  }
  if (options.imageResolution) {
    args.push('--image-resolution', options.imageResolution);
  }
  if (options.spaceRatio) {
    args.push('--space-ratio', options.spaceRatio);
  }

  return args;
}
