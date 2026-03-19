#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..');
const CLI_TARGET_DIR = join(ROOT_DIR, 'java', 'opendataloader-pdf-cli', 'target');
const OPTIONS_PATH = join(ROOT_DIR, 'options.json');
const javaCommand = process.platform === 'win32' ? 'java.exe' : 'java';

export function findCliJar(targetDir) {
  const candidates = readdirSync(targetDir, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .filter((name) =>
      name.startsWith('opendataloader-pdf-cli-') &&
      name.endsWith('.jar') &&
      !name.includes('-sources') &&
      !name.includes('-javadoc') &&
      !name.startsWith('original-'));

  if (candidates.length === 0) {
    throw new Error(`No CLI jar found in ${targetDir}. Run "npm run build-java" first.`);
  }

  if (candidates.length > 1) {
    throw new Error(`Multiple CLI jars found in ${targetDir}: ${candidates.join(', ')}`);
  }

  return join(targetDir, candidates[0]);
}

export function exportOptions() {
  let cliJarPath;
  try {
    cliJarPath = findCliJar(CLI_TARGET_DIR);
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }

  try {
    const optionsJson = execFileSync(javaCommand, ['-jar', cliJarPath, '--export-options'], {
      cwd: ROOT_DIR,
      encoding: 'utf8',
    });
    writeFileSync(OPTIONS_PATH, optionsJson);
    console.log(`Exported options to ${OPTIONS_PATH}`);
  } catch (error) {
    if (error.code === 'ENOENT') {
      console.error(`Error: ${javaCommand} not found in PATH`);
    } else if (typeof error.stderr === 'string' && error.stderr.trim()) {
      console.error(error.stderr.trim());
    } else {
      console.error(error.message);
    }
    process.exit(1);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  exportOptions();
}
