import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const scenarioUrl = new URL('../scenarios/message-throughput.js', import.meta.url);
const runnerUrl = new URL('../../../scripts/test-load-test-k6-scenarios.sh', import.meta.url);
const checkerUrl = new URL('../tools/message-fixture-check.mjs', import.meta.url);

test('message scenario uses one immutable cohort per VU and the shared scheduling contracts', async () => {
  const source = await readFile(scenarioUrl, 'utf8');

  assert.match(source, /new SharedArray\(/);
  assert.match(source, /executor:\s*['"]per-vu-iterations['"]/);
  assert.match(source, /__VU\s*-\s*1/);
  assert.match(source, /messageSlot\(/);
  assert.match(source, /message\.private\.send/);
  assert.match(source, /message\.group\.send/);
  assert.match(source, /notification\.ack/);
  assert.match(source, /BACKLOG_SAMPLE_SECONDS.*60/);
  assert.match(source, /openedUsers\.size !== GROUP_SIZE/);
  assert.match(source, /openedUsers\.delete/);
  assert.match(source, /state\.buffered\.length >= MAX_TRACKED_DELIVERIES_PER_VU/);
  assert.match(source, /const receivedAtMs = Date\.now\(\).*state\.buffered\.push\(\{ member, socket, frame, receivedAtMs \}\)/s);
  assert.match(source, /processPush\(buffered\.member, buffered\.socket, buffered\.frame, send, buffered\.receivedAtMs\)/);
  assert.match(source, /MAX_TRACKED_SENDS_PER_VU/);
  assert.match(source, /MAX_COMPLETED_RECEIPTS_PER_VU/);
  assert.match(source, /trackLatencies: false/);
  assert.match(source, /messageContent/);
  for (const diagnostic of [
    'sendFailuresPrivate',
    'sendFailuresGroup',
    'sendBusyFailures',
    'sendRepeatFailures',
    'sendOtherBusinessFailures',
    'sendResponseTimeouts',
  ]) {
    assert.match(source, new RegExp(diagnostic));
  }
  assert.doesNotMatch(source, /bufferedAmount/);
});

test('message fixture executes the real k6 scenario and verifies exact fanout', async () => {
  const source = await readFile(runnerUrl, 'utf8');
  const checker = await readFile(checkerUrl, 'utf8');
  assert.match(source, /run_message_fixture/);
  assert.match(source, /message-throughput\.js/);
  assert.match(source, /normal duplicate missing unknown ack-failure send-timeout send-busy send-repeat response-after-push/);
  assert.match(source, /message-fixture-check\.mjs/);
  assert.match(source, /generator saturated/);
  assert.match(checker, /metrics\.expectedDeliveries !== 68/);
  assert.match(checker, /'send-timeout': \[19, 67, 0, 0, 1, 1, 67, 67, 0\]/);
  assert.match(checker, /'send-busy': \[19, 59, 0, 0, 1, 9, 59, 59, 0\]/);
});

test('message summary contains exact delivery and drain counters without credentials', async () => {
  const source = await readFile(scenarioUrl, 'utf8');

  for (const name of [
    'sends', 'sendSuccesses', 'expectedDeliveries', 'deliveryAckSuccesses',
    'unknownReceivers', 'duplicates', 'drainResidual', 'postDrainExcess',
    'sendResponseLatencyP95Ms', 'sendResponseLatencyP99Ms',
    'receiveLatencyP95Ms', 'receiveLatencyP99Ms',
  ]) {
    assert.match(source, new RegExp(name));
  }
  assert.match(source, /export function handleSummary\s*\(/);
  assert.doesNotMatch(source, /accessToken\s*:/);
  assert.doesNotMatch(source, /refreshToken\s*:/);
});
