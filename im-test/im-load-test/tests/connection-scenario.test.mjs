import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const scenarioUrl = new URL('../scenarios/connection-capacity.js', import.meta.url);
const runnerUrl = new URL('../../../scripts/test-load-test-k6-scenarios.sh', import.meta.url);

test('connection scenario uses event websockets and an immutable cohort assignment', async () => {
  const source = await readFile(scenarioUrl, 'utf8');

  assert.match(source, /from ['"]k6\/websockets['"]/);
  assert.match(source, /from ['"]k6\/data['"]/);
  assert.match(source, /new SharedArray\(/);
  assert.match(source, /executor:\s*['"]per-vu-iterations['"]/);
  assert.match(source, /__VU\s*-\s*1/);
  assert.match(source, /cohortIndex\s*\*\s*GROUP_SIZE\s*\+\s*memberIndex/);
  assert.match(source, /BATCH_ID/);
  assert.match(source, /ROUND_ID/);
  assert.match(source, /return \{ batch: BATCH_ID, round: ROUND_ID, phase \}/);
  assert.match(source, /handshakeState/);
  assert.match(source, /closeRequested/);
  assert.match(source, /controlledCloseInitiated/);
  assert.match(source, /SOCKET_OPEN\s*=\s*1/);
  assert.doesNotMatch(source, /WebSocket\.OPEN/);
  assert.doesNotMatch(source, /bufferedAmount/);
  assert.doesNotMatch(source, /\.send\s*\(/);
});

test('connection scenario writes a bounded machine summary without credentials', async () => {
  const source = await readFile(scenarioUrl, 'utf8');

  assert.match(source, /export function handleSummary\s*\(/);
  assert.match(source, /SUMMARY_PATH/);
  assert.match(source, /connectionSuccessRate/);
  assert.match(source, /unexpectedDisconnectRate/);
  assert.doesNotMatch(source, /accessToken\s*:/);
  assert.doesNotMatch(source, /refreshToken\s*:/);
});

test('connection fixture uses the login barrier and real k6 for 20 and 50 users', async () => {
  const source = await readFile(runnerUrl, 'utf8');

  assert.match(source, /set -euo pipefail/);
  assert.match(source, /trap .*EXIT/);
  assert.match(source, /tools\/data\.mjs/);
  assert.match(source, /^\s*login-round \\$/m);
  assert.match(source, /20 50/);
  assert.match(source, /connection-capacity\.js/);
  assert.match(source, /K6_BIN/);
  assert.match(source, /timeout|deadline/i);
  assert.match(source, /activeRoutes/);
  assert.match(source, /terminate_process\s*\(\)/);
  assert.match(source, /kill -KILL/);
});
