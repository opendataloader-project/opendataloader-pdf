// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run `npm run generate-options` to regenerate

import { Command } from 'commander';

/**
 * Register all CLI options on the given Commander program.
 */
export function registerCliOptions(program: Command): void {
  program.option('-o, --output-dir <value>', 'Directory where output files are written. Default: input file directory');
  program.option('-p, --password <value>', 'Password for encrypted PDF files');
  program.option('-f, --format <value>', 'Output formats (comma-separated). Values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images. Default: json');
  program.option('-q, --quiet', 'Suppress console logging output');
  program.option('--content-safety-off <value>', 'Disable content safety filters. Values: all, hidden-text, off-page, tiny, hidden-ocg');
  program.option('--keep-line-breaks', 'Preserve original line breaks in extracted text');
  program.option('--replace-invalid-chars <value>', 'Replacement character for invalid/unrecognized characters. Default: space');
  program.option('--use-struct-tree', 'Use PDF structure tree (tagged PDF) for reading order and semantic structure');
  program.option('--table-method <value>', 'Table detection method. Values: default (border-based), cluster (border + cluster). Default: default');
  program.option('--reading-order <value>', 'Reading order algorithm. Values: off, xycut. Default: xycut');
  program.option('--markdown-page-separator <value>', 'Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none');
  program.option('--text-page-separator <value>', 'Separator between pages in text output. Use %page-number% for page numbers. Default: none');
  program.option('--html-page-separator <value>', 'Separator between pages in HTML output. Use %page-number% for page numbers. Default: none');
  program.option('--image-output <value>', 'Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external');
  program.option('--image-format <value>', 'Output format for extracted images. Values: png, jpeg. Default: png');
  program.option('--pages <value>', 'Pages to extract (e.g., "1,3,5-7"). Default: all pages');
  program.option('--hybrid <value>', 'Hybrid backend for AI processing. Values: off (default), docling-fast');
  program.option('--hybrid-url <value>', 'Hybrid backend server URL (overrides default)');
  program.option('--hybrid-timeout <value>', 'Hybrid backend request timeout in milliseconds. Default: 30000');
  program.option('--hybrid-fallback', 'Fallback to Java processing on hybrid backend error. Default: true');
}
