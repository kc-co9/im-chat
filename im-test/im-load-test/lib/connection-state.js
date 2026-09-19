const STABLE = 'stable';
const SERVICE_FAILED = 'service_failed';
const UNCERTAIN = 'uncertain';

export function normalizeConnectionLevels(levels, groupSize) {
  if (!Array.isArray(levels)
      || levels.some((level) => !Number.isInteger(level) || level <= 0)) {
    throw new Error('requested connection levels must be positive integers');
  }
  assertPositiveInteger(groupSize, 'groupSize');
  const requested = [...levels];
  const effective = [...new Set(requested
    .map((level) => Math.floor(level / groupSize) * groupSize)
    .filter((level) => level > 0))];
  if (effective.length === 0) {
    throw new Error('connection levels must contain at least one complete cohort');
  }
  return { requested, effective };
}

export function connectionSlot({
  cohortIndex,
  memberIndex,
  groupSize,
  roundStartMs,
  ratePerSecond,
  nowMs,
  lateSlotMs,
}) {
  assertNonNegativeInteger(cohortIndex, 'cohortIndex');
  assertNonNegativeInteger(memberIndex, 'memberIndex');
  assertPositiveInteger(groupSize, 'groupSize');
  if (memberIndex >= groupSize) throw new Error('memberIndex must be below groupSize');
  assertFiniteNumber(roundStartMs, 'roundStartMs');
  assertPositiveNumber(ratePerSecond, 'ratePerSecond');
  assertFiniteNumber(nowMs, 'nowMs');
  assertNonNegativeNumber(lateSlotMs, 'lateSlotMs');

  const ordinal = cohortIndex * groupSize + memberIndex;
  const targetAtMs = roundStartMs + (ordinal * 1_000) / ratePerSecond;
  const latenessMs = Math.max(0, nowMs - targetAtMs);
  return {
    ordinal,
    targetAtMs,
    waitMs: Math.max(0, targetAtMs - nowMs),
    latenessMs,
    skipped: latenessMs > lateSlotMs,
  };
}

export function createConnectionState({ expectedConnections } = {}) {
  assertPositiveInteger(expectedConnections, 'expectedConnections');
  const attempted = new Set();
  const opened = new Set();
  const online = new Set();
  const skipped = new Set();
  const handshakeFailures = new Map();
  const disconnected = new Set();
  const controlledCloses = new Set();
  const pendingRoutes = new Set();
  const cleanedRoutes = new Set();
  const scheduleDelaysMs = [];
  let measurementStarted = false;
  let measurementEnded = false;
  let measurementEndOnline;
  let routeCleanupFinished = false;

  const assertOrdinal = (ordinal) => {
    assertNonNegativeInteger(ordinal, 'ordinal');
    if (ordinal >= expectedConnections) throw new Error('ordinal exceeds expected connections');
  };
  const assertUnseen = (ordinal) => {
    if (attempted.has(ordinal) || skipped.has(ordinal)) {
      throw new Error(`connection ordinal ${ordinal} is already scheduled`);
    }
  };

  return Object.freeze({
    recordAttempt(ordinal, scheduleDelayMs = 0) {
      assertOrdinal(ordinal);
      assertNonNegativeNumber(scheduleDelayMs, 'scheduleDelayMs');
      assertUnseen(ordinal);
      attempted.add(ordinal);
      scheduleDelaysMs.push(scheduleDelayMs);
    },
    recordSkipped(ordinal, latenessMs) {
      assertOrdinal(ordinal);
      assertNonNegativeNumber(latenessMs, 'latenessMs');
      assertUnseen(ordinal);
      skipped.add(ordinal);
      scheduleDelaysMs.push(latenessMs);
    },
    recordOpened(ordinal) {
      assertOrdinal(ordinal);
      if (!attempted.has(ordinal)) throw new Error('connection must be attempted before opening');
      if (opened.has(ordinal) || handshakeFailures.has(ordinal)) {
        throw new Error(`connection ordinal ${ordinal} already has a handshake result`);
      }
      opened.add(ordinal);
      online.add(ordinal);
    },
    recordHandshakeFailure(ordinal, kind) {
      assertOrdinal(ordinal);
      if (!attempted.has(ordinal)) throw new Error('connection must be attempted before handshake failure');
      if (!['business', 'transport'].includes(kind)) {
        throw new Error('handshake failure kind must be business or transport');
      }
      if (opened.has(ordinal) || handshakeFailures.has(ordinal)) {
        throw new Error(`connection ordinal ${ordinal} already has a handshake result`);
      }
      handshakeFailures.set(ordinal, kind);
    },
    beginMeasurement() {
      if (measurementStarted) throw new Error('measurement already started');
      measurementStarted = true;
    },
    endMeasurement() {
      if (!measurementStarted) throw new Error('measurement must start before it ends');
      if (measurementEnded) throw new Error('measurement already ended');
      measurementEnded = true;
      measurementEndOnline = online.size;
    },
    recordDisconnected(ordinal) {
      assertOrdinal(ordinal);
      if (!opened.has(ordinal)) throw new Error('only an opened connection can disconnect');
      if (disconnected.has(ordinal) || controlledCloses.has(ordinal)) {
        throw new Error(`connection ordinal ${ordinal} is already closed`);
      }
      disconnected.add(ordinal);
      online.delete(ordinal);
    },
    recordControlledClose(ordinal) {
      assertOrdinal(ordinal);
      if (!opened.has(ordinal)) throw new Error('only an opened connection can close');
      if (disconnected.has(ordinal) || controlledCloses.has(ordinal)) {
        throw new Error(`connection ordinal ${ordinal} is already closed`);
      }
      controlledCloses.add(ordinal);
      pendingRoutes.add(ordinal);
      online.delete(ordinal);
    },
    recordRouteCleaned(ordinal) {
      assertOrdinal(ordinal);
      if (!controlledCloses.has(ordinal)) {
        throw new Error('only a controlled connection can report route cleanup');
      }
      pendingRoutes.delete(ordinal);
      cleanedRoutes.add(ordinal);
    },
    finishRouteCleanup() {
      if (routeCleanupFinished) throw new Error('route cleanup already finished');
      routeCleanupFinished = true;
    },
    snapshot() {
      const connectionSuccesses = opened.size;
      const unexpectedDisconnects = disconnected.size;
      return Object.freeze({
        plannedConnections: expectedConnections,
        attemptedConnections: attempted.size,
        skippedConnections: skipped.size,
        skipRate: skipped.size / expectedConnections,
        scheduleP95Ms: percentile(scheduleDelaysMs, 0.95),
        connectionSuccesses,
        connectionSuccessRate: connectionSuccesses / expectedConnections,
        businessHandshakeFailures: countValues(handshakeFailures, 'business'),
        transportHandshakeFailures: countValues(handshakeFailures, 'transport'),
        currentOnline: online.size,
        measurementEndOnline,
        onlineRate: measurementEnded ? measurementEndOnline / expectedConnections : undefined,
        unexpectedDisconnects,
        unexpectedDisconnectRate: connectionSuccesses === 0
          ? 0
          : unexpectedDisconnects / connectionSuccesses,
        controlledCloses: controlledCloses.size,
        cleanedRoutes: cleanedRoutes.size,
        routeCleanupTimeouts: routeCleanupFinished ? pendingRoutes.size : 0,
        measurementStarted,
        measurementEnded,
        routeCleanupFinished,
      });
    },
  });
}

export function classifyConnectionState(snapshot, { thresholds, pressureEvidence } = {}) {
  if (pressureEvidence?.complete !== true) {
    return result(UNCERTAIN, ['load-generator evidence is missing']);
  }
  if (!isPlainObject(snapshot) || !isPlainObject(thresholds)) {
    return result(UNCERTAIN, ['connection metrics or thresholds are missing']);
  }

  const uncertain = [];
  const failed = [];
  if (!snapshot.measurementEnded || !isFraction(snapshot.onlineRate)) {
    uncertain.push('measurement-end online evidence is missing');
  }
  if (!isFraction(snapshot.skipRate) || snapshot.skipRate > thresholds.skipRateMax) {
    uncertain.push('connection slot skip rate exceeds the validity threshold');
  }
  if (!isFiniteNonNegative(snapshot.scheduleP95Ms)
      || snapshot.scheduleP95Ms > thresholds.scheduleP95MsMax) {
    uncertain.push('connection schedule P95 exceeds the validity threshold');
  }
  if (snapshot.attemptedConnections + snapshot.skippedConnections
      !== snapshot.plannedConnections) {
    uncertain.push('connection scheduling evidence is incomplete');
  }
  if (snapshot.unexpectedDisconnects + snapshot.controlledCloses
      !== snapshot.connectionSuccesses) {
    uncertain.push('opened connection close classification is incomplete');
  }
  if (snapshot.routeCleanupFinished !== true) {
    uncertain.push('route cleanup evidence is incomplete');
  }

  if (!isFraction(snapshot.connectionSuccessRate)
      || snapshot.connectionSuccessRate < thresholds.connectionSuccessRateMin) {
    failed.push('connection success rate is below threshold');
  }
  if (isFraction(snapshot.onlineRate) && snapshot.onlineRate < thresholds.onlineRateMin) {
    failed.push('measurement-end online rate is below threshold');
  }
  if (!isFraction(snapshot.unexpectedDisconnectRate)
      || snapshot.unexpectedDisconnectRate > thresholds.unexpectedDisconnectRateMax) {
    failed.push('unexpected disconnect rate exceeds threshold');
  }
  if (!Number.isInteger(snapshot.routeCleanupTimeouts) || snapshot.routeCleanupTimeouts < 0) {
    uncertain.push('route cleanup evidence is invalid');
  } else if (snapshot.routeCleanupTimeouts > 0) {
    failed.push('route cleanup exceeded its deadline');
  }

  if (uncertain.length > 0) return result(UNCERTAIN, uncertain);
  if (failed.length > 0) return result(SERVICE_FAILED, failed);
  return result(STABLE, []);
}

export function classifyConnectionRun(run, { thresholds, pressureEvidence } = {}) {
  const summary = run?.summary;
  const evidence = run?.evidence;
  const metrics = summary?.metrics;
  if (summary?.boundary !== 'k6-connection' || evidence?.boundary !== 'mock-runtime'
      || !isPlainObject(metrics) || !Array.isArray(evidence.opens)
      || !Array.isArray(evidence.routeCleanups)) {
    return result(UNCERTAIN, ['connection run evidence is incomplete']);
  }

  const countNames = [
    'plannedConnections',
    'attemptedConnections',
    'skippedConnections',
    'connectionSuccesses',
    'measurementEndOnline',
    'unexpectedDisconnects',
    'controlledCloses',
  ];
  if (countNames.some((name) => !Number.isInteger(metrics[name]) || metrics[name] < 0)
      || !Number.isInteger(evidence.activeRoutes) || evidence.activeRoutes < 0) {
    return result(UNCERTAIN, ['connection run counters are invalid']);
  }
  const openOrdinals = evidence.opens.map(({ ordinal }) => ordinal);
  if (evidence.opens.length !== metrics.connectionSuccesses
      || new Set(openOrdinals).size !== openOrdinals.length
      || openOrdinals.some((ordinal) => !Number.isInteger(ordinal)
        || ordinal < 0 || ordinal >= metrics.plannedConnections)) {
    return result(UNCERTAIN, ['connection open ownership evidence is invalid']);
  }
  const openedOrdinals = new Set(openOrdinals);
  const cleanupOrdinals = new Set();
  let timedOutRoutes = 0;
  for (const cleanup of evidence.routeCleanups) {
    if (!Number.isInteger(cleanup?.ordinal) || !openedOrdinals.has(cleanup.ordinal)
        || cleanupOrdinals.has(cleanup.ordinal)
        || !['cleaned', 'timed_out'].includes(cleanup.outcome)) {
      return result(UNCERTAIN, ['connection route cleanup ownership evidence is invalid']);
    }
    cleanupOrdinals.add(cleanup.ordinal);
    if (cleanup.outcome === 'timed_out') timedOutRoutes += 1;
  }
  if (cleanupOrdinals.size !== metrics.connectionSuccesses
      || timedOutRoutes !== evidence.activeRoutes) {
    return result(UNCERTAIN, ['connection route cleanup evidence is incomplete']);
  }
  if (metrics.controlledCloses + metrics.unexpectedDisconnects !== metrics.connectionSuccesses) {
    return result(UNCERTAIN, ['connection close classification is incomplete']);
  }

  return classifyConnectionState({
    plannedConnections: metrics.plannedConnections,
    attemptedConnections: metrics.attemptedConnections,
    skippedConnections: metrics.skippedConnections,
    skipRate: metrics.skipRate,
    scheduleP95Ms: metrics.scheduleP95Ms,
    connectionSuccesses: metrics.connectionSuccesses,
    connectionSuccessRate: metrics.connectionSuccessRate,
    measurementEndOnline: metrics.measurementEndOnline,
    onlineRate: metrics.onlineRate,
    unexpectedDisconnects: metrics.unexpectedDisconnects,
    unexpectedDisconnectRate: metrics.unexpectedDisconnectRate,
    controlledCloses: metrics.controlledCloses,
    routeCleanupTimeouts: evidence.activeRoutes,
    measurementStarted: true,
    measurementEnded: true,
    routeCleanupFinished: true,
  }, { thresholds, pressureEvidence });
}

export function classifyConnectionRound({ summary, routeEvidence } = {}, {
  thresholds,
  pressureEvidence,
} = {}) {
  const metrics = summary?.metrics;
  const effectiveUsers = summary?.input?.effectiveUsers;
  if (summary?.boundary !== 'k6-connection' || !isPlainObject(metrics)
      || routeEvidence?.boundary !== 'connection-route-cleanup') {
    return result(UNCERTAIN, ['connection round evidence is incomplete']);
  }
  if (routeEvidence.complete !== true
      || !Number.isInteger(routeEvidence.userCount)
      || routeEvidence.userCount !== effectiveUsers
      || !Number.isInteger(routeEvidence.remainingRouteCount)
      || routeEvidence.remainingRouteCount < 0) {
    return result(UNCERTAIN, ['connection route cleanup evidence is incomplete']);
  }
  return classifyConnectionState({
    plannedConnections: metrics.plannedConnections,
    attemptedConnections: metrics.attemptedConnections,
    skippedConnections: metrics.skippedConnections,
    skipRate: metrics.skipRate,
    scheduleP95Ms: metrics.scheduleP95Ms,
    connectionSuccesses: metrics.connectionSuccesses,
    connectionSuccessRate: metrics.connectionSuccessRate,
    measurementEndOnline: metrics.measurementEndOnline,
    onlineRate: metrics.onlineRate,
    unexpectedDisconnects: metrics.unexpectedDisconnects,
    unexpectedDisconnectRate: metrics.unexpectedDisconnectRate,
    controlledCloses: metrics.controlledCloses,
    routeCleanupTimeouts: routeEvidence.remainingRouteCount,
    measurementStarted: true,
    measurementEnded: true,
    routeCleanupFinished: true,
  }, { thresholds, pressureEvidence });
}

function percentile(values, quantile) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.ceil(sorted.length * quantile) - 1];
}

function countValues(values, expected) {
  let count = 0;
  for (const value of values.values()) {
    if (value === expected) count += 1;
  }
  return count;
}

function result(status, reasons) {
  return { status, reasons };
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
}

function assertNonNegativeInteger(value, name) {
  if (!Number.isInteger(value) || value < 0) throw new Error(`${name} must be a nonnegative integer`);
}

function assertFiniteNumber(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${name} must be finite`);
}

function assertPositiveNumber(value, name) {
  assertFiniteNumber(value, name);
  if (value <= 0) throw new Error(`${name} must be positive`);
}

function assertNonNegativeNumber(value, name) {
  assertFiniteNumber(value, name);
  if (value < 0) throw new Error(`${name} must be nonnegative`);
}

function isFiniteNonNegative(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function isFraction(value) {
  return isFiniteNonNegative(value) && value <= 1;
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
