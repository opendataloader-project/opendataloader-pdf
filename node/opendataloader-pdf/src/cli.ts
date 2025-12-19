#!/usr/bin/env node
import { Command, CommanderError } from 'commander';
import { convert } from './index.js';
import { CliOptions, buildConvertOptions } from './convert-options.generated.js';
import { registerCliOptions } from './cli-options.generated.js';

function createProgram(): Command {
  const program = new Command();

  program
    .name('opendataloader-pdf')
    .usage('[options] <input...>')
    .description('Convert PDFs using the OpenDataLoader CLI.')
    .showHelpAfterError("Use '--help' to see available options.")
    .showSuggestionAfterError(false)
    .argument('<input...>', 'Input files or directories to convert');

  // Register CLI options from auto-generated file
  registerCliOptions(program);

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
