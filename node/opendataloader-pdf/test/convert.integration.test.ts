/**
 * Integration tests that actually run the JAR (slow)
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { convert } from '../src/index';
import * as path from 'path';
import * as fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const rootDir = path.resolve(__dirname, '..', '..', '..');
const inputPdf = path.join(rootDir, 'resources', '1901.03003.pdf');
const tempDir = path.join(__dirname, 'temp-convert');

describe('convert() integration', () => {
  beforeAll(() => {
    if (fs.existsSync(tempDir)) {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
    fs.mkdirSync(tempDir, { recursive: true });
  });

  afterAll(() => {
    if (fs.existsSync(tempDir)) {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
  });

  it('should generate output file', async () => {
    await convert(inputPdf, {
      outputDir: tempDir,
      format: 'json',
      quiet: true,
    });

    const outputFile = path.join(tempDir, '1901.03003.json');
    expect(fs.existsSync(outputFile)).toBe(true);
    expect(fs.statSync(outputFile).size).toBeGreaterThan(0);
  }, 30000);
});
