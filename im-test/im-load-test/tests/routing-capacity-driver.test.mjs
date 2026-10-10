import test from 'node:test';
import assert from 'node:assert/strict';
import { applicable, classify, verifyEvidence, resourceValue } from '../tools/routing-capacity-driver.mjs';

test('capacity driver uses a fixed applicability matrix', () => {
  assert.equal(applicable('businessSuccessRate', 'steady-state', 'connection'), false);
  assert.equal(applicable('migrationP95Ms', 'gateway-switch', 'message'), true);
  assert.equal(applicable('migrationP95Ms', 'steady-state', 'message'), false);
  assert.equal(applicable('electionRecoveryMs', 'leader-route-broker-failure', 'message'), true);
  assert.equal(applicable('electionRecoveryMs', 'broker-drain', 'message'), false);
});

test('capacity driver never accepts measurement-end connection loss', () => {
  const thresholds = {
    duplicateRateMax: 0.001, misrouteCountMax: 0, oldEpochAdmissionCountMax: 0,
    routeLookupP95MsMax: 300, routeLookupP99MsMax: 2000, fullLookupRateMax: 0.001,
    snapshotAckRateMin: 1, staleRouteCountMax: 0, cpuLimitPercentMax: 70,
    rssLimitPercentMax: 70, diskUsagePercentMax: 70, connectionSuccessRateMin: 0.995,
  };
  assert.equal(classify({
    connectionSuccessRate: 0.9, duplicateRate: 0, misrouteCount: 0,
    oldEpochAdmissionCount: 0, routeLookupP95Ms: 1, routeLookupP99Ms: 1,
    fullLookupRate: 0, snapshotAckRate: 1, staleRouteCount: 0, cpuMaxPercent: 1,
    rssLimitPercent: 1, diskUsagePercent: 1, generatorSaturated: false,
    operationOutcome: 'NOT_APPLICABLE',
  }, thresholds, 'connection', 'steady-state'), 'service_failed');
});

test('missing raw online counter cannot be presented as zero', () => {
  assert.throws(() => verifyEvidence({
    metrics: {}, observations: {}, phase: 'steady-state', workload: 'connection',
    window: { startedAt: 1, finishedAt: 2 },
    rawSummary: { metrics: {
      im_connection_planned: { values: { count: 100 } },
      im_connection_attempted: { values: { count: 100 } },
      im_connection_opened: { values: { count: 100 } },
    } },
  }), /im_connection_measurement_online/);
});

test('resources require every node and reject a sampling gap', () => {
  const window = { startedAt: 0, finishedAt: 60_000 };
  const samples = [0, 15_000, 30_000, 45_000, 60_000]
    .map((atMs) => ({ nodeId: 'broker-a', atMs, value: 50 }));
  assert.equal(resourceValue('cpuMaxPercent', samples, ['broker-a'], window), 50);
  assert.throws(() => resourceValue('cpuMaxPercent', samples, ['broker-a', 'broker-b'], window), /coverage/);
  assert.throws(() => resourceValue('cpuMaxPercent', [samples[0], samples.at(-1)], ['broker-a'], window), /interval/);
});
