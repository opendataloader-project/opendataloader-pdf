/**
 * Unit tests for the JAR execution layer's stdout/stderr handling.
 *
 * These tests use a fake child process (no real Java) so we can deterministically
 * assert the streaming/buffering contract:
 *   - convert() — library API: never writes to process.stdout, returns full stdout
 *   - _runForCli() — CLI helper: streams stdout/stderr to the parent in real time,
 *     does not return the stdout payload (the caller must not re-print it)
 *
 * Issue #398 reproducer: a long-running conversion (think hybrid mode, 1h+) must
 * surface progress via stderr without the CLI double-printing the result on close.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { EventEmitter } from 'events';

// Mock child_process so we control spawn() entirely (no real Java process).
vi.mock('child_process', () => ({
  spawn: vi.fn(),
}));

// Mock fs so JAR-file-exists and input-file-exists checks always pass.
vi.mock('fs', () => ({
  existsSync: vi.fn().mockReturnValue(true),
}));

import { spawn } from 'child_process';
import { convert, _runForCli } from '../src/index';

class FakeProcess extends EventEmitter {
  stdout = new EventEmitter() as EventEmitter & { pipe?: unknown };
  stderr = new EventEmitter() as EventEmitter & { pipe?: unknown };
}

function makeFakeSpawn(): {
  proc: FakeProcess;
  spawnMock: ReturnType<typeof vi.fn>;
} {
  const proc = new FakeProcess();
  const spawnMock = spawn as unknown as ReturnType<typeof vi.fn>;
  spawnMock.mockReturnValue(proc as unknown as ReturnType<typeof spawn>);
  return { proc, spawnMock };
}

/** Yield to the microtask queue so Promise callbacks fire. */
const tick = () => new Promise<void>((r) => setImmediate(r));

describe('executeJar — library API (convert)', () => {
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

  it('returns the full stdout string on success', async () => {
    const { proc } = makeFakeSpawn();
    const promise = convert('input.pdf', { quiet: true });

    proc.stdout.emit('data', Buffer.from('Lorem '));
    proc.stdout.emit('data', Buffer.from('ipsum dolor'));
    proc.emit('close', 0);

    await expect(promise).resolves.toBe('Lorem ipsum dolor');
  });

  it('does not write to process.stdout (no streaming side-effect)', async () => {
    const { proc } = makeFakeSpawn();
    const promise = convert('input.pdf', { quiet: true });

    proc.stdout.emit('data', Buffer.from('result text'));
    proc.emit('close', 0);

    await promise;

    expect(stdoutSpy).not.toHaveBeenCalled();
  });

  it('does not write to process.stderr (no streaming side-effect)', async () => {
    // Library callers should not have stderr leaked into their parent process.
    // Java's --quiet silences stderr at the source for typical library use,
    // but the contract holds even if Java emits to stderr.
    const { proc } = makeFakeSpawn();
    const promise = convert('input.pdf', { quiet: true });

    proc.stderr.emit('data', Buffer.from('정보: progress'));
    proc.stdout.emit('data', Buffer.from('result'));
    proc.emit('close', 0);

    await promise;

    expect(stderrSpy).not.toHaveBeenCalled();
  });

  it('rejects with stderr in the error message on non-zero exit', async () => {
    const { proc } = makeFakeSpawn();
    const promise = convert('input.pdf', { quiet: true });

    proc.stderr.emit('data', Buffer.from('Java stack trace'));
    proc.emit('close', 1);

    await expect(promise).rejects.toThrow(/exited with code 1/);
    await expect(promise).rejects.toThrow(/Java stack trace/);
  });

  it('reassembles multi-byte UTF-8 codepoints split across chunks', async () => {
    // Java's progress logs are localized; '정' = 0xEC 0xA0 0x95 (3 bytes).
    // If the OS hands us this codepoint split across two 'data' events, a
    // naive Buffer.toString() emits two replacement characters. Streaming
    // is meant to be byte-faithful to what Java printed, so we must
    // reassemble across the boundary.
    const { proc } = makeFakeSpawn();
    const promise = convert('input.pdf', { quiet: true });

    proc.stdout.emit('data', Buffer.from([0xEC, 0xA0])); // first 2 bytes of '정'
    proc.stdout.emit('data', Buffer.from([0x95]));        // last byte of '정'
    proc.stdout.emit('data', Buffer.from('보', 'utf8'));  // a clean codepoint
    proc.emit('close', 0);

    await expect(promise).resolves.toBe('정보');
  });
});

describe('executeJar — CLI helper (_runForCli)', () => {
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

  it('streams stdout chunks to process.stdout in real time', async () => {
    // The defining requirement for hybrid-mode-style long runs: the user must
    // see output as it arrives, not buffered until the process closes.
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    proc.stdout.emit('data', Buffer.from('chunk1'));
    await tick();
    expect(stdoutSpy).toHaveBeenCalledWith('chunk1');
    expect(stdoutSpy).toHaveBeenCalledTimes(1);

    proc.stdout.emit('data', Buffer.from('chunk2'));
    await tick();
    expect(stdoutSpy).toHaveBeenCalledTimes(2);

    proc.emit('close', 0);
    await promise;

    // Critical: process.stdout was called exactly twice — once per chunk.
    // No extra "final flush" call. That's how we avoid the #398 double-write.
    expect(stdoutSpy).toHaveBeenCalledTimes(2);
  });

  it('streams stderr chunks to process.stderr in real time', async () => {
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    proc.stderr.emit('data', Buffer.from('정보: Number of pages: 14\n'));
    await tick();
    expect(stderrSpy).toHaveBeenCalledWith('정보: Number of pages: 14\n');

    proc.stderr.emit('data', Buffer.from('정보: Processing 14 pages\n'));
    await tick();
    expect(stderrSpy).toHaveBeenCalledTimes(2);

    proc.emit('close', 0);
    await promise;
  });

  it('preserves true interleaving of stderr and stdout chunks as they arrive', async () => {
    // Hybrid mode emits progress logs on stderr *throughout* the run, with
    // result chunks landing on stdout in between. Both streams must reach the
    // parent in arrival order without one buffering the other.
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    const calls: string[] = [];
    stdoutSpy.mockImplementation(((chunk: Buffer) => {
      calls.push('OUT:' + chunk.toString());
      return true;
    }) as never);
    stderrSpy.mockImplementation(((chunk: Buffer) => {
      calls.push('ERR:' + chunk.toString());
      return true;
    }) as never);

    proc.stderr.emit('data', Buffer.from('progress 1'));
    await tick();
    proc.stdout.emit('data', Buffer.from('result A'));
    await tick();
    proc.stderr.emit('data', Buffer.from('progress 2'));
    await tick();
    proc.stdout.emit('data', Buffer.from('result B'));
    await tick();
    proc.emit('close', 0);
    await promise;

    expect(calls).toEqual([
      'ERR:progress 1',
      'OUT:result A',
      'ERR:progress 2',
      'OUT:result B',
    ]);
  });

  it('resolves without returning the stdout payload (caller must not re-print)', async () => {
    // _runForCli's contract: streaming has the side-effect, the resolved value
    // carries no stdout text. This is what blocks the CLI from double-printing.
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    proc.stdout.emit('data', Buffer.from('result text'));
    proc.emit('close', 0);

    const resolved = await promise;

    expect(resolved).toBeUndefined();
  });

  it('rejects with stderr in the error message on non-zero exit', async () => {
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    proc.stderr.emit('data', Buffer.from('boom'));
    proc.emit('close', 2);

    await expect(promise).rejects.toThrow(/exited with code 2/);
    await expect(promise).rejects.toThrow(/boom/);
  });

  it('forwards multi-byte UTF-8 to process.stderr without splitting codepoints', async () => {
    // Defining the streaming contract for non-ASCII progress logs. Without a
    // StringDecoder, splitting '정' (0xEC 0xA0 0x95) across two chunks would
    // emit two replacement characters to the user's terminal — exactly the
    // failure mode the comment in index.ts warns about.
    const { proc } = makeFakeSpawn();
    const promise = _runForCli(['input.pdf']);

    const forwarded: string[] = [];
    stderrSpy.mockImplementation(((chunk: string) => {
      forwarded.push(chunk);
      return true;
    }) as never);

    proc.stderr.emit('data', Buffer.from([0xEC, 0xA0])); // partial '정'
    await tick();
    proc.stderr.emit('data', Buffer.from([0x95]));        // completes '정'
    await tick();
    proc.stderr.emit('data', Buffer.from('보', 'utf8'));
    await tick();
    proc.emit('close', 0);
    await promise;

    // No replacement characters anywhere in what the user saw.
    const joined = forwarded.join('');
    expect(joined).toBe('정보');
    expect(joined).not.toContain('�');
  });
});

