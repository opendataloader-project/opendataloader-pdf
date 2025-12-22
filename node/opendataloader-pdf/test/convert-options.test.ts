/**
 * Unit tests for auto-generated convert-options functions
 */

import { describe, it, expect } from 'vitest';
import {
  buildArgs,
  buildConvertOptions,
  ConvertOptions,
  CliOptions,
} from '../src/convert-options.generated';

describe('buildArgs()', () => {
  it('should return empty array for empty options', () => {
    const args = buildArgs({});
    expect(args).toEqual([]);
  });

  it('should handle string options', () => {
    const args = buildArgs({
      outputDir: '/output',
      password: 'secret',
      readingOrder: 'xycut',
    });
    expect(args).toEqual([
      '--output-dir', '/output',
      '--password', 'secret',
      '--reading-order', 'xycut',
    ]);
  });

  it('should handle boolean options', () => {
    const args = buildArgs({
      quiet: true,
      keepLineBreaks: true,
    });
    expect(args).toEqual([
      '--quiet',
      '--keep-line-breaks',
    ]);
  });

  it('should handle list options with string value', () => {
    const args = buildArgs({
      format: 'json,markdown',
      contentSafetyOff: 'all',
    });
    expect(args).toEqual([
      '--format', 'json,markdown',
      '--content-safety-off', 'all',
    ]);
  });

  it('should handle list options with array value', () => {
    const args = buildArgs({
      format: ['json', 'markdown', 'html'],
      contentSafetyOff: ['hidden-text', 'off-page'],
    });
    expect(args).toEqual([
      '--format', 'json,markdown,html',
      '--content-safety-off', 'hidden-text,off-page',
    ]);
  });

  it('should handle all options together', () => {
    const options: ConvertOptions = {
      outputDir: '/output',
      password: 'secret',
      format: ['json', 'markdown'],
      quiet: true,
      contentSafetyOff: 'all',
      keepLineBreaks: true,
      replaceInvalidChars: '_',
      useStructTree: true,
      tableMethod: 'cluster',
      readingOrder: 'xycut',
      markdownPageSeparator: '---',
      textPageSeparator: '\\n\\n',
      htmlPageSeparator: '<hr>',
      imageOutput: 'external',
      imageFormat: 'jpeg',
    };

    const args = buildArgs(options);

    expect(args).toContain('--output-dir');
    expect(args).toContain('/output');
    expect(args).toContain('--password');
    expect(args).toContain('secret');
    expect(args).toContain('--format');
    expect(args).toContain('json,markdown');
    expect(args).toContain('--quiet');
    expect(args).toContain('--content-safety-off');
    expect(args).toContain('all');
    expect(args).toContain('--keep-line-breaks');
    expect(args).toContain('--replace-invalid-chars');
    expect(args).toContain('_');
    expect(args).toContain('--use-struct-tree');
    expect(args).toContain('--table-method');
    expect(args).toContain('cluster');
    expect(args).toContain('--reading-order');
    expect(args).toContain('xycut');
    expect(args).toContain('--markdown-page-separator');
    expect(args).toContain('---');
    expect(args).toContain('--image-output');
    expect(args).toContain('external');
    expect(args).toContain('--image-format');
    expect(args).toContain('jpeg');
  });

  it('should not include undefined options', () => {
    const args = buildArgs({
      outputDir: '/output',
      quiet: false,
      keepLineBreaks: false,
    });
    expect(args).toEqual(['--output-dir', '/output']);
  });

  it('should skip empty arrays for list options', () => {
    const args = buildArgs({
      format: [],
      contentSafetyOff: [],
      outputDir: '/output',
    });
    expect(args).toEqual(['--output-dir', '/output']);
  });
});

describe('buildConvertOptions()', () => {
  it('should return empty object for empty CLI options', () => {
    const result = buildConvertOptions({});
    expect(result).toEqual({});
  });

  it('should convert string options', () => {
    const cliOptions: CliOptions = {
      outputDir: '/output',
      password: 'secret',
      readingOrder: 'xycut',
      imageFormat: 'png',
    };
    const result = buildConvertOptions(cliOptions);
    expect(result).toEqual({
      outputDir: '/output',
      password: 'secret',
      readingOrder: 'xycut',
      imageFormat: 'png',
    });
  });

  it('should convert boolean options', () => {
    const cliOptions: CliOptions = {
      quiet: true,
      keepLineBreaks: true,
      useStructTree: true,
    };
    const result = buildConvertOptions(cliOptions);
    expect(result).toEqual({
      quiet: true,
      keepLineBreaks: true,
      useStructTree: true,
    });
  });

  it('should not include false boolean options', () => {
    const cliOptions: CliOptions = {
      outputDir: '/output',
      quiet: false,
      keepLineBreaks: false,
    };
    const result = buildConvertOptions(cliOptions);
    expect(result).toEqual({
      outputDir: '/output',
    });
  });

  it('should pass through all provided options', () => {
    const cliOptions: CliOptions = {
      outputDir: '/output',
      password: 'secret',
      format: 'json,markdown',
      quiet: true,
      contentSafetyOff: 'all',
      keepLineBreaks: true,
      replaceInvalidChars: '_',
      useStructTree: true,
      tableMethod: 'cluster',
      readingOrder: 'xycut',
      markdownPageSeparator: '---',
      textPageSeparator: '\\n',
      htmlPageSeparator: '<hr>',
      imageOutput: 'external',
      imageFormat: 'jpeg',
    };
    const result = buildConvertOptions(cliOptions);
    expect(result.outputDir).toBe('/output');
    expect(result.password).toBe('secret');
    expect(result.format).toBe('json,markdown');
    expect(result.quiet).toBe(true);
    expect(result.contentSafetyOff).toBe('all');
    expect(result.keepLineBreaks).toBe(true);
    expect(result.replaceInvalidChars).toBe('_');
    expect(result.useStructTree).toBe(true);
    expect(result.tableMethod).toBe('cluster');
    expect(result.readingOrder).toBe('xycut');
    expect(result.markdownPageSeparator).toBe('---');
    expect(result.textPageSeparator).toBe('\\n');
    expect(result.htmlPageSeparator).toBe('<hr>');
    expect(result.imageOutput).toBe('external');
    expect(result.imageFormat).toBe('jpeg');
  });
});
