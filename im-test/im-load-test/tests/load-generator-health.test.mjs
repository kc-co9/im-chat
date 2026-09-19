import assert from 'node:assert/strict';
import test from 'node:test';

import { classifyMessageRun, evaluateLoadGenerator } from '../lib/load-generator-health.js';

const thresholds = Object.freeze({
  sampleSeconds: 5,
  actualTargetRateMin: 0.98,
  actualTargetRateMax: 1.02,
  hostAvailableMemoryBytesMin: 1_073_741_824,
  hostMemoryConsecutiveSamples: 3,
  generatorCpuPercentMin: 90,
  generatorCpuConsecutiveSamples: 3,
});

test('complete five-second resource samples classify a healthy generator as stable', () => {
  assert.deepEqual(evaluateLoadGenerator({
    samples: samples([30, 35, 40], [2e9, 2e9, 2e9]),
    actualTargetRate: 1,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  }), {
    status: 'stable',
    reasons: [],
    evidence: { complete: true, sampleSeconds: 5, sampleCount: 3 },
  });
});

test('accepts bounded sampler scheduling jitter around five seconds', () => {
  const jittered = samples([30, 35, 40], [2e9, 2e9, 2e9])
    .map((sample, index) => ({ ...sample, atMs: [0, 5_000, 11_000][index] }));
  assert.equal(evaluateLoadGenerator({
    samples: jittered,
    actualTargetRate: 1,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  }).status, 'stable');
});

test('accepts isolated sampler collection overruns when the median cadence remains five seconds', () => {
  const jittered = samples([30, 35, 40, 45, 50], [2e9, 2e9, 2e9, 2e9, 2e9])
    .map((sample, index) => ({
      ...sample,
      atMs: [0, 5_000, 11_361, 15_192, 20_192][index],
    }));
  assert.equal(evaluateLoadGenerator({
    samples: jittered,
    actualTargetRate: 1,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  }).status, 'stable');
});

test('missing, non-finite, or wrong-interval samples are uncertain', () => {
  for (const fixture of [
    samples([30, 30], [2e9, 2e9]),
    [{ atMs: 0, cpuPercent: Number.NaN, rssBytes: 1, availableMemoryBytes: 2e9 }, ...samples([30, 30], [2e9, 2e9]).slice(1)],
    samples([30, 30, 30], [2e9, 2e9, 2e9]).map((sample, index) => ({ ...sample, atMs: index * 3_500 })),
    samples([30, 30, 30], [2e9, 2e9, 2e9]).map((sample, index) => ({ ...sample, atMs: index * 7_000 })),
    samples([30, 30, 30, 30], [2e9, 2e9, 2e9, 2e9])
      .map((sample, index) => ({ ...sample, atMs: [0, 5_000, 12_501, 17_501][index] })),
  ]) {
    assert.equal(evaluateLoadGenerator({
      samples: fixture,
      actualTargetRate: 1,
      sendExceptions: { count: 0, origin: 'none' },
      thresholds,
    }).status, 'uncertain');
  }
});

test('known message threshold failures remain service failures when sampler cadence is uncertain', () => {
  const failed = messageSummary();
  failed.metrics.sends = 1_000;
  failed.metrics.sendSuccesses = 999;
  failed.metrics.expectedDeliveries = 3_400;
  failed.metrics.deliveryAckSuccesses = 3_391;
  const result = classifyMessageRun({
    summary: failed,
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9])
      .map((sample, index) => ({ ...sample, atMs: index * 7_000 })),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  });
  assert.equal(result.generatorHealth.status, 'uncertain');
  assert.equal(result.decision.status, 'service_failed');
  assert.match(result.decision.reasons.join('\n'), /delivery and ACK success rate/i);
});

test('target-rate deviation is uncertain even before resource saturation', () => {
  const result = evaluateLoadGenerator({
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9]),
    actualTargetRate: 0.97,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  });
  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /target rate/i);
});

test('three consecutive high CPU samples with low rate mark generator saturation', () => {
  const result = evaluateLoadGenerator({
    samples: samples([90, 95, 91], [2e9, 2e9, 2e9]),
    actualTargetRate: 0.97,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  });
  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /CPU.*saturat/i);
});

test('three consecutive low-memory samples mark host saturation', () => {
  const result = evaluateLoadGenerator({
    samples: samples([30, 30, 30], [1e9, 1e9, 1e9]),
    actualTargetRate: 1,
    sendExceptions: { count: 0, origin: 'none' },
    thresholds,
  });
  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /memory.*saturat/i);
});

test('service send exceptions fail the service while local exceptions are uncertain', () => {
  const input = {
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9]),
    actualTargetRate: 1,
    thresholds,
  };
  assert.equal(evaluateLoadGenerator({
    ...input, sendExceptions: { count: 1, origin: 'service_send' },
  }).status, 'service_failed');
  assert.equal(evaluateLoadGenerator({
    ...input, sendExceptions: { count: 1, origin: 'generator' },
  }).status, 'uncertain');
});

test('message run combines k6 correctness and generator evidence into three-state decisions', () => {
  const stable = messageSummary();
  assert.equal(classifyMessageRun({
    summary: stable,
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9]),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  }).decision.status, 'stable');

  const duplicate = structuredClone(stable);
  duplicate.metrics.duplicates = 1;
  assert.equal(classifyMessageRun({
    summary: duplicate,
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9]),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  }).decision.status, 'service_failed');

  const residual = structuredClone(stable);
  residual.metrics.deliveryAckSuccesses = 67;
  residual.metrics.drainResidual = 1;
  assert.equal(classifyMessageRun({
    summary: residual,
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9]),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  }).decision.status, 'service_failed');

  assert.equal(classifyMessageRun({
    summary: stable,
    samples: samples([30, 30, 30], [1e9, 1e9, 1e9]),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  }).decision.status, 'uncertain');

  assert.equal(classifyMessageRun({
    summary: stable,
    samples: samples([30, 30, 30], [2e9, 2e9, 2e9])
      .map((sample, index) => ({ ...sample, atMs: index * 3_500 })),
    sendExceptionOrigin: 'none',
    thresholds: roundThresholds(),
  }).decision.status, 'uncertain');
});

function samples(cpu, memory) {
  return cpu.map((cpuPercent, index) => ({
    atMs: index * 5_000,
    cpuPercent,
    rssBytes: 100_000_000 + index,
    availableMemoryBytes: memory[index],
  }));
}

function messageSummary() {
  return {
    boundary: 'k6-message',
    metrics: {
      sends: 20,
      sendSuccesses: 20,
      expectedDeliveries: 68,
      deliveryAckSuccesses: 68,
      unknownReceivers: 0,
      duplicates: 0,
      ackFailures: 0,
      drainResidual: 0,
      postDrainExcess: 0,
      actualTargetRate: 1,
      skipRate: 0,
      scheduleP95Ms: 1,
      receiveLatencyP95Ms: 10,
      receiveLatencyP99Ms: 20,
      minuteBacklog: [0, 0, 0],
      sendExceptions: 0,
      trackingOverflows: 0,
      forcedStops: 0,
    },
  };
}

function roundThresholds() {
  return {
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
    minimumSends: 20,
    drainResidualRateMax: 0.001,
    hostAvailableMemoryBytesMin: 1_073_741_824,
    hostMemoryConsecutiveSamples: 3,
    generatorCpuPercentMin: 90,
    generatorCpuConsecutiveSamples: 3,
    generatorSampleSeconds: 5,
  };
}
