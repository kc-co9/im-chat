import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import { classifyRound } from '../lib/round-result.js';

const defaults = JSON.parse(readFileSync(
  new URL('../config/defaults.json', import.meta.url),
  'utf8',
));
const GiB = 1024 ** 3;

function generator(overrides = {}) {
  return {
    sampleSeconds: 5,
    cpuPercent: [30, 35, 40],
    rssBytes: [128 * 1024 ** 2, 130 * 1024 ** 2, 132 * 1024 ** 2],
    availableMemoryBytes: [2 * GiB, 2 * GiB, 2 * GiB],
    sendExceptions: { count: 0, origin: 'none' },
    ...overrides,
  };
}

function stableConnection(overrides = {}) {
  return {
    kind: 'connection',
    generator: generator(),
    metrics: {
      actualTargetRate: 1,
      skipRate: 0.01,
      scheduleP95Ms: 100,
      connectionSuccessRate: 0.995,
      onlineRate: 0.995,
      unexpectedDisconnectRate: 0.005,
      ...overrides,
    },
  };
}

function stableMessage(overrides = {}) {
  return {
    kind: 'message',
    generator: generator(),
    metrics: {
      actualTargetRate: 1,
      skipRate: 0.01,
      scheduleP95Ms: 100,
      sends: 1000,
      sendSuccesses: 999,
      expectedDeliveries: 1000,
      deliveryAckSuccesses: 999,
      receiveLatencyP95Ms: 500,
      receiveLatencyP99Ms: 2000,
      unknownReceivers: 0,
      duplicates: 1,
      ackFailures: 0,
      minuteBacklog: [3, 2, 2],
      drainResidual: 1,
      postDrainExcess: 0,
      trackingOverflows: 0,
      forcedStops: 0,
      ...overrides,
    },
  };
}

function classify(round) {
  return classifyRound(round, defaults.thresholds);
}

function thresholds(overrides = {}) {
  return { ...defaults.thresholds, ...overrides };
}

test('classifies exact connection and message boundaries as stable', () => {
  assert.deepEqual(classify(stableConnection()), { status: 'stable', reasons: [] });
  assert.deepEqual(classify(stableMessage()), { status: 'stable', reasons: [] });
});

test('classifies missing, non-finite, or impossible thresholds as uncertain', () => {
  const missing = thresholds();
  delete missing.sendSuccessRateMin;
  const malformed = [
    missing,
    thresholds({ scheduleP95MsMax: Number.NaN }),
    thresholds({ lateSlotMs: -1 }),
    thresholds({ skipRateMax: 1.01 }),
    thresholds({ actualTargetRateMin: 1.03, actualTargetRateMax: 1.02 }),
    thresholds({ receiveLatencyP95Ms: 2001, receiveLatencyP99Ms: 2000 }),
    thresholds({ generatorCpuPercentMin: 101 }),
    thresholds({ hostMemoryConsecutiveSamples: 0 }),
  ];

  for (const value of malformed) {
    assert.equal(classifyRound(stableMessage(), value).status, 'uncertain');
  }
});

test('classifies malformed or impossible metrics as uncertain', () => {
  const malformed = [
    { actualTargetRate: Number.NaN },
    { scheduleP95Ms: -1 },
    { receiveLatencyP95Ms: -1 },
    { receiveLatencyP95Ms: 1000, receiveLatencyP99Ms: 999 },
    { sends: -1 },
    { sendSuccesses: 1001 },
    { deliveryAckSuccesses: 1001 },
    { ackFailures: -1 },
    { drainResidual: 1001 },
    { postDrainExcess: 1001 },
    { trackingOverflows: -1 },
    { minuteBacklog: [3, Number.NaN, 1] },
    { minuteBacklog: [3, -1, 1] },
    { minuteBacklog: [3, 1001, 1] },
  ];

  for (const metrics of malformed) {
    assert.equal(
      classify(stableMessage(metrics)).status,
      'uncertain',
      `expected uncertain for ${JSON.stringify(metrics)}`,
    );
  }
  assert.equal(classify(stableConnection({ connectionSuccessRate: 1.01 })).status, 'uncertain');
  assert.equal(classify({
    ...stableConnection(),
    generator: generator({ cpuPercent: [30, Number.NaN, 40] }),
  }).status, 'uncertain');
  assert.equal(classify({
    ...stableConnection(),
    generator: generator({ rssBytes: [100, -1, 100] }),
  }).status, 'uncertain');
});

test('classifies generator evidence missing or saturated as uncertain', () => {
  assert.equal(classify({ ...stableConnection(), generator: undefined }).status, 'uncertain');
  for (const field of ['cpuPercent', 'rssBytes', 'availableMemoryBytes', 'sendExceptions']) {
    const evidence = generator();
    delete evidence[field];
    assert.equal(
      classify({ ...stableConnection(), generator: evidence }).status,
      'uncertain',
      `${field} must be required generator evidence`,
    );
  }
  assert.equal(classify({
    ...stableConnection(),
    generator: generator({ availableMemoryBytes: [GiB - 1, GiB - 1, GiB - 1] }),
  }).status, 'uncertain');
  assert.equal(classify({
    ...stableMessage({ actualTargetRate: 0.97 }),
    generator: generator({ cpuPercent: [90, 91, 92] }),
  }).status, 'uncertain');
  assert.equal(classify({
    ...stableMessage({ actualTargetRate: 0.97 }),
    generator: generator({ cpuPercent: [90, 91, 92], sampleSeconds: 10 }),
  }).status, 'uncertain');
});

test('separates service send failures from local generator exceptions', () => {
  assert.equal(classify({
    ...stableMessage(),
    generator: generator({ sendExceptions: { count: 1, origin: 'service_send' } }),
  }).status, 'service_failed');
  assert.equal(classify({
    ...stableMessage(),
    generator: generator({ sendExceptions: { count: 1, origin: 'generator_local' } }),
  }).status, 'uncertain');
  assert.equal(classify({
    ...stableMessage({ actualTargetRate: 0.97 }),
    generator: generator({
      cpuPercent: [90, 91, 92],
      sendExceptions: { count: 1, origin: 'service_send' },
    }),
  }).status, 'uncertain');
});

test('classifies invalid scheduling and insufficient message evidence as uncertain', () => {
  for (const metrics of [
    { actualTargetRate: 0.979 },
    { actualTargetRate: 1.021 },
    { skipRate: 0.011 },
    { scheduleP95Ms: 101 },
    { sends: 999 },
    { minuteBacklog: [1, 2, 3] },
    { drainResidual: 2 },
    { postDrainExcess: 1 },
    { trackingOverflows: 1 },
  ]) {
    assert.equal(classify(stableMessage(metrics)).status, 'uncertain');
  }

  assert.equal(classify(stableMessage({ minuteBacklog: [3, 3, 4] })).status, 'stable');
  assert.equal(classify(stableMessage({ minuteBacklog: [3, 4, 3] })).status, 'stable');
});

test('classifies a forced websocket shutdown as a service failure', () => {
  assert.equal(classify(stableMessage({ forcedStops: 1 })).status, 'service_failed');
});

test('classifies measured connection threshold violations as service_failed', () => {
  for (const metrics of [
    { connectionSuccessRate: 0.994 },
    { onlineRate: 0.994 },
    { unexpectedDisconnectRate: 0.006 },
  ]) {
    assert.equal(classify(stableConnection(metrics)).status, 'service_failed');
  }
});

test('classifies measured message threshold violations as service_failed', () => {
  for (const metrics of [
    { sendSuccesses: 998 },
    { deliveryAckSuccesses: 998 },
    { receiveLatencyP95Ms: 501 },
    { receiveLatencyP99Ms: 2001 },
    { unknownReceivers: 1 },
    { duplicates: 2 },
    { ackFailures: 1 },
  ]) {
    assert.equal(classify(stableMessage(metrics)).status, 'service_failed');
  }
});

test('classifies extreme duplicate and unknown receiver observations as service failures', () => {
  const duplicateResult = classify(stableMessage({ duplicates: 1_000_000 }));
  const unknownResult = classify(stableMessage({ unknownReceivers: 1_000_000 }));

  assert.equal(duplicateResult.status, 'service_failed');
  assert.ok(duplicateResult.reasons.some((reason) => reason.includes('duplicate')));
  assert.equal(unknownResult.status, 'service_failed');
  assert.ok(unknownResult.reasons.some((reason) => reason.includes('unknown receiver')));
});

test('duplicates never compensate for missing unique delivery and ACK success', () => {
  const result = classify(stableMessage({
    deliveryAckSuccesses: 998,
    duplicates: 1,
  }));

  assert.equal(result.status, 'service_failed');
  assert.ok(result.reasons.some((reason) => reason.includes('delivery')));
});

test('known send or delivery failures are not hidden by drain residual uncertainty', () => {
  assert.equal(classify(stableMessage({
    sendSuccesses: 998,
    deliveryAckSuccesses: 998,
    drainResidual: 2,
  })).status, 'service_failed');
  assert.equal(classify(stableMessage({
    deliveryAckSuccesses: 998,
    drainResidual: 2,
  })).status, 'service_failed');
});

test('known message threshold failures are not hidden by a growing backlog', () => {
  const result = classify(stableMessage({
    sendSuccesses: 998,
    minuteBacklog: [1, 2, 3],
  }));

  assert.equal(result.status, 'service_failed');
  assert.ok(result.reasons.some((reason) => reason.includes('send business')));
});

test('unknown round kinds and incomplete service metrics are uncertain', () => {
  assert.equal(classify({ ...stableConnection(), kind: 'other' }).status, 'uncertain');
  const round = stableMessage();
  delete round.metrics.unknownReceivers;
  assert.equal(classify(round).status, 'uncertain');
});
