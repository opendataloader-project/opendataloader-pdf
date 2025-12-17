import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JAR_NAME = 'opendataloader-pdf-cli.jar';

interface JarExecutionOptions {
  debug?: boolean;
  streamOutput?: boolean;
}

function executeJar(args: string[], executionOptions: JarExecutionOptions = {}): Promise<string> {
  const { debug = false, streamOutput = false } = executionOptions;

  return new Promise((resolve, reject) => {
    const jarPath = path.join(__dirname, '..', 'lib', JAR_NAME);

    if (!fs.existsSync(jarPath)) {
      return reject(
        new Error(`JAR file not found at ${jarPath}. Please run the build script first.`),
      );
    }

    const command = 'java';
    const commandArgs = ['-jar', jarPath, ...args];

    const javaProcess = spawn(command, commandArgs);

    let stdout = '';
    let stderr = '';

    javaProcess.stdout.on('data', (data) => {
      const chunk = data.toString();
      if (streamOutput) {
        process.stdout.write(chunk);
      }
      stdout += chunk;
    });

    javaProcess.stderr.on('data', (data) => {
      const chunk = data.toString();
      if (streamOutput) {
        process.stderr.write(chunk);
      }
      stderr += chunk;
    });

    javaProcess.on('close', (code) => {
      if (code === 0) {
        resolve(stdout);
      } else {
        const errorOutput = stderr || stdout;
        const error = new Error(
          `The opendataloader-pdf CLI exited with code ${code}.\n\n${errorOutput}`,
        );
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

export interface ConvertOptions {
  outputDir?: string;
  password?: string;
  format?: string | string[];
  quiet?: boolean;
  contentSafetyOff?: string | string[];
  keepLineBreaks?: boolean;
  replaceInvalidChars?: string;
  useStructTree?: boolean;
  tableMethod?: string | string[];
  readingOrder?: string;
  markdownPageSeparator?: string;
  textPageSeparator?: string;
  htmlPageSeparator?: string;
}

export function convert(
  inputPaths: string | string[],
  options: ConvertOptions = {},
): Promise<string> {
  const inputList = Array.isArray(inputPaths) ? inputPaths : [inputPaths];
  if (inputList.length === 0) {
    return Promise.reject(new Error('At least one input path must be provided.'));
  }

  for (const input of inputList) {
    if (!fs.existsSync(input)) {
      return Promise.reject(new Error(`Input file or folder not found: ${input}`));
    }
  }

  const args: string[] = [...inputList];
  if (options.outputDir) {
    args.push('--output-dir', options.outputDir);
  }
  if (options.password) {
    args.push('--password', options.password);
  }
  if (options.format) {
    if (Array.isArray(options.format)) {
      args.push('--format', options.format.join(','));
    } else {
      args.push('--format', options.format);
    }
  }
  if (options.quiet) {
    args.push('--quiet');
  }
  if (options.contentSafetyOff) {
    if (Array.isArray(options.contentSafetyOff)) {
      args.push('--content-safety-off', options.contentSafetyOff.join(','));
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
    if (Array.isArray(options.tableMethod)) {
      args.push('--table-method', options.tableMethod.join(','));
    } else {
      args.push('--table-method', options.tableMethod);
    }
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

  return executeJar(args, {
    streamOutput: !options.quiet,
  });
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
