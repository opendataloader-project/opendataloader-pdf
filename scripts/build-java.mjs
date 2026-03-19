#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..');
const PACKAGE_DIR = join(ROOT_DIR, 'java');
const result = process.platform === 'win32'
  ? spawnSync('cmd.exe', ['/d', '/s', '/c', 'mvn -B clean package -P release'], {
      cwd: PACKAGE_DIR,
      stdio: 'inherit',
    })
  : spawnSync('mvn', ['-B', 'clean', 'package', '-P', 'release'], {
      cwd: PACKAGE_DIR,
      stdio: 'inherit',
    });

if (result.error) {
  if (result.error.code === 'ENOENT') {
    console.error('Error: Maven not found in PATH');
  } else {
    console.error(result.error.message);
  }
  process.exit(1);
}

process.exit(result.status ?? 1);
