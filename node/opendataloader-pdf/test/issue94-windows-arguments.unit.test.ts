/**
 * Issue #94 (review): non-ASCII arguments and the temporary folder of the JAR on Windows.
 *
 * On Windows the Java launcher decodes the command line with the ANSI code page, so characters
 * outside it become "?". The PDF of convertBytes() is in the temporary folder of the user, and
 * that path can be non-ASCII. The CLI reads its arguments from OPENDATALOADER_PDF_ARGS when the
 * command line has none; the Python package uses it in the same case. These tests set
 * process.platform to win32 and check what reaches spawn(). They do not run Java on Windows.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { EventEmitter } from 'events';
import * as path from 'path';
import * as os from 'os';

vi.mock('child_process', () => ({
  spawn: vi.fn(),
}));

vi.mock('fs', async (importOriginal) => {
  const actual = await importOriginal<typeof import('fs')>();
  const existsSync = (file: import('fs').PathLike) =>
    String(file).endsWith('.jar') || actual.existsSync(file);
  return { ...actual, default: { ...actual, existsSync }, existsSync };
});

import { spawn } from 'child_process';
import * as fs from 'fs';
import { convert } from '../src/index';

class FakeProcess extends EventEmitter {
  stdout = new EventEmitter();
  stderr = new EventEmitter();
}

type SpawnCall = [string, string[], { env?: NodeJS.ProcessEnv }?];

function captureSpawn(): SpawnCall[] {
  const calls: SpawnCall[] = [];
  (spawn as unknown as ReturnType<typeof vi.fn>).mockImplementation((...call: SpawnCall) => {
    calls.push(call);
    const proc = new FakeProcess();
    setImmediate(() => proc.emit('close', 0));
    return proc;
  });
  return calls;
}

const afterJar = (call: SpawnCall) => call[1].slice(call[1].indexOf('-jar') + 2);
const isAscii = (value: string) => /^[\x00-\x7f]*$/.test(value);
const realPlatform = process.platform;

let testRoot: string;
let saved: Record<string, string | undefined>;

// A -Djava.io.tmpdir in one of these makes the package add no option of its own.
const JAVA_OPTION_VARS = ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'];

beforeEach(() => {
  testRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'issue94-windows-'));
  saved = { TMPDIR: process.env.TMPDIR, TMP: process.env.TMP, TEMP: process.env.TEMP };
  for (const name of JAVA_OPTION_VARS) {
    saved[name] = process.env[name];
    delete process.env[name];
  }
});

afterEach(() => {
  Object.defineProperty(process, 'platform', { value: realPlatform });
  for (const [name, value] of Object.entries(saved)) {
    if (value === undefined) {
      delete process.env[name];
    } else {
      process.env[name] = value;
    }
  }
  fs.rmSync(testRoot, { recursive: true, force: true });
  vi.clearAllMocks();
});

function onWindows() {
  Object.defineProperty(process, 'platform', { value: 'win32' });
}

function pdfIn(folder: string, name: string): string {
  fs.mkdirSync(folder, { recursive: true });
  const pdf = path.join(folder, name);
  fs.writeFileSync(pdf, '%PDF-1.7 stand-in');
  return pdf;
}

describe('the JAR on Windows', () => {
  it('passes non-ASCII arguments in OPENDATALOADER_PDF_ARGS on Windows', async () => {
    const calls = captureSpawn();
    const input = pdfIn(path.join(testRoot, 'in'), '문서.pdf');
    const out = path.join(testRoot, 'out');
    onWindows();

    await convert(input, { outputDir: out, quiet: true });

    expect(afterJar(calls[0]), 'non-ASCII argument on the Windows command line').toEqual([]);
    const value = calls[0][2]?.env?.OPENDATALOADER_PDF_ARGS as string;
    expect(isAscii(value)).toBe(true);
    const args = JSON.parse(value) as string[];
    expect(args[0]).toBe(input);
    expect(args).toContain(out);
    expect(calls[0][2]?.env?.PATH).toBe(process.env.PATH);
  });

  it('leaves out a non-ASCII temporary folder on Windows', async () => {
    const calls = captureSpawn();
    const temp = path.join(testRoot, '임시');
    fs.mkdirSync(temp);
    process.env.TMPDIR = process.env.TMP = process.env.TEMP = temp;
    const input = pdfIn(path.join(testRoot, 'in'), 'document.pdf');
    onWindows();

    await convert(input, { outputDir: path.join(testRoot, 'out'), quiet: true });

    expect(
      calls[0][1].some((arg) => arg.startsWith('-Djava.io.tmpdir=')),
      'non-ASCII temporary folder option on Windows',
    ).toBe(false);
  });

  it('keeps ASCII arguments and an ASCII temporary folder on the command line on Windows', async ({
    skip,
  }) => {
    // The paths of this case are in the temporary folder of the system; they are ASCII only if it is.
    if (!isAscii(testRoot)) {
      skip('the temporary folder of this system has a non-ASCII path');
    }
    const calls = captureSpawn();
    const temp = path.join(testRoot, 'temp');
    fs.mkdirSync(temp);
    process.env.TMPDIR = process.env.TMP = process.env.TEMP = temp;
    const input = pdfIn(path.join(testRoot, 'in'), 'document.pdf');
    onWindows();

    await convert(input, { outputDir: path.join(testRoot, 'out'), quiet: true });

    expect(calls[0]).toHaveLength(2);
    expect(afterJar(calls[0])[0]).toBe(input);
    expect(calls[0][1]).toContain(`-Djava.io.tmpdir=${temp}`);
  });

  it('keeps non-ASCII arguments on the command line on other systems', async () => {
    if (realPlatform === 'win32') {
      return;
    }
    const calls = captureSpawn();
    const input = pdfIn(path.join(testRoot, 'in'), '문서.pdf');

    await convert(input, { outputDir: path.join(testRoot, 'out'), quiet: true });

    expect(calls[0]).toHaveLength(2);
    expect(afterJar(calls[0])[0]).toBe(input);
  });
});
