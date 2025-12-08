#!/usr/bin/env node
import { Command, CommanderError } from 'commander';
import { convert, ConvertOptions } from './index.js';

interface CliOptions {
  outputDir?: string;
  password?: string;
  format?: string;
  quiet?: boolean;
  contentSafetyOff?: string;
  keepLineBreaks?: boolean;
  replaceInvalidChars?: string;
  useStructTree?: boolean;
  tableMethod?: string[];
  readingOrder?: string;
}

const VALID_TABLE_METHODS = new Set([
    'cluster',
]);

function createProgram(): Command {
  const program = new Command();

  program
    .name('opendataloader-pdf')
    .usage('[options] <input...>')
    .description('Convert PDFs using the OpenDataLoader CLI.')
    .showHelpAfterError("Use '--help' to see available options.")
    .showSuggestionAfterError(false)
    .argument('<input...>', 'Input files or directories to convert')
    .option('-o, --output-dir <path>', 'Directory where outputs are written')
    .option('-p, --password <password>', 'Password for encrypted PDFs')
    .option('-f, --format <format>', 'Comma-separated output format(s) to generate.')
    .option('-q, --quiet', 'Suppress CLI logging output')
    .option('--content-safety-off <modes>', 'Comma-separated content safety filters to disable.')
    .option('--keep-line-breaks', 'Preserve line breaks in text output')
    .option('--replace-invalid-chars <c>', 'Replacement character for invalid characters')
    .option('--use-struct-tree', 'Enable processing structure tree (disabled by default)')
    .option('--reading-order <readingOrder>', 'Specifies reading order of content. Supported values: bbox')
    .option('--table-method <method...>', 'Enable specified table detection method');

  program.configureOutput({
    writeErr: (str) => {
      console.error(str.trimEnd());
    },
    outputError: (str, write) => {
      write(str);
    },
  });

  return program;
}

function buildConvertOptions(options: CliOptions): ConvertOptions {
  const convertOptions: ConvertOptions = {};

  if (options.outputDir) {
    convertOptions.outputDir = options.outputDir;
  }
  if (options.password) {
    convertOptions.password = options.password;
  }
  if (options.format) {
    convertOptions.format = options.format;
  }
  if (options.quiet) {
    convertOptions.quiet = true;
  }
  if (options.contentSafetyOff) {
    convertOptions.contentSafetyOff = options.contentSafetyOff;
  }
  if (options.keepLineBreaks) {
    convertOptions.keepLineBreaks = true;
  }
  if (options.replaceInvalidChars) {
    convertOptions.replaceInvalidChars = options.replaceInvalidChars;
  }
  if (options.useStructTree) {
    convertOptions.useStructTree = true;
  }
  if (options.tableMethod && options.tableMethod.length) {
    convertOptions.tableMethod = options.tableMethod;
  }
  if (options.readingOrder) {
      convertOptions.readingOrder = options.readingOrder;
  }

  return convertOptions;
}

async function main(): Promise<number> {
  const program = createProgram();

  program.exitOverride();

  try {
    program.parse(process.argv);
  } catch (err) {
    if (err instanceof CommanderError) {
      if (err.code === 'commander.helpDisplayed') {
        return 0;
      }
      return err.exitCode ?? 1;
    }

    const message = err instanceof Error ? err.message : String(err);
    console.error(message);
    console.error("Use '--help' to see available options.");
    return 1;
  }

  const cliOptions = program.opts<CliOptions>();
  const inputPaths = program.args;

  if (cliOptions.format) {
    for (const value of cliOptions.format) {
      if (!VALID_FORMATS.has(value)) {
        console.error(`Invalid format '${value}'. See '--help' for allowed values.`);
        console.error("Use '--help' to see available options.");
        return 1;
      }
    }
  }

  if (cliOptions.contentSafetyOff) {
    for (const value of cliOptions.contentSafetyOff) {
      if (!VALID_CONTENT_SAFETY_MODES.has(value)) {
        console.error(`Invalid content safety mode '${value}'. See '--help' for allowed values.`);
        console.error("Use '--help' to see available options.");
        return 1;
      }
    }
  }
  if (cliOptions.tableMethod) {
    for (const value of cliOptions.tableMethod) {
      if (!VALID_TABLE_METHODS.has(value)) {
        console.error(`Invalid table method '${value}'. See '--help' for allowed values.`);
        console.error("Use '--help' to see available options.");
        return 1;
      }
    }
  }

  const convertOptions = buildConvertOptions(cliOptions);

  try {
    const output = await convert(inputPaths, convertOptions);
    if (output && !convertOptions.quiet) {
      process.stdout.write(output);
      if (!output.endsWith('\n')) {
        process.stdout.write('\n');
      }
    }
    return 0;
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(message);
    return 1;
  }
}

main().then((code) => {
  if (code !== 0) {
    process.exit(code);
  }
});
