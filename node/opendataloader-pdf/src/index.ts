import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import { StringDecoder } from 'string_decoder';
import { fileURLToPath } from 'url';

// Re-export types and utilities from auto-generated file
export type { ConvertOptions } from './convert-options.generated.js';
export { buildArgs } from './convert-options.generated.js';
import type { ConvertOptions } from './convert-options.generated.js';
import { buildArgs } from './convert-options.generated.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JAR_NAME = 'opendataloader-pdf-cli.jar';

interface JarExecutionOptions {
  /**
   * When true, forwards Java's stdout and stderr chunks to the parent
   * process in real time as well as accumulating them. Used by the bundled
   * CLI so long-running conversions show progress as it happens.
   */
  streamOutput?: boolean;
}

function executeJar(args: string[], executionOptions: JarExecutionOptions = {}): Promise<string> {
  const { streamOutput = false } = executionOptions;

  return new Promise((resolve, reject) => {
    const jarPath = path.join(__dirname, '..', 'lib', JAR_NAME);

    if (!fs.existsSync(jarPath)) {
      return reject(
        new Error(`JAR file not found at ${jarPath}. Please run the build script first.`),
      );
    }

    const command = 'java';
    // Force headless AWT so macOS doesn't surface a Dock icon (and steal focus)
    // every time the JVM touches ImageIO/PDFBox rendering. Safe on all OSes —
    // the CLI never opens a UI window, only manipulates BufferedImages.
    const commandArgs = [
      '-Djava.awt.headless=true',
      '-Dapple.awt.UIElement=true',
      '-jar',
      jarPath,
      ...args,
    ];

    const javaProcess = spawn(command, commandArgs);

    let stdout = '';
    let stderr = '';
    // StringDecoder buffers incomplete multi-byte UTF-8 sequences across
    // chunk boundaries — Buffer.toString() alone would emit replacement
    // characters when, e.g., a 3-byte Korean codepoint splits across two
    // 'data' events. One decoder per stream so they don't share state.
    const stdoutDecoder = new StringDecoder('utf8');
    const stderrDecoder = new StringDecoder('utf8');

    javaProcess.stdout.on('data', (data: Buffer) => {
      const chunk = stdoutDecoder.write(data);
      if (chunk.length === 0) return;
      if (streamOutput) {
        // Stream-only: don't also accumulate, or a multi-hour conversion
        // would buffer its entire (potentially gigabyte) stdout in memory
        // for no consumer.
        process.stdout.write(chunk);
      } else {
        stdout += chunk;
      }
    });

    javaProcess.stderr.on('data', (data: Buffer) => {
      const chunk = stderrDecoder.write(data);
      if (chunk.length === 0) return;
      if (streamOutput) {
        process.stderr.write(chunk);
      }
      // stderr is always accumulated (progress logs are small and we need
      // them for error messages on non-zero exit).
      stderr += chunk;
    });

    javaProcess.on('close', (code) => {
      // Flush any trailing bytes the decoder is still holding (always emit
      // them — if we drop them on error paths, error messages with non-ASCII
      // characters lose their tail).
      const stdoutTail = stdoutDecoder.end();
      const stderrTail = stderrDecoder.end();
      if (stdoutTail.length > 0) {
        if (streamOutput) {
          process.stdout.write(stdoutTail);
        } else {
          stdout += stdoutTail;
        }
      }
      if (stderrTail.length > 0) {
        if (streamOutput) process.stderr.write(stderrTail);
        stderr += stderrTail;
      }

      if (code === 0) {
        resolve(stdout);
      } else {
        const errorOutput = stderr || stdout;
        const error = new Error(
          `The opendataloader-pdf CLI exited with code ${code}.\n\n${errorOutput}`,
        );
        // Tag so the CLI can suppress re-printing this message — Java's
        // stderr was already streamed live to the parent in CLI mode, and
        // re-printing risks leaking anything sensitive Java logged
        // (e.g. a --password value echoed by an underlying library).
        (error as Error & { isJavaExit?: boolean }).isJavaExit = true;
        reject(error);
      }
    });

    javaProcess.on('error', (err: Error) => {
      if (err.message.includes('ENOENT')) {
        reject(
          new Error(
            "'java' command not found. Please ensure Java is installed and in your system's PATH.",
          ),
        );
      } else {
        reject(err);
      }
    });
  });
}

function buildJarArgs(
  inputPaths: string | string[],
  options: ConvertOptions,
): string[] | Error {
  const inputList = Array.isArray(inputPaths) ? inputPaths : [inputPaths];
  if (inputList.length === 0) {
    return new Error('At least one input path must be provided.');
  }

  for (const input of inputList) {
    if (!fs.existsSync(input)) {
      return new Error(`Input file or folder not found: ${input}`);
    }
  }

  return [...inputList, ...buildArgs(options)];
}

export function convert(
  inputPaths: string | string[],
  options: ConvertOptions = {},
): Promise<string> {
  const argsOrError = buildJarArgs(inputPaths, options);
  if (argsOrError instanceof Error) {
    return Promise.reject(argsOrError);
  }
  // Library API: never streams to the parent process. Returns the full stdout
  // string so callers can do `const out = await convert(...)` without surprise
  // side-effects on process.stdout / process.stderr.
  return executeJar(argsOrError, { streamOutput: false });
}

/**
 * Internal entry point used by the bundled CLI. Streams Java's stdout and
 * stderr to the parent process in real time (so long-running conversions like
 * hybrid mode show progress as it happens) and resolves without a stdout
 * payload — preventing the caller from re-printing what was already streamed.
 *
 * Not part of the public API: do not import this from application code. Use
 * {@link convert} instead.
 *
 * @internal
 */
export async function _runForCli(
  inputPaths: string | string[],
  options: ConvertOptions = {},
): Promise<void> {
  const argsOrError = buildJarArgs(inputPaths, options);
  if (argsOrError instanceof Error) {
    throw argsOrError;
  }
  await executeJar(argsOrError, { streamOutput: true });
}

/**
 * @deprecated Use `convert()` and `ConvertOptions` instead. This function will be removed in a future version.
 */
export interface RunOptions {
  outputFolder?: string;
  password?: string;
  replaceInvalidChars?: string;
  generateMarkdown?: boolean;
  generateHtml?: boolean;
  generateAnnotatedPdf?: boolean;
  keepLineBreaks?: boolean;
  contentSafetyOff?: string;
  htmlInMarkdown?: boolean;
  addImageToMarkdown?: boolean;
  noJson?: boolean;
  debug?: boolean;
  useStructTree?: boolean;
}

/**
 * @deprecated Use `convert()` instead. This function will be removed in a future version.
 */
export function run(inputPath: string, options: RunOptions = {}): Promise<string> {
  console.warn(
    'Warning: run() is deprecated and will be removed in a future version. Use convert() instead.',
  );

  // Build format array based on legacy boolean options
  const formats: string[] = [];
  if (!options.noJson) {
    formats.push('json');
  }
  if (options.generateMarkdown) {
    if (options.addImageToMarkdown) {
      formats.push('markdown-with-images');
    } else if (options.htmlInMarkdown) {
      formats.push('markdown-with-html');
    } else {
      formats.push('markdown');
    }
  }
  if (options.generateHtml) {
    formats.push('html');
  }
  if (options.generateAnnotatedPdf) {
    formats.push('pdf');
  }

  return convert(inputPath, {
    outputDir: options.outputFolder,
    password: options.password,
    replaceInvalidChars: options.replaceInvalidChars,
    keepLineBreaks: options.keepLineBreaks,
    contentSafetyOff: options.contentSafetyOff,
    useStructTree: options.useStructTree,
    format: formats.length > 0 ? formats : undefined,
    quiet: !options.debug,
  });
}
