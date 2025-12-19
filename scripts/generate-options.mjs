#!/usr/bin/env node
/**
 * Generates CLI option definitions for Node.js, Python, and documentation
 * from the single source of truth (options.json).
 *
 * Usage: node scripts/generate-options.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..');

// Read options.json
const optionsPath = join(ROOT_DIR, 'options.json');
const options = JSON.parse(readFileSync(optionsPath, 'utf-8'));

const AUTO_GENERATED_HEADER = `// AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
// Run \`npm run generate-options\` to regenerate
`;

const AUTO_GENERATED_HEADER_PYTHON = `# AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY
# Run \`npm run generate-options\` to regenerate
`;

const AUTO_GENERATED_HEADER_MDX = `{/* AUTO-GENERATED FROM options.json - DO NOT EDIT DIRECTLY */}
{/* Run \`npm run generate-options\` to regenerate */}

`;

/**
 * Convert kebab-case to camelCase
 */
function toCamelCase(str) {
  return str.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
}

/**
 * Convert kebab-case to snake_case
 */
function toSnakeCase(str) {
  return str.replace(/-/g, '_');
}

/**
 * Options that accept comma-separated list values.
 */
const LIST_OPTIONS = new Set(['format', 'content-safety-off']);

/**
 * Check if option supports list values.
 */
function isListOption(opt) {
  return LIST_OPTIONS.has(opt.name);
}

/**
 * Generate Node.js CLI options file
 */
function generateNodeCliOptions() {
  const lines = [AUTO_GENERATED_HEADER];
  lines.push(`import { Command } from 'commander';`);
  lines.push('');
  lines.push('/**');
  lines.push(' * Register all CLI options on the given Commander program.');
  lines.push(' */');
  lines.push('export function registerCliOptions(program: Command): void {');

  for (const opt of options.options) {
    const flags = opt.shortName
      ? `-${opt.shortName}, --${opt.name}${opt.type === 'string' ? ' <value>' : ''}`
      : `--${opt.name}${opt.type === 'string' ? ' <value>' : ''}`;

    const description = opt.description.replace(/'/g, "\\'");
    lines.push(`  program.option('${flags}', '${description}');`);
  }

  lines.push('}');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'node/opendataloader-pdf/src/cli-options.generated.ts');
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Node.js ConvertOptions interface and helper functions
 */
function generateNodeConvertOptions() {
  const lines = [AUTO_GENERATED_HEADER];

  // Generate ConvertOptions interface
  lines.push('/**');
  lines.push(' * Options for the convert function.');
  lines.push(' */');
  lines.push('export interface ConvertOptions {');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    let tsType = 'string';

    if (opt.type === 'boolean') {
      tsType = 'boolean';
    } else if (isListOption(opt)) {
      tsType = 'string | string[]';
    }

    lines.push(`  /** ${opt.description} */`);
    lines.push(`  ${camelName}?: ${tsType};`);
  }

  lines.push('}');
  lines.push('');

  // Generate CliOptions interface (for CLI parsing - all values are strings from commander)
  lines.push('/**');
  lines.push(' * Options as parsed from CLI (all values are strings from commander).');
  lines.push(' */');
  lines.push('export interface CliOptions {');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    const tsType = opt.type === 'boolean' ? 'boolean' : 'string';
    lines.push(`  ${camelName}?: ${tsType};`);
  }

  lines.push('}');
  lines.push('');

  // Generate buildConvertOptions function
  lines.push('/**');
  lines.push(' * Convert CLI options to ConvertOptions.');
  lines.push(' */');
  lines.push('export function buildConvertOptions(cliOptions: CliOptions): ConvertOptions {');
  lines.push('  const convertOptions: ConvertOptions = {};');
  lines.push('');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    if (opt.type === 'boolean') {
      lines.push(`  if (cliOptions.${camelName}) {`);
      lines.push(`    convertOptions.${camelName} = true;`);
      lines.push('  }');
    } else {
      lines.push(`  if (cliOptions.${camelName}) {`);
      lines.push(`    convertOptions.${camelName} = cliOptions.${camelName};`);
      lines.push('  }');
    }
  }

  lines.push('');
  lines.push('  return convertOptions;');
  lines.push('}');
  lines.push('');

  // Generate buildArgs function
  lines.push('/**');
  lines.push(' * Build CLI arguments array from ConvertOptions.');
  lines.push(' */');
  lines.push('export function buildArgs(options: ConvertOptions): string[] {');
  lines.push('  const args: string[] = [];');
  lines.push('');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    const cliFlag = `--${opt.name}`;

    if (opt.type === 'boolean') {
      lines.push(`  if (options.${camelName}) {`);
      lines.push(`    args.push('${cliFlag}');`);
      lines.push('  }');
    } else if (isListOption(opt)) {
      lines.push(`  if (options.${camelName}) {`);
      lines.push(`    if (Array.isArray(options.${camelName})) {`);
      lines.push(`      args.push('${cliFlag}', options.${camelName}.join(','));`);
      lines.push('    } else {');
      lines.push(`      args.push('${cliFlag}', options.${camelName});`);
      lines.push('    }');
      lines.push('  }');
    } else {
      lines.push(`  if (options.${camelName}) {`);
      lines.push(`    args.push('${cliFlag}', options.${camelName});`);
      lines.push('  }');
    }
  }

  lines.push('');
  lines.push('  return args;');
  lines.push('}');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'node/opendataloader-pdf/src/convert-options.generated.ts');
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python CLI options file (cli_options.py)
 */
function generatePythonCliOptions() {
  const lines = [AUTO_GENERATED_HEADER_PYTHON];
  lines.push('"""');
  lines.push('CLI option definitions for opendataloader-pdf.');
  lines.push('"""');
  lines.push('from typing import Any, Dict, List');
  lines.push('');
  lines.push('');
  lines.push('# Option metadata list');
  lines.push('CLI_OPTIONS: List[Dict[str, Any]] = [');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    const defaultValue = opt.default === null ? 'None'
      : typeof opt.default === 'boolean' ? (opt.default ? 'True' : 'False')
      : `"${opt.default}"`;

    lines.push('    {');
    lines.push(`        "name": "${opt.name}",`);
    lines.push(`        "python_name": "${snakeName}",`);
    lines.push(`        "short_name": ${opt.shortName ? `"${opt.shortName}"` : 'None'},`);
    lines.push(`        "type": "${opt.type}",`);
    lines.push(`        "required": ${opt.required ? 'True' : 'False'},`);
    lines.push(`        "default": ${defaultValue},`);
    lines.push(`        "description": "${opt.description.replace(/"/g, '\\"')}",`);
    lines.push('    },');
  }

  lines.push(']');
  lines.push('');
  lines.push('');
  lines.push('def add_options_to_parser(parser) -> None:');
  lines.push('    """Add all CLI options to an argparse.ArgumentParser."""');
  lines.push('    for opt in CLI_OPTIONS:');
  lines.push('        flags = []');
  lines.push('        if opt["short_name"]:');
  lines.push("            flags.append(f'-{opt[\"short_name\"]}')");
  lines.push("        flags.append(f'--{opt[\"name\"]}')");
  lines.push('');
  lines.push('        kwargs = {"help": opt["description"]}');
  lines.push('        if opt["type"] == "boolean":');
  lines.push('            kwargs["action"] = "store_true"');
  lines.push('        else:');
  lines.push('            kwargs["default"] = None');
  lines.push('');
  lines.push('        parser.add_argument(*flags, **kwargs)');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python convert function (convert.py)
 */
function generatePythonConvert() {
  const lines = [AUTO_GENERATED_HEADER_PYTHON];
  lines.push('"""');
  lines.push('Auto-generated convert function for opendataloader-pdf.');
  lines.push('"""');
  lines.push('from typing import List, Optional, Union');
  lines.push('');
  lines.push('from .runner import run_jar');
  lines.push('');
  lines.push('');

  // Generate function signature
  lines.push('def convert(');
  lines.push('    input_path: Union[str, List[str]],');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    let typeHint;
    let defaultVal;

    if (opt.type === 'boolean') {
      typeHint = 'bool';
      defaultVal = opt.default ? 'True' : 'False';
    } else if (isListOption(opt)) {
      typeHint = 'Optional[Union[str, List[str]]]';
      defaultVal = 'None';
    } else {
      typeHint = 'Optional[str]';
      defaultVal = 'None';
    }

    lines.push(`    ${snakeName}: ${typeHint} = ${defaultVal},`);
  }

  lines.push(') -> None:');
  lines.push('    """');
  lines.push('    Convert PDF(s) into the requested output format(s).');
  lines.push('');
  lines.push('    Args:');
  lines.push('        input_path: One or more input PDF file paths or directories');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    lines.push(`        ${snakeName}: ${opt.description}`);
  }

  lines.push('    """');

  // Generate function body
  lines.push('    args: List[str] = []');
  lines.push('');
  lines.push('    # Build input paths');
  lines.push('    if isinstance(input_path, list):');
  lines.push('        args.extend(input_path)');
  lines.push('    else:');
  lines.push('        args.append(input_path)');
  lines.push('');

  // Generate args building for each option
  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    const cliFlag = `--${opt.name}`;

    if (opt.type === 'boolean') {
      lines.push(`    if ${snakeName}:`);
      lines.push(`        args.append("${cliFlag}")`);
    } else if (isListOption(opt)) {
      lines.push(`    if ${snakeName}:`);
      lines.push(`        if isinstance(${snakeName}, list):`);
      lines.push(`            args.extend(["${cliFlag}", ",".join(${snakeName})])`);
      lines.push(`        else:`);
      lines.push(`            args.extend(["${cliFlag}", ${snakeName}])`);
    } else {
      lines.push(`    if ${snakeName}:`);
      lines.push(`        args.extend(["${cliFlag}", ${snakeName}])`);
    }
  }

  lines.push('');
  lines.push('    run_jar(args, quiet)');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Python convert() options table (MDX snippet)
 */
function generatePythonConvertOptionsMdx() {
  const lines = [];
  lines.push(AUTO_GENERATED_HEADER_MDX);
  lines.push('| Parameter | Type | Default | Description |');
  lines.push('|-----------|------|---------|-------------|');

  // Add input_path first (not in options.json)
  lines.push('| `input_path` | `str \\| list[str]` | required | One or more input PDF file paths or directories |');

  for (const opt of options.options) {
    const snakeName = toSnakeCase(opt.name);
    let pyType = 'str';
    if (opt.type === 'boolean') {
      pyType = 'bool';
    } else if (isListOption(opt)) {
      pyType = 'str \\| list[str]';
    }

    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? (opt.default ? '`True`' : '`False`')
      : `\`"${opt.default}"\``;

    const description = opt.description.replace(/\|/g, '\\|');
    lines.push(`| \`${snakeName}\` | \`${pyType}\` | ${defaultVal} | ${description} |`);
  }

  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/_generated/python-convert-options.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate Node.js convert() options table (MDX snippet)
 */
function generateNodeConvertOptionsMdx() {
  const lines = [];
  lines.push(AUTO_GENERATED_HEADER_MDX);
  lines.push('| Option | Type | Default | Description |');
  lines.push('|--------|------|---------|-------------|');

  for (const opt of options.options) {
    const camelName = toCamelCase(opt.name);
    let tsType = 'string';
    if (opt.type === 'boolean') {
      tsType = 'boolean';
    } else if (isListOption(opt)) {
      tsType = 'string \\| string[]';
    }

    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? `\`${opt.default}\``
      : `\`"${opt.default}"\``;

    const description = opt.description.replace(/\|/g, '\\|');
    lines.push(`| \`${camelName}\` | \`${tsType}\` | ${defaultVal} | ${description} |`);
  }

  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/_generated/node-convert-options.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

/**
 * Generate options reference documentation (MDX)
 */
function generateOptionsReferenceMdx() {
  const lines = [];
  lines.push('---');
  lines.push('title: CLI Options Reference');
  lines.push('description: Complete reference for all CLI options');
  lines.push('---');
  lines.push('');
  lines.push(AUTO_GENERATED_HEADER_MDX);
  lines.push('# CLI Options Reference');
  lines.push('');
  lines.push('This page documents all available CLI options for opendataloader-pdf.');
  lines.push('');
  lines.push('## Options');
  lines.push('');
  lines.push('| Option | Short | Type | Default | Description |');
  lines.push('|--------|-------|------|---------|-------------|');

  for (const opt of options.options) {
    const longOpt = `\`--${opt.name}\``;
    const shortOpt = opt.shortName ? `\`-${opt.shortName}\`` : '-';
    const type = `\`${opt.type}\``;
    const defaultVal = opt.default === null ? '-'
      : typeof opt.default === 'boolean' ? `\`${opt.default}\``
      : `\`"${opt.default}"\``;
    const description = opt.description.replace(/\|/g, '\\|');

    lines.push(`| ${longOpt} | ${shortOpt} | ${type} | ${defaultVal} | ${description} |`);
  }

  lines.push('');
  lines.push('## Examples');
  lines.push('');
  lines.push('### Basic conversion');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf document.pdf -o ./output -f json,markdown');
  lines.push('```');
  lines.push('');
  lines.push('### Convert entire folder');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf ./pdf-folder -o ./output -f json');
  lines.push('```');
  lines.push('');
  lines.push('### Markdown with embedded images');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf document.pdf -f markdown --embed-images');
  lines.push('```');
  lines.push('');
  lines.push('### Improve reading order with XY-Cut algorithm');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf document.pdf -f json --reading-order xycut');
  lines.push('```');
  lines.push('');
  lines.push('### Add page separators in output');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf document.pdf -f markdown --markdown-page-separator "--- Page %page-number% ---"');
  lines.push('```');
  lines.push('');
  lines.push('### Encrypted PDF');
  lines.push('');
  lines.push('```bash');
  lines.push('opendataloader-pdf encrypted.pdf -p mypassword -o ./output');
  lines.push('```');
  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/cli-options-reference.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

// Run all generators
console.log('Generating files from options.json...\n');

generateNodeCliOptions();
generateNodeConvertOptions();
generatePythonCliOptions();
generatePythonConvert();
generateOptionsReferenceMdx();
generatePythonConvertOptionsMdx();
generateNodeConvertOptionsMdx();

console.log('\nDone!');
