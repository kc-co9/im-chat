import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

import { parseCorrelation } from '../lib/correlation.js';
import {
  messageOrdinals,
  messageSlot,
} from '../lib/message-scheduler.js';

test('VU message ordinals partition one global sequence without overlap', () => {
  assert.deepEqual(messageOrdinals({ cohortIndex: 0, cohortCount: 3, totalMessages: 10 }), [0, 3, 6, 9]);
  assert.deepEqual(messageOrdinals({ cohortIndex: 1, cohortCount: 3, totalMessages: 10 }), [1, 4, 7]);
  assert.deepEqual(messageOrdinals({ cohortIndex: 2, cohortCount: 3, totalMessages: 10 }), [2, 5, 8]);
});

test('global sequence assigns exactly seventy private and thirty group messages per hundred', () => {
  const types = Array.from({ length: 100 }, (_, ordinal) => slot(ordinal).messageType);

  assert.equal(types.filter((type) => type === 'private').length, 70);
  assert.equal(types.filter((type) => type === 'group').length, 30);
});

test('sender and expected receivers stay inside the complete cohort', () => {
  const privateMessage = slot(6, { groupSize: 10 });
  assert.equal(privateMessage.senderIndex, 6);
  assert.deepEqual(privateMessage.receiverIndexes, [7]);

  const groupMessage = slot(7, { groupSize: 10 });
  assert.equal(groupMessage.senderIndex, 7);
  assert.deepEqual(groupMessage.receiverIndexes, [0, 1, 2, 3, 4, 5, 6, 8, 9]);
});

test('slot target uses the global rate and late work is skipped without catch-up', () => {
  assert.deepEqual(slot(13, {
    measureStartMs: 1_000,
    targetRate: 20,
    nowMs: 1_600,
  }).timing, {
    targetAtMs: 1_650,
    waitMs: 50,
    latenessMs: 0,
    skipped: false,
  });
  assert.deepEqual(slot(13, {
    measureStartMs: 1_000,
    targetRate: 20,
    nowMs: 1_851,
  }).timing, {
    targetAtMs: 1_650,
    waitMs: 0,
    latenessMs: 201,
    skipped: true,
  });
});

test('every scheduled body is exactly 256 bytes and carries its isolated window', () => {
  const measured = slot(12, { phase: 'measure' });
  const warmed = slot(12, { phase: 'warmup' });

  assert.equal(new TextEncoder().encode(measured.body).length, 256);
  assert.equal(new TextEncoder().encode(warmed.body).length, 256);
  assert.deepEqual(parseCorrelation(measured.body), {
    batch: 'batch1', round: '1', phase: 'measure', sequence: '12',
  });
  assert.deepEqual(parseCorrelation(warmed.body), {
    batch: 'batch1', round: '1', phase: 'warmup', sequence: '12',
  });
  assert.equal(measured.messageToken, 'loadtest-batch1:1:measure:12');
  assert.equal(warmed.messageToken, 'loadtest-batch1:1:warmup:12');
  assert.equal(measured.messageToken.length <= 90, true);
});

test('message scenario force-stops only sockets that outlive the close grace period', async () => {
  const source = await readFile(new URL('../scenarios/message-throughput.js', import.meta.url), 'utf8');

  assert.match(source, /from 'k6\/execution'/);
  assert.match(source, /openedUsers\.size[^\n]*=== 0/);
  assert.match(source, /exec\.test\.abort/);
  assert.match(source, /im_message_forced_stops/);
});

function slot(ordinal, overrides = {}) {
  return messageSlot({
    ordinal,
    cohortIndex: 0,
    groupSize: 10,
    measureStartMs: 1_000,
    targetRate: 20,
    nowMs: 1_000,
    lateSlotMs: 200,
    batch: 'batch1',
    round: '1',
    phase: 'measure',
    messageBytes: 256,
    ...overrides,
  });
}
