/**
 * Mock-based tests for convert() argument handling (fast)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..', '..', '..');
const inputPdf = path.join(rootDir, 'resources', '1901.03003.pdf');

// Mock child_process.spawn
vi.mock('child_process', () => ({
  spawn: vi.fn(() => ({
    stdout: { on: vi.fn() },
    stderr: { on: vi.fn() },
    on: vi.fn((event, callback) => {
      if (event === 'close') callback(0);
    }),
  })),
}));

import { spawn } from 'child_process';
import { convert } from '../src/index';

function getSpawnArgs(): string[] {
  const mockSpawn = spawn as unknown as ReturnType<typeof vi.fn>;
  const calls = mockSpawn.mock.calls;
  if (calls.length === 0) return [];
  const lastCall = calls[calls.length - 1];
  // spawn(command, args) -> args is lastCall[1]
  const args = lastCall[1] as string[];
  return args;
}

beforeEach(() => {
  vi.clearAllMocks();
});

// --- Format argument tests ---
describe('format arguments', () => {
  it.each([
    'json',
    'text',
    'html',
    'pdf',
    'markdown',
    'markdown-with-html',
    'markdown-with-images',
  ])('should pass format=%s', async (fmt) => {
    await convert(inputPdf, { format: fmt });
    const args = getSpawnArgs();
    expect(args).toContain('--format');
    expect(args).toContain(fmt);
  });

  it('should pass format as comma-separated list', async () => {
    await convert(inputPdf, { format: ['json', 'text'] });
    const args = getSpawnArgs();
    expect(args).toContain('--format');
    expect(args).toContain('json,text');
  });
});

// --- Input path argument tests ---
describe('input path arguments', () => {
  it('should pass input path as array', async () => {
    await convert([inputPdf], { format: 'json' });
    const args = getSpawnArgs();
    expect(args).toContain(inputPdf);
  });
});

// --- Option argument tests ---
describe('option arguments', () => {
  const optionCases: [Record<string, unknown>, string, string | null][] = [
    [{ keepLineBreaks: true }, '--keep-line-breaks', null],
    [{ replaceInvalidChars: ' ' }, '--replace-invalid-chars', ' '],
    [{ useStructTree: true }, '--use-struct-tree', null],
    [{ readingOrder: 'bbox' }, '--reading-order', 'bbox'],
    [{ tableMethod: 'cluster' }, '--table-method', 'cluster'],
    [{ tableMethod: ['cluster'] }, '--table-method', 'cluster'],
    [{ contentSafetyOff: 'hidden-text,tiny' }, '--content-safety-off', 'hidden-text,tiny'],
    [{ contentSafetyOff: ['hidden-text', 'tiny'] }, '--content-safety-off', 'hidden-text,tiny'],
  ];

  it.each(optionCases)('should pass %o as %s', async (option, cliFlag, value) => {
    await convert(inputPdf, { format: 'json', ...option });
    const args = getSpawnArgs();
    expect(args).toContain(cliFlag);
    if (value) {
      expect(args).toContain(value);
    }
  });
});

// --- Page separator tests ---
describe('page separator arguments', () => {
  const separatorCases: [Record<string, string>, string, string][] = [
    [{ markdownPageSeparator: '---' }, '--markdown-page-separator', '---'],
    [{ textPageSeparator: '---' }, '--text-page-separator', '---'],
    [{ htmlPageSeparator: '<hr/>' }, '--html-page-separator', '<hr/>'],
  ];

  it.each(separatorCases)('should pass %o as %s', async (option, cliFlag, value) => {
    await convert(inputPdf, { format: 'json', ...option });
    const args = getSpawnArgs();
    expect(args).toContain(cliFlag);
    expect(args).toContain(value);
  });
});

// --- Embed images tests ---
describe('embed images arguments', () => {
  it('should pass --embed-images flag', async () => {
    await convert(inputPdf, { format: 'json', embedImages: true });
    const args = getSpawnArgs();
    expect(args).toContain('--embed-images');
  });

  it.each(['png', 'jpeg'])('should pass --image-format %s', async (fmt) => {
    await convert(inputPdf, { format: 'json', imageFormat: fmt });
    const args = getSpawnArgs();
    expect(args).toContain('--image-format');
    expect(args).toContain(fmt);
  });

  it('should pass both embed-images and image-format', async () => {
    await convert(inputPdf, { format: 'json', embedImages: true, imageFormat: 'jpeg' });
    const args = getSpawnArgs();
    expect(args).toContain('--embed-images');
    expect(args).toContain('--image-format');
    expect(args).toContain('jpeg');
  });
});
