// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run `npm run generate-options` to regenerate

import { Command } from 'commander';

/**
 * Register all CLI options on the given Commander program.
 */
export function registerCliOptions(program: Command): void {
  program.option('-o, --output-dir <value>', 'Directory where output files are written. Default: input file directory');
  program.option('-p, --password <value>', 'Password for encrypted PDF files');
  program.option('-f, --format <value>', 'Output formats (comma-separated). Values: json, text, html, pdf, markdown, tagged-pdf. Default: json. For HTML inside Markdown use --markdown-with-html. For image extraction control use --image-output.');
  program.option('-q, --quiet', 'Suppress console logging output');
  program.option('--content-safety-off <value>', 'Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg');
  program.option('--sanitize', 'Enable sensitive data sanitization. Replaces emails, phone numbers, IPs, credit cards, and URLs with placeholders');
  program.option('--keep-line-breaks', 'Preserve original line breaks in extracted text');
  program.option('--replace-invalid-chars <value>', 'Replacement character for invalid/unrecognized characters. Default: space');
  program.option('--use-struct-tree', 'Use PDF structure tree (tagged PDF) for reading order and semantic structure');
  program.option('--table-method <value>', 'Table detection method. Values: default (border-based), cluster (border + cluster). Default: default');
  program.option('--reading-order <value>', 'Reading order algorithm. Values: off, xycut. Default: xycut');
  program.option('--markdown-page-separator <value>', 'Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none');
  program.option('--markdown-with-html', 'Allow HTML tags inside Markdown output for complex structures such as multi-row-span tables. Implies --format markdown.');
  program.option('--text-page-separator <value>', 'Separator between pages in text output. Use %page-number% for page numbers. Default: none');
  program.option('--html-page-separator <value>', 'Separator between pages in HTML output. Use %page-number% for page numbers. Default: none');
  program.option('--image-output <value>', 'Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external');
  program.option('--image-format <value>', 'Output format for extracted images. Values: png, jpeg. Default: png');
  program.option('--image-dir <value>', 'Directory for extracted images');
  program.option('--pages <value>', 'Pages to extract (e.g., "1,3,5-7"). Default: all pages');
  program.option('--include-header-footer', 'Include page headers and footers in output');
  program.option('--detect-strikethrough', 'Detect strikethrough text and wrap with ~~ in Markdown output or <del></del> tag in HTML output (experimental)');
  program.option('--hybrid <value>', 'Hybrid backend (requires a running server). Quick start: pip install "opendataloader-pdf[hybrid]" && opendataloader-pdf-hybrid --port 5002. For remote servers use --hybrid-url. Values: off (default), docling-fast, hancom-ai');
  program.option('--hybrid-mode <value>', 'Hybrid triage mode. Values: auto (default, dynamic triage), full (skip triage, all pages to backend)');
  program.option('--hybrid-url <value>', 'Hybrid backend server URL (overrides default)');
  program.option('--hybrid-timeout <value>', 'Hybrid backend request timeout in milliseconds (0 = no timeout). Default: 0');
  program.option('--hybrid-fallback', 'Opt in to Java fallback on hybrid backend error (default: disabled)');
  program.option('--hybrid-hancom-ai-regionlist-strategy <value>', 'DLA label 7 (regionlist) handling. Requires --hybrid=hancom-ai. Values: table-first (default; check TSR overlap), list-only (skip TSR, always treat as list)');
  program.option('--hybrid-hancom-ai-ocr-strategy <value>', 'OCR strategy. Requires --hybrid=hancom-ai. Values: off (stream-only), auto (default; stream first, OCR fallback), force (OCR-only)');
  program.option('--hybrid-hancom-ai-image-cache <value>', 'Page image cache backing. Requires --hybrid=hancom-ai. Values: memory (default), disk');
  program.option('--to-stdout', 'Write output to stdout instead of file (single format only)');
  program.option('--threads <value>', 'Number of worker threads for per-page processing. Default: 1 (sequential, stable). Values >1 (experimental) run pages in parallel for faster throughput; output may vary slightly on some PDFs. Capped at the number of available CPU cores. Applies to the native Java pipeline only; ignored in --hybrid mode');
}
