/**
 * Integration tests for the stdout/stderr streaming contract.
 *
 * Runs the bundled CLI as a real subprocess against a multi-page sample PDF.
 * Asserts the user-facing behavior that mock unit tests cannot prove:
 *   - CLI never double-prints stdout (regression test for #398)
 *   - Java's progress logs reach the parent's stderr in real time, before the
 *     stdout payload finishes — the property that makes hour-long hybrid runs
 *     observable
 *   - Library API does not leak Java's output to the parent's stdout
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { spawn, spawnSync } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { fileURLToPath, pathToFileURL } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const rootDir = path.resolve(__dirname, '..', '..', '..');
const cliPath = path.resolve(__dirname, '..', 'dist', 'cli.js');
const jarPath = path.resolve(__dirname, '..', 'lib', 'opendataloader-pdf-cli.jar');
const samplePdf = path.join(rootDir, 'samples', 'pdf', '2408.02509v1.pdf');

interface Capture {
  stdout: string;
  stderr: string;
  /** Wall-clock ms (since process spawn) of each chunk we received. */
  timeline: Array<{ stream: 'out' | 'err'; size: number; at: number }>;
  exitCode: number | null;
}

function captureSubprocess(command: string, args: string[]): Promise<Capture> {
  return new Promise((resolve, reject) => {
    const proc = spawn(command, args);
    const start = Date.now();
    const cap: Capture = { stdout: '', stderr: '', timeline: [], exitCode: null };

    proc.stdout.on('data', (chunk: Buffer) => {
      cap.stdout += chunk.toString();
      cap.timeline.push({ stream: 'out', size: chunk.length, at: Date.now() - start });
    });
    proc.stderr.on('data', (chunk: Buffer) => {
      cap.stderr += chunk.toString();
      cap.timeline.push({ stream: 'err', size: chunk.length, at: Date.now() - start });
    });
    proc.on('close', (code) => {
      cap.exitCode = code;
      resolve(cap);
    });
    proc.on('error', reject);
  });
}

const runCli = (args: string[]) => captureSubprocess('node', [cliPath, ...args]);
const runJar = (args: string[]) => captureSubprocess('java', ['-jar', jarPath, ...args]);

describe('CLI streaming contract', () => {
  beforeAll(() => {
    // Self-contained: build dist on demand so `pnpm test` works on a fresh
    // checkout without a separate `pnpm build` step. The JAR has to come from
    // the Maven build (it's not something Vitest should rebuild), so we still
    // surface a clear error if it's missing.
    if (!fs.existsSync(cliPath)) {
      const result = spawnSync(
        'pnpm',
        ['exec', 'tsup', '--no-dts', 'src/index.ts', 'src/cli.ts',
         '--format', 'esm,cjs', '--shims', '--out-dir', 'dist'],
        { cwd: path.resolve(__dirname, '..'), stdio: 'inherit' },
      );
      if (result.status !== 0) {
        throw new Error('Failed to build dist for streaming integration test');
      }
    }
    if (!fs.existsSync(jarPath)) {
      throw new Error(
        `Bundled JAR not found at ${jarPath} — build the Java module first ` +
        `(\`mvn package\` in java/, then \`pnpm run setup\` in this package).`,
      );
    }
    if (!fs.existsSync(samplePdf)) {
      throw new Error(`Sample PDF not found at ${samplePdf}`);
    }
  }, 60000);

  it('prints --to-stdout output exactly once (regression for #398)', async () => {
    // Ground truth: invoke the JAR directly with --quiet so stderr is empty
    // and stdout carries only the result payload. The Node CLI must produce
    // the same bytes — no more, no less. Comparing against the JAR rather
    // than another Node-CLI run prevents a symmetric-mutation regression
    // (e.g., trimming/appending in both code paths) from sneaking past.
    const referenceCap = await runJar([samplePdf, '--quiet', '--format', 'text', '--to-stdout']);
    expect(referenceCap.exitCode).toBe(0);
    const referenceStdout = referenceCap.stdout;
    expect(referenceStdout.length).toBeGreaterThan(1000);

    const cap = await runCli([samplePdf, '--format', 'text', '--to-stdout']);
    expect(cap.exitCode).toBe(0);

    // Defining check: the no-flag Node CLI must produce the *exact same*
    // stdout bytes as the JAR. Pre-fix this came out at 2x because of
    // double-write. Length-only comparison would miss byte-substituting
    // mutations of identical length, so we compare the full payload.
    expect(cap.stdout).toBe(referenceStdout);
  }, 60000);

  it('forwards Java progress logs to stderr in real time before stdout completes', async () => {
    const cap = await runCli([samplePdf, '--format', 'text', '--to-stdout']);
    expect(cap.exitCode).toBe(0);

    // Java emits a "Number of pages" line during preprocessing — long before
    // text extraction finishes — to stderr.
    expect(cap.stderr).toMatch(/Number of pages/);

    // The property that makes long runs observable: at least one stderr chunk
    // arrives before stdout finishes streaming. We compare *event indices*
    // (arrival order in cap.timeline) rather than millisecond timestamps to
    // stay deterministic — two events scheduled in the same tick will share
    // an `at` value but always have distinct indices.
    const firstErrIdx = cap.timeline.findIndex((e) => e.stream === 'err');
    let lastOutIdx = -1;
    for (let i = cap.timeline.length - 1; i >= 0; i--) {
      if (cap.timeline[i].stream === 'out') {
        lastOutIdx = i;
        break;
      }
    }
    expect(firstErrIdx).toBeGreaterThanOrEqual(0);
    expect(lastOutIdx).toBeGreaterThanOrEqual(0);
    expect(firstErrIdx).toBeLessThan(lastOutIdx);
  }, 60000);

  it('library convert() does not leak to the parent process stdio', async () => {
    // Spawn a tiny Node script that imports convert() and calls it. We capture
    // *that* process's stdio: convert() must not write to stdout/stderr itself.
    // The import specifier is a file:// URL so the harness works on Windows
    // (where path.resolve yields backslashes that ESM rejects).
    const distIndexUrl = pathToFileURL(path.resolve(__dirname, '..', 'dist', 'index.js')).href;
    const harness = `
      import { convert } from '${distIndexUrl}';
      const out = await convert(${JSON.stringify(samplePdf)}, {
        format: ['text'], toStdout: true, quiet: true,
      });
      // One intended write at the end so we can distinguish "the marker"
      // from any leakage caused by executeJar forwarding to process.stdout.
      process.stdout.write('LEN=' + out.length + '\\n');
    `;

    // Place the harness in an isolated tmpdir — survives Ctrl-C / OOM cleanly
    // (OS reaps tmpdir trees) instead of polluting the source tree.
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'odl-pdf-issue398-'));
    const tmpScript = path.join(tmpDir, 'harness.mjs');
    fs.writeFileSync(tmpScript, harness);
    try {
      const cap = await captureSubprocess('node', [tmpScript]);
      expect(cap.exitCode).toBe(0);
      // The only line on stdout must be our LEN= marker. Anything else means
      // executeJar leaked Java's stdout into the parent.
      expect(cap.stdout.trim().split('\n')).toHaveLength(1);
      expect(cap.stdout).toMatch(/^LEN=\d+\n$/);
      expect(cap.stderr).toBe('');
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  }, 60000);
});
