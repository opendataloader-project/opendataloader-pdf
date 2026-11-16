/**
 * Issue #94 (unit): what convertBytes() gives to the JAR, and its temporary folder.
 *
 * A stand-in replaces the Java process. It records its arguments and the input file, writes an
 * output file, and exits with the code that the test chooses. Only the check that the JAR file
 * exists is replaced in fs; the temporary files are real.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { EventEmitter } from 'events';
import * as path from 'path';
import * as os from 'os';
import { fileURLToPath } from 'url';

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
import * as odl from '../src/index';
import { convert } from '../src/index';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..', '..', '..');
const EXTENSIONS: Record<string, string> = {
  markdown: 'md',
  text: 'txt',
  json: 'json',
  html: 'html',
};
const SYMPTOM = 'no PDF bytes input: the package has no convertBytes()';
const STAND_IN_PDF = Buffer.from('%PDF-1.7 stand-in');
/** The options that the bytes input sets itself; the other options of convert() pass through. */
const SET_BY_BYTES_INPUT = new Set(['outputDir', 'format', 'imageOutput', 'toStdout', 'imageDir']);

type ConvertBytes = (data: unknown, options?: Record<string, unknown>) => Promise<string>;

function bytesInput(): ConvertBytes {
  const fn = (odl as Record<string, unknown>).convertBytes;
  if (typeof fn !== 'function') {
    throw new Error(SYMPTOM);
  }
  return fn as ConvertBytes;
}

const camel = (name: string) => name.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase());

class FakeProcess extends EventEmitter {
  stdout = new EventEmitter();
  stderr = new EventEmitter();
}

interface Run {
  args: string[];
  bytes?: Buffer;
  fileMode?: number;
  folderMode?: number;
}

type Outcome = { exitCode: number; writeOutput?: boolean } | { spawnError: string };

function standIn(runs: Run[], outcome: Outcome = { exitCode: 0 }) {
  (spawn as unknown as ReturnType<typeof vi.fn>).mockImplementation(
    (_command: string, commandArgs: string[]) => {
      const proc = new FakeProcess();
      const args = commandArgs.slice(commandArgs.indexOf('-jar') + 2);
      const pdf = args.find((arg) => arg.endsWith('.pdf')) as string;
      const out = args[args.indexOf('--output-dir') + 1];
      const run: Run = { args };
      if (fs.existsSync(pdf)) {
        run.bytes = fs.readFileSync(pdf);
        run.fileMode = fs.statSync(pdf).mode & 0o777;
        run.folderMode = fs.statSync(path.dirname(pdf)).mode & 0o777;
      }
      runs.push(run);
      setImmediate(() => {
        if ('spawnError' in outcome) {
          proc.emit('error', Object.assign(new Error(outcome.spawnError), { code: 'ENOENT' }));
          return;
        }
        if (outcome.exitCode === 0 && outcome.writeOutput !== false) {
          fs.mkdirSync(out, { recursive: true });
          const extension = EXTENSIONS[args[args.indexOf('--format') + 1]];
          fs.writeFileSync(
            path.join(out, `${path.basename(pdf, '.pdf')}.${extension}`),
            'stand-in output',
          );
        }
        proc.emit('close', outcome.exitCode);
      });
      return proc;
    },
  );
}

let stdoutSpy: ReturnType<typeof vi.spyOn>;
let stderrSpy: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  stdoutSpy = vi.spyOn(process.stdout, 'write').mockImplementation(() => true);
  stderrSpy = vi.spyOn(process.stderr, 'write').mockImplementation(() => true);
});

afterEach(() => {
  stdoutSpy.mockRestore();
  stderrSpy.mockRestore();
  vi.clearAllMocks();
});

describe('convertBytes() and the JAR', () => {
  it('every option reaches the JAR unchanged', async () => {
    const convertBytes = bytesInput();
    const runs: Run[] = [];
    standIn(runs);
    // Each temporary folder of this test is in testRoot. The test removes only testRoot.
    const testRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'issue94-options-'));
    const saved = { TMPDIR: process.env.TMPDIR, TMP: process.env.TMP, TEMP: process.env.TEMP };
    process.env.TMPDIR = process.env.TMP = process.env.TEMP = testRoot;
    const definitions = JSON.parse(fs.readFileSync(path.join(rootDir, 'options.json'), 'utf8')) as {
      options: { name: string; type: string }[];
    };
    const options: Record<string, unknown> = {};
    for (const definition of definitions.options) {
      const key = camel(definition.name);
      if (!SET_BY_BYTES_INPUT.has(key)) {
        options[key] = definition.type === 'boolean' ? true : '1';
      }
    }

    let fromBytes: string[];
    let pdf: string;
    try {
      await convertBytes(STAND_IN_PDF, { ...options, format: 'json' });
      fromBytes = runs[0].args;
      pdf = fromBytes.find((arg) => arg.endsWith('.pdf')) as string;
      const out = fromBytes[fromBytes.indexOf('--output-dir') + 1];
      expect(path.dirname(path.dirname(pdf))).toBe(testRoot);
      fs.mkdirSync(path.dirname(pdf));
      fs.writeFileSync(pdf, STAND_IN_PDF);
      await convert(pdf, { ...options, outputDir: out, format: 'json', imageOutput: 'off' });
    } finally {
      for (const [name, value] of Object.entries(saved)) {
        if (value === undefined) {
          delete process.env[name];
        } else {
          process.env[name] = value;
        }
      }
      fs.rmSync(testRoot, { recursive: true, force: true });
    }

    expect(runs[1].args).toEqual(fromBytes);
    expect(runs[0].bytes).toEqual(STAND_IN_PDF);
    expect(path.basename(pdf)).toBe('document.pdf');
  });

  it('name names the input file', async () => {
    const convertBytes = bytesInput();
    const runs: Run[] = [];
    standIn(runs);

    await convertBytes(STAND_IN_PDF, { format: 'markdown', name: 'report' });

    expect(path.basename(runs[0].args.find((arg) => arg.endsWith('.pdf')) as string)).toBe(
      'report.pdf',
    );
  });

  const refused: [Record<string, unknown>, string][] = [
    [{ outputDir: 'out' }, 'outputDir'],
    [{ toStdout: true }, 'toStdout'],
    [{ imageDir: 'images' }, 'imageDir'],
    [{ imageOutput: 'external' }, 'imageOutput'],
    [{ format: 'pdf' }, 'format'],
    [{ format: 'tagged-pdf' }, 'format'],
    [{ format: 'json,markdown' }, 'format'],
    [{ format: ['json', 'markdown'] }, 'format'],
    [{ name: '../document' }, 'name'],
    [{ name: '' }, 'name'],
  ];
  for (const [options, named] of refused) {
    describe(JSON.stringify(options), () => {
      it('refuses an option that the bytes input sets', async () => {
        const convertBytes = bytesInput();
        const runs: Run[] = [];
        standIn(runs);

        await expect(
          convertBytes(STAND_IN_PDF, { format: 'markdown', ...options }),
        ).rejects.toThrow(named);
        expect(runs).toEqual([]);
      });
    });
  }

  const data: [string, unknown, ErrorConstructor][] = [
    ['empty Buffer', Buffer.alloc(0), Error],
    ['empty Uint8Array', new Uint8Array(0), Error],
    ['string', '%PDF-1.7', TypeError],
    ['undefined', undefined, TypeError],
  ];
  for (const [id, value, errorType] of data) {
    describe(id, () => {
      it('refuses empty or other data before the JAR', async () => {
        const convertBytes = bytesInput();
        const runs: Run[] = [];
        standIn(runs);

        await expect(convertBytes(value, { format: 'markdown' })).rejects.toBeInstanceOf(errorType);
        expect(runs).toEqual([]);
      });
    });
  }

  it('accepts a Buffer and a Uint8Array', async () => {
    const convertBytes = bytesInput();
    const runs: Run[] = [];
    standIn(runs);

    await convertBytes(STAND_IN_PDF, { format: 'markdown' });
    await convertBytes(new Uint8Array(STAND_IN_PDF), { format: 'markdown' });

    expect(runs.map((run) => run.bytes)).toEqual([STAND_IN_PDF, STAND_IN_PDF]);
  });

  it('fails when the JAR writes no output', async () => {
    const convertBytes = bytesInput();
    standIn([], { exitCode: 0, writeOutput: false });

    await expect(convertBytes(STAND_IN_PDF, { format: 'markdown' })).rejects.toThrow('no output');
  });
});

describe('convertBytes() and its temporary folder', () => {
  let tempRoot: string;
  let savedTmp: Record<string, string | undefined>;

  beforeEach(() => {
    tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'issue94-unit-'));
    savedTmp = { TMPDIR: process.env.TMPDIR, TMP: process.env.TMP, TEMP: process.env.TEMP };
    process.env.TMPDIR = process.env.TMP = process.env.TEMP = path.join(tempRoot, 'tmp');
    fs.mkdirSync(path.join(tempRoot, 'tmp'));
  });

  afterEach(() => {
    for (const [name, value] of Object.entries(savedTmp)) {
      if (value === undefined) {
        delete process.env[name];
      } else {
        process.env[name] = value;
      }
    }
    fs.rmSync(tempRoot, { recursive: true, force: true });
  });

  const outcomes: [string, Outcome][] = [
    ['success', { exitCode: 0 }],
    ['error of the JAR', { exitCode: 1 }],
    ['no java command', { spawnError: 'spawn java ENOENT' }],
  ];
  for (const [id, outcome] of outcomes) {
    describe(id, () => {
      it('removes the temporary folder', async () => {
        const convertBytes = bytesInput();
        const runs: Run[] = [];
        standIn(runs, outcome);

        const result = convertBytes(STAND_IN_PDF, { format: 'markdown' });
        if (id === 'success') {
          await result;
        } else {
          await expect(result).rejects.toThrow();
        }

        expect(runs).toHaveLength(1);
        expect(fs.readdirSync(path.join(tempRoot, 'tmp'))).toEqual([]);
      });
    });
  }

  it.skipIf(process.platform === 'win32')('keeps the temporary files private', async () => {
    const convertBytes = bytesInput();
    const runs: Run[] = [];
    standIn(runs);

    await convertBytes(STAND_IN_PDF, { format: 'markdown' });

    expect([runs[0].folderMode, runs[0].fileMode]).toEqual([0o700, 0o600]);
  });
});
