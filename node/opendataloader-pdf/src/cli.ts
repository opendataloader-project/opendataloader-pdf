#!/usr/bin/env node
import { run, RunOptions } from './index.js';

interface ParsedArgs {
  inputPath?: string;
  options: RunOptions;
  showHelp: boolean;
}

function printHelp(): void {
  console.log(`Usage: opendataloader-pdf [options] <input>`);
  console.log('');
  console.log('Options:');
  console.log('  -o, --output-dir <path>      Directory where outputs are written');
  console.log('  -p, --password <password>    Password for encrypted PDFs');
  console.log('  --replace-invalid-chars <c>  Replacement character for invalid characters');
  console.log('  --content-safety-off <mode>  Disable content safety filtering (provide mode)');
  console.log('  --markdown                   Generate Markdown output');
  console.log('  --html                       Generate HTML output');
  console.log('  --pdf                        Generate annotated PDF output');
  console.log('  --keep-line-breaks           Preserve line breaks in text output');
  console.log('  --markdown-with-html         Allow raw HTML within Markdown output');
  console.log('  --markdown-with-images       Embed images in Markdown output');
  console.log('  --no-json                    Disable JSON output generation');
  console.log('  --debug                      Stream CLI logs directly to stdout/stderr');
  console.log('  -h, --help                   Show this message and exit');
}

function parseArgs(argv: string[]): ParsedArgs {
  const options: RunOptions = {};
  let inputPath: string | undefined;
  let showHelp = false;

  const readValue = (currentIndex: number, option: string): { value: string; nextIndex: number } => {
    const nextValue = argv[currentIndex + 1];
    if (!nextValue || nextValue.startsWith('-')) {
      throw new Error(`Option ${option} requires a value.`);
    }
    return { value: nextValue, nextIndex: currentIndex + 1 };
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];

    switch (arg) {
      case '--help':
      case '-h':
        showHelp = true;
        i = argv.length; // exit loop
        break;
      case '--output-dir':
      case '-o': {
        const { value, nextIndex } = readValue(i, arg);
        options.outputFolder = value;
        i = nextIndex;
        break;
      }
      case '--password':
      case '-p': {
        const { value, nextIndex } = readValue(i, arg);
        options.password = value;
        i = nextIndex;
        break;
      }
      case '--replace-invalid-chars': {
        const { value, nextIndex } = readValue(i, arg);
        options.replaceInvalidChars = value;
        i = nextIndex;
        break;
      }
      case '--content-safety-off': {
        const { value, nextIndex } = readValue(i, arg);
        options.contentSafetyOff = value;
        i = nextIndex;
        break;
      }
      case '--markdown':
        options.generateMarkdown = true;
        break;
      case '--html':
        options.generateHtml = true;
        break;
      case '--pdf':
        options.generateAnnotatedPdf = true;
        break;
      case '--keep-line-breaks':
        options.keepLineBreaks = true;
        break;
      case '--markdown-with-html':
        options.htmlInMarkdown = true;
        break;
      case '--markdown-with-images':
        options.addImageToMarkdown = true;
        break;
      case '--no-json':
        options.noJson = true;
        break;
      case '--debug':
        options.debug = true;
        break;
      default:
        if (arg.startsWith('-')) {
          throw new Error(`Unknown option: ${arg}`);
        }

        if (inputPath) {
          throw new Error('Multiple input paths provided. Only one input path is allowed.');
        }
        inputPath = arg;
    }
  }

  return { inputPath, options, showHelp };
}

async function main(): Promise<number> {
  let parsed: ParsedArgs;

  try {
    parsed = parseArgs(process.argv.slice(2));
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(message);
    console.error("Use '--help' to see available options.");
    return 1;
  }

  if (parsed.showHelp) {
    printHelp();
    return 0;
  }

  if (!parsed.inputPath) {
    console.error('Missing required input path.');
    console.error("Use '--help' to see usage information.");
    return 1;
  }

  try {
    const output = await run(parsed.inputPath, parsed.options);
    if (output && !parsed.options.debug) {
      process.stdout.write(output);
    }
    if (output && !output.endsWith('\n') && !parsed.options.debug) {
      process.stdout.write('\n');
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
