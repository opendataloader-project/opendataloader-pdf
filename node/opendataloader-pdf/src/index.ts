import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const JAR_NAME = 'opendataloader-pdf-cli.jar';

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
  debug?: boolean;
}

export function run(inputPath: string, options: RunOptions = {}): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(inputPath)) {
      return reject(new Error(`Input file or folder not found: ${inputPath}`));
    }

    const args: string[] = [];
    if (options.outputFolder) {
      args.push('--output-dir', options.outputFolder);
    }
    if (options.password) {
      args.push('--password', options.password);
    }
    if (options.replaceInvalidChars) {
      args.push('--replace-invalid-chars', options.replaceInvalidChars);
    }
    if (options.generateMarkdown) {
      args.push('--markdown');
    }
    if (options.generateHtml) {
      args.push('--html');
    }
    if (options.generateAnnotatedPdf) {
      args.push('--pdf');
    }
    if (options.keepLineBreaks) {
      args.push('--keep-line-breaks');
    }
    if (options.contentSafetyOff) {
      args.push('--content-safety-off', options.contentSafetyOff);
    }
    if (options.htmlInMarkdown) {
      args.push('--markdown-with-html');
    }
    if (options.addImageToMarkdown) {
      args.push('--markdown-with-images');
    }

    args.push(inputPath);

    const jarPath = path.join(__dirname, '..', 'lib', JAR_NAME);

    if (!fs.existsSync(jarPath)) {
      return reject(
        new Error(`JAR file not found at ${jarPath}. Please run the build script first.`),
      );
    }

    const command = 'java';
    const commandArgs = ['-jar', jarPath, ...args];

    if (options.debug) {
      console.error(`Running command: ${command} ${commandArgs.join(' ')}`);
    }

    const javaProcess = spawn(command, commandArgs);

    let stdout = '';
    let stderr = '';

    javaProcess.stdout.on('data', (data) => {
      const chunk = data.toString();
      if (options.debug) {
        process.stdout.write(chunk);
      }
      stdout += chunk;
    });

    javaProcess.stderr.on('data', (data) => {
      const chunk = data.toString();
      if (options.debug) {
        process.stderr.write(chunk);
      }
      stderr += chunk;
    });

    javaProcess.on('close', (code) => {
      if (code === 0) {
        resolve(stdout);
      } else {
        const error = new Error(
          `The opendataloader-pdf CLI exited with code ${code}.\n\n${stderr}`,
        );
        reject(error);
      }
    });

    javaProcess.on('error', (err) => {
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
