import assert from 'node:assert/strict';
import test from 'node:test';

import {
  classifyConnectionRound,
  classifyConnectionRun,
  classifyConnectionState,
  connectionSlot,
  createConnectionState,
  normalizeConnectionLevels,
} from '../lib/connection-state.js';

const thresholds = Object.freeze({
  connectionSuccessRateMin: 0.995,
  onlineRateMin: 0.995,
  unexpectedDisconnectRateMax: 0.005,
  skipRateMax: 0.01,
  scheduleP95MsMax: 100,
});
const completePressureEvidence = Object.freeze({ complete: true });

test('connection levels preserve requested values and deduplicate full-cohort effective values', () => {
  assert.deepEqual(normalizeConnectionLevels([20, 53, 57, 9], 10), {
    requested: [20, 53, 57, 9],
    effective: [20, 50],
  });
  assert.throws(() => normalizeConnectionLevels([9], 10), /complete cohort/i);
});

test('connection slots use one global twenty-per-second ordinal schedule', () => {
  assert.deepEqual(connectionSlot({
    cohortIndex: 1,
    memberIndex: 3,
    groupSize: 10,
    roundStartMs: 1_000,
    ratePerSecond: 20,
    nowMs: 1_600,
    lateSlotMs: 200,
  }), {
    ordinal: 13,
    targetAtMs: 1_650,
    waitMs: 50,
    latenessMs: 0,
    skipped: false,
  });
});

test('connection slots skip arrivals later than 200ms without a catch-up delay', () => {
  assert.deepEqual(connectionSlot({
    cohortIndex: 1,
    memberIndex: 3,
    groupSize: 10,
    roundStartMs: 1_000,
    ratePerSecond: 20,
    nowMs: 1_851,
    lateSlotMs: 200,
  }), {
    ordinal: 13,
    targetAtMs: 1_650,
    waitMs: 0,
    latenessMs: 201,
    skipped: true,
  });
});

test('a late skipped connection slot makes the round uncertain', () => {
  const state = createConnectionState({ expectedConnections: 2 });
  state.recordAttempt(0, 25);
  state.recordOpened(0);
  state.recordSkipped(1, 201);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  const result = classifyConnectionState(state.snapshot(), {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(state.snapshot().skipRate, 0.5);
  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /skip rate/i);
});

test('business handshake rejection lowers connection success and fails the service round', () => {
  const state = createConnectionState({ expectedConnections: 2 });
  state.recordAttempt(0);
  state.recordOpened(0);
  state.recordAttempt(1);
  state.recordHandshakeFailure(1, 'business');
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  const result = classifyConnectionState(state.snapshot(), {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(state.snapshot().businessHandshakeFailures, 1);
  assert.equal(state.snapshot().connectionSuccessRate, 0.5);
  assert.equal(result.status, 'service_failed');
  assert.match(result.reasons.join('\n'), /connection success/i);
});

test('an unexpected disconnect is counted and fails the service round', () => {
  const state = connectedState(2);
  state.beginMeasurement();
  state.recordDisconnected(1);
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  const snapshot = state.snapshot();
  const result = classifyConnectionState(snapshot, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(snapshot.unexpectedDisconnects, 1);
  assert.equal(snapshot.unexpectedDisconnectRate, 0.5);
  assert.equal(result.status, 'service_failed');
  assert.match(result.reasons.join('\n'), /unexpected disconnect/i);
});

test('measurement-end online ratio is frozen before controlled shutdown', () => {
  const state = connectedState(2);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordControlledClose(1);

  const snapshot = state.snapshot();

  assert.equal(snapshot.measurementEndOnline, 2);
  assert.equal(snapshot.onlineRate, 1);
  assert.equal(snapshot.currentOnline, 0);
});

test('controlled shutdown is not counted as an unexpected disconnect', () => {
  const state = connectedState(2);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordControlledClose(1);
  state.recordRouteCleaned(0);
  state.recordRouteCleaned(1);
  state.finishRouteCleanup();

  const snapshot = state.snapshot();
  const result = classifyConnectionState(snapshot, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(snapshot.controlledCloses, 2);
  assert.equal(snapshot.unexpectedDisconnects, 0);
  assert.equal(result.status, 'stable');
});

test('route cleanup deadline reports remaining controlled routes as service failure', () => {
  const state = connectedState(2);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordControlledClose(1);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  const snapshot = state.snapshot();
  const result = classifyConnectionState(snapshot, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(snapshot.routeCleanupTimeouts, 1);
  assert.equal(result.status, 'service_failed');
  assert.match(result.reasons.join('\n'), /route cleanup/i);
});

test('round completion requires every opened connection to have a close classification', () => {
  const state = connectedState(2);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  const result = classifyConnectionState(state.snapshot(), {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /close classification.*incomplete/i);
});

test('unfinished route cleanup evidence cannot classify a round as stable', () => {
  const state = connectedState(1);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);

  const result = classifyConnectionState(state.snapshot(), {
    thresholds,
    pressureEvidence: completePressureEvidence,
  });

  assert.equal(result.status, 'uncertain');
  assert.match(result.reasons.join('\n'), /route cleanup.*incomplete/i);
});

test('missing load-generator evidence makes an otherwise stable round uncertain', () => {
  const state = connectedState(1);
  state.beginMeasurement();
  state.endMeasurement();
  state.recordControlledClose(0);
  state.recordRouteCleaned(0);
  state.finishRouteCleanup();

  assert.deepEqual(classifyConnectionState(state.snapshot(), {
    thresholds,
  }), {
    status: 'uncertain',
    reasons: ['load-generator evidence is missing'],
  });
});

test('connection run classification combines k6 counters with route cleanup evidence', () => {
  const stable = connectionRun();
  assert.equal(classifyConnectionRun(stable, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'stable');

  const disconnected = structuredClone(stable);
  disconnected.summary.metrics.unexpectedDisconnects = 1;
  disconnected.summary.metrics.unexpectedDisconnectRate = 0.5;
  disconnected.summary.metrics.controlledCloses = 1;
  assert.equal(classifyConnectionRun(disconnected, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'service_failed');

  const leakedRoute = structuredClone(stable);
  leakedRoute.evidence.activeRoutes = 1;
  leakedRoute.evidence.routeCleanups[0].outcome = 'timed_out';
  assert.equal(classifyConnectionRun(leakedRoute, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'service_failed');

  const incomplete = structuredClone(stable);
  incomplete.summary.metrics.controlledCloses = 1;
  assert.equal(classifyConnectionRun(incomplete, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'uncertain');

  for (const invalidCleanups of [
    [],
    [{ ordinal: 0, outcome: 'cleaned' }, { ordinal: 0, outcome: 'cleaned' }],
    [{ ordinal: 0, outcome: 'cleaned' }, { ordinal: 3, outcome: 'cleaned' }],
  ]) {
    const invalid = structuredClone(stable);
    invalid.evidence.routeCleanups = invalidCleanups;
    assert.equal(classifyConnectionRun(invalid, {
      thresholds,
      pressureEvidence: completePressureEvidence,
    }).status, 'uncertain');
  }
});

test('real connection round classifies complete all-user route cleanup without mock ordinals', () => {
  const summary = structuredClone(connectionRun().summary);
  summary.input = { effectiveUsers: 2 };
  const routeEvidence = {
    boundary: 'connection-route-cleanup',
    userCount: 2,
    remainingRouteCount: 0,
    complete: true,
  };
  assert.equal(classifyConnectionRound({ summary, routeEvidence }, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'stable');

  routeEvidence.remainingRouteCount = 1;
  assert.equal(classifyConnectionRound({ summary, routeEvidence }, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'service_failed');

  routeEvidence.complete = false;
  assert.equal(classifyConnectionRound({ summary, routeEvidence }, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'uncertain');

  routeEvidence.complete = true;
  routeEvidence.userCount = 1;
  assert.equal(classifyConnectionRound({ summary, routeEvidence }, {
    thresholds,
    pressureEvidence: completePressureEvidence,
  }).status, 'uncertain');
});

function connectionRun() {
  return {
    summary: {
      boundary: 'k6-connection',
      metrics: {
        plannedConnections: 2,
        attemptedConnections: 2,
        skippedConnections: 0,
        skipRate: 0,
        scheduleP95Ms: 1,
        connectionSuccesses: 2,
        connectionSuccessRate: 1,
        measurementEndOnline: 2,
        onlineRate: 1,
        unexpectedDisconnects: 0,
        unexpectedDisconnectRate: 0,
        controlledCloses: 2,
      },
    },
    evidence: {
      boundary: 'mock-runtime',
      activeRoutes: 0,
      opens: [{ ordinal: 0 }, { ordinal: 1 }],
      routeCleanups: [{ ordinal: 0, outcome: 'cleaned' }, { ordinal: 1, outcome: 'cleaned' }],
    },
  };
}

function connectedState(expectedConnections) {
  const state = createConnectionState({ expectedConnections });
  for (let ordinal = 0; ordinal < expectedConnections; ordinal += 1) {
    state.recordAttempt(ordinal);
    state.recordOpened(ordinal);
  }
  return state;
}
