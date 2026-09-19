import assert from 'node:assert/strict';
import test from 'node:test';

import { buildCorrelation, parseCorrelation } from '../lib/correlation.js';

test('builds and parses an exactly 256 UTF-8 byte correlation text', () => {
  const text = buildCorrelation({
    batch: 'batch-20260914',
    round: '42',
    phase: 'measure',
    sequence: '9007199254740993123',
    messageBytes: 256,
  });

  assert.equal(Buffer.byteLength(text, 'utf8'), 256);
  assert.deepEqual(parseCorrelation(text, { messageBytes: 256 }), {
    batch: 'batch-20260914',
    round: '42',
    phase: 'measure',
    sequence: '9007199254740993123',
  });
});

test('supports warmup while keeping fixed field widths', () => {
  const warmup = buildCorrelation({
    batch: 'b1',
    round: '1',
    phase: 'warmup',
    sequence: '7',
    messageBytes: 256,
  });
  const measure = buildCorrelation({
    batch: 'b1',
    round: '1',
    phase: 'measure',
    sequence: '7',
    messageBytes: 256,
  });

  assert.equal(Buffer.byteLength(warmup), Buffer.byteLength(measure));
  assert.equal(parseCorrelation(warmup).phase, 'warmup');
  assert.equal(parseCorrelation(measure).phase, 'measure');
});

test('rejects impossible sizes and malformed correlation text', () => {
  const input = {
    batch: 'batch-1',
    round: '1',
    phase: 'measure',
    sequence: '1',
  };

  assert.throws(() => buildCorrelation({ ...input, messageBytes: 32 }), /too small/i);
  assert.throws(() => buildCorrelation({ ...input, phase: 'drain', messageBytes: 256 }), /phase/i);
  assert.throws(() => buildCorrelation({ ...input, batch: '用户-1', messageBytes: 256 }), /batch/i);
  assert.throws(() => parseCorrelation('not-a-correlation'), /correlation/i);
  assert.throws(
    () => parseCorrelation(`${buildCorrelation({ ...input, messageBytes: 256 })}x`),
    /256 UTF-8 bytes/i,
  );
});
