import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { buildRoundReport, parseResourceSamples } from '../tools/round-report.mjs';

const thresholds = {
  lateSlotMs: 200,
  actualTargetRateMin: 0.98,
  actualTargetRateMax: 1.02,
  skipRateMax: 0.01,
  scheduleP95MsMax: 100,
  connectionSuccessRateMin: 0.995,
  onlineRateMin: 0.995,
  unexpectedDisconnectRateMax: 0.005,
  sendSuccessRateMin: 0.999,
  deliveryAckSuccessRateMin: 0.999,
  receiveLatencyP95Ms: 500,
  receiveLatencyP99Ms: 2_000,
  unknownReceiverMax: 0,
  duplicateRateMax: 0.001,
  minimumSends: 1_000,
  drainResidualRateMax: 0.001,
  hostAvailableMemoryBytesMin: 1_073_741_824,
  hostMemoryConsecutiveSamples: 3,
  generatorCpuPercentMin: 90,
  generatorCpuConsecutiveSamples: 3,
  generatorSampleSeconds: 5,
};

test('builds stable real connection report from k6, resources, and all-user route cleanup', () => {
  const report = buildRoundReport({
    kind: 'connection',
    summary: connectionSummary(),
    resourceSamples: completeSamples(),
    routeEvidence: {
      boundary: 'connection-route-cleanup',
      userCount: 20,
      remainingRouteCount: 0,
      complete: true,
    },
    thresholds,
  });
  assert.equal(report.boundary, 'load-test-round');
  assert.equal(report.decision.status, 'stable');
  assert.equal(report.kind, 'connection');
});

test('classifies remaining real routes as service failure', () => {
  const report = buildRoundReport({
    kind: 'connection',
    summary: connectionSummary(),
    resourceSamples: completeSamples(),
    routeEvidence: {
      boundary: 'connection-route-cleanup',
      userCount: 20,
      remainingRouteCount: 1,
      complete: true,
    },
    thresholds,
  });
  assert.equal(report.decision.status, 'service_failed');
});

test('builds stable message report and marks invalid generator samples uncertain', () => {
  const stable = buildRoundReport({
    kind: 'message',
    summary: messageSummary(),
    resourceSamples: completeSamples(),
    thresholds,
  });
  assert.equal(stable.decision.status, 'stable');

  const uncertain = buildRoundReport({
    kind: 'message',
    summary: messageSummary(),
    resourceSamples: [{ ...completeSamples()[0], status: 'unknown' }],
    thresholds,
  });
  assert.equal(uncertain.decision.status, 'uncertain');
});

test('parses sampler NDJSON without retaining container payloads', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'round-report-'));
  const file = join(directory, 'resources.jsonl');
  await writeFile(file, completeSamples().map((sample) => JSON.stringify({
    timestamp: sample.atMs / 1_000,
    status: sample.status,
    k6CpuPercent: sample.cpuPercent,
    k6RssBytes: sample.rssBytes,
    availableMemoryBytes: sample.availableMemoryBytes,
    docker: [{ Name: 'fixture', CPUPerc: '1%' }],
  })).join('\n') + '\n');
  const parsed = await parseResourceSamples(file);
  assert.deepEqual(parsed, completeSamples());
  assert.doesNotMatch(JSON.stringify(parsed), /fixture|CPUPerc/);
  assert.doesNotMatch(await readFile(file, 'utf8'), /accessToken/);
});

function completeSamples() {
  return [0, 1, 2].map((index) => ({
    atMs: 1_000_000 + index * 5_000,
    status: 'complete',
    cpuPercent: 20,
    rssBytes: 100_000_000,
    availableMemoryBytes: 4_000_000_000,
  }));
}

function connectionSummary() {
  return {
    boundary: 'k6-connection',
    input: { batchId: 'abc123', roundId: 'connection-1-20', effectiveUsers: 20 },
    metrics: {
      plannedConnections: 20,
      attemptedConnections: 20,
      skippedConnections: 0,
      skipRate: 0,
      scheduleP95Ms: 10,
      connectionSuccesses: 20,
      connectionSuccessRate: 1,
      measurementEndOnline: 20,
      onlineRate: 1,
      unexpectedDisconnects: 0,
      unexpectedDisconnectRate: 0,
      controlledCloses: 20,
    },
  };
}

function messageSummary() {
  return {
    boundary: 'k6-message',
    input: { batchId: 'abc123', roundId: 'message-1-50', effectiveUsers: 20 },
    metrics: {
      sends: 1_000,
      sendSuccesses: 1_000,
      expectedDeliveries: 3_400,
      deliveryAckSuccesses: 3_400,
      unknownReceivers: 0,
      duplicates: 0,
      duplicateAcks: 0,
      ackFailures: 0,
      drainResidual: 0,
      postDrainExcess: 0,
      skipped: 0,
      skipRate: 0,
      actualTargetRate: 1,
      scheduleP95Ms: 10,
      receiveLatencyP95Ms: 100,
      receiveLatencyP99Ms: 200,
      minuteBacklog: [0, 0, 0],
      sendExceptions: 0,
      connectionBarrierFailures: 0,
      trackingOverflows: 0,
      forcedStops: 0,
    },
  };
}
