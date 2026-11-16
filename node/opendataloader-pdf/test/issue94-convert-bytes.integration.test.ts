/**
 * Issue #94: the bytes of a PDF as input of the Node package (runs the JAR, slow).
 *
 * convert() takes only file paths, so a caller with the bytes of a PDF must write a file first.
 * convertBytes() takes the bytes, writes them to a private temporary folder, runs convert() on that
 * file, and returns the output as a string. The expected output is the output that convert() writes
 * for the same PDF as a file: the cases are in verification/convert-bytes-cases.json, which the
 * tests of the Python package read too.
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as odl from '../src/index';
import { convert } from '../src/index';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..', '..', '..');
const table = JSON.parse(
  fs.readFileSync(path.join(rootDir, 'verification', 'convert-bytes-cases.json'), 'utf8'),
) as { name: string; cases: { id: string; pdf: string; options: Record<string, string> }[] };
const EXTENSIONS: Record<string, string> = {
  markdown: 'md',
  text: 'txt',
  json: 'json',
  html: 'html',
};
const SYMPTOM = 'no PDF bytes input: the package has no convertBytes()';

type ConvertBytes = (data: Uint8Array, options?: Record<string, unknown>) => Promise<string>;

function bytesInput(): ConvertBytes {
  const fn = (odl as Record<string, unknown>).convertBytes;
  if (typeof fn !== 'function') {
    throw new Error(SYMPTOM);
  }
  return fn as ConvertBytes;
}

const camel = (name: string) => name.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase());
const nodeOptions = (cli: Record<string, string>) =>
  Object.fromEntries(Object.entries(cli).map(([key, value]) => [camel(key), value]));

let tempDir: string;

beforeAll(() => {
  tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'issue94-'));
});

afterAll(() => {
  fs.rmSync(tempDir, { recursive: true, force: true });
});

describe('convertBytes()', () => {
  for (const testCase of table.cases) {
    describe(testCase.id, () => {
      it('equals the output of convert() for the same file', async () => {
        const convertBytes = bytesInput();
        const options = nodeOptions(testCase.options);
        const named = path.join(tempDir, testCase.id, 'in', `${table.name}.pdf`);
        fs.mkdirSync(path.dirname(named), { recursive: true });
        fs.copyFileSync(path.join(rootDir, testCase.pdf), named);
        const out = path.join(tempDir, testCase.id, 'out');
        await convert(named, { imageOutput: 'off', ...options, outputDir: out, quiet: true });
        const expected = fs.readFileSync(
          path.join(out, `${table.name}.${EXTENSIONS[options.format as string]}`),
          'utf8',
        );

        const actual = await convertBytes(fs.readFileSync(path.join(rootDir, testCase.pdf)), {
          ...options,
          quiet: true,
        });

        expect(actual).toBe(expected);
      }, 60000);
    });
  }

  const passwordPdf = path.join(rootDir, 'samples', 'pdf', 'password-protected.pdf');
  const failures: [string, () => Buffer, Record<string, unknown>][] = [
    ['not a PDF', () => Buffer.from('This is not a PDF.'), {}],
    ['no password', () => fs.readFileSync(passwordPdf), {}],
    ['wrong password', () => fs.readFileSync(passwordPdf), { password: 'wrong' }],
  ];
  for (const [id, data, options] of failures) {
    describe(id, () => {
      it('fails as convert() fails for the same file', async () => {
        const convertBytes = bytesInput();
        const named = path.join(tempDir, `failure-${id.replace(/ /g, '-')}`, `${table.name}.pdf`);
        fs.mkdirSync(path.dirname(named), { recursive: true });
        fs.writeFileSync(named, data());
        const fromFile = await convert(named, {
          format: 'markdown',
          imageOutput: 'off',
          quiet: true,
          outputDir: path.join(path.dirname(named), 'out'),
          ...options,
        }).then(
          () => null,
          (error: Error) => error,
        );
        expect(fromFile).toBeInstanceOf(Error);

        const fromBytes = await convertBytes(data(), {
          format: 'markdown',
          quiet: true,
          ...options,
        }).then(
          () => null,
          (error: Error) => error,
        );

        expect(fromBytes).toBeInstanceOf(Error);
        expect(fromBytes!.message.split('\n')[0]).toBe(fromFile!.message.split('\n')[0]);
      }, 60000);
    });
  }
});
