/**
 * Unit tests for the error output of the bundled CLI (src/cli.ts).
 *
 * _runForCli is mocked, so no Java process starts. Each test imports cli.ts
 * again, because the module runs main() when it loads.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

const runForCli = vi.fn();

vi.mock('../src/index', () => ({
  _runForCli: (...args: unknown[]) => runForCli(...args),
}));

describe('CLI error output', () => {
  const argv = process.argv;
  let errors: string[];
  let exit: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.resetModules();
    runForCli.mockReset();
    errors = [];
    vi.spyOn(console, 'error').mockImplementation((...parts: unknown[]) => {
      errors.push(parts.map(String).join(' '));
    });
    exit = vi.spyOn(process, 'exit').mockImplementation((() => undefined) as never);
    process.argv = ['node', 'cli.js', 'input.pdf', '--password', 'secret-value'];
  });

  afterEach(() => {
    process.argv = argv;
    vi.restoreAllMocks();
  });

  async function runCli(): Promise<void> {
    await import('../src/cli');
    await vi.waitFor(() => expect(exit).toHaveBeenCalled());
  }

  it('does not print a rejection value that is not an Error', async () => {
    runForCli.mockRejectedValue(['input.pdf', '--password', 'secret-value']);

    await runCli();

    expect(exit).toHaveBeenCalledWith(1);
    expect(errors.join('\n')).not.toContain('secret-value');
    expect(errors).toEqual(['The opendataloader-pdf CLI failed.']);
  });

  it('prints the message of a wrapper-side Error', async () => {
    runForCli.mockRejectedValue(new Error('Input file or folder not found: input.pdf'));

    await runCli();

    expect(exit).toHaveBeenCalledWith(1);
    expect(errors).toEqual(['Input file or folder not found: input.pdf']);
  });

  it('prints nothing more for a Java exit and returns its code', async () => {
    runForCli.mockRejectedValue(
      Object.assign(new Error('The opendataloader-pdf CLI exited with code 2.'), {
        isJavaExit: true,
        exitCode: 2,
      }),
    );

    await runCli();

    expect(exit).toHaveBeenCalledWith(2);
    expect(errors).toEqual([]);
  });
});
