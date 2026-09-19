import { buildCorrelation } from './correlation.js';

export function messageOrdinals({ cohortIndex, cohortCount, totalMessages }) {
  assertNonNegativeInteger(cohortIndex, 'cohortIndex');
  assertPositiveInteger(cohortCount, 'cohortCount');
  assertNonNegativeInteger(totalMessages, 'totalMessages');
  if (cohortIndex >= cohortCount) throw new Error('cohortIndex must be below cohortCount');
  const ordinals = [];
  for (let ordinal = cohortIndex; ordinal < totalMessages; ordinal += cohortCount) {
    ordinals.push(ordinal);
  }
  return ordinals;
}

export function messageSlot({
  ordinal,
  cohortIndex,
  groupSize,
  measureStartMs,
  targetRate,
  nowMs,
  lateSlotMs,
  batch,
  round,
  phase,
  messageBytes,
}) {
  assertNonNegativeInteger(ordinal, 'ordinal');
  assertNonNegativeInteger(cohortIndex, 'cohortIndex');
  assertPositiveInteger(groupSize, 'groupSize');
  if (groupSize < 2) throw new Error('groupSize must be at least 2');
  assertFinite(measureStartMs, 'measureStartMs');
  assertPositive(targetRate, 'targetRate');
  assertFinite(nowMs, 'nowMs');
  assertNonNegative(lateSlotMs, 'lateSlotMs');

  const messageType = ordinal % 10 < 7 ? 'private' : 'group';
  const senderIndex = ordinal % groupSize;
  const receiverIndexes = messageType === 'private'
    ? [(senderIndex + 1) % groupSize]
    : Array.from({ length: groupSize }, (_, index) => index).filter((index) => index !== senderIndex);
  const targetAtMs = measureStartMs + (ordinal * 1_000) / targetRate;
  const latenessMs = Math.max(0, nowMs - targetAtMs);

  return Object.freeze({
    ordinal,
    cohortIndex,
    messageType,
    senderIndex,
    receiverIndexes: Object.freeze(receiverIndexes),
    messageToken: buildMessageToken({ batch, round, phase, sequence: String(ordinal) }),
    body: buildCorrelation({
      batch,
      round,
      phase,
      sequence: String(ordinal),
      messageBytes,
    }),
    timing: Object.freeze({
      targetAtMs,
      waitMs: Math.max(0, targetAtMs - nowMs),
      latenessMs,
      skipped: latenessMs > lateSlotMs,
    }),
  });
}

export function buildMessageToken({ batch, round, phase, sequence }) {
  const correlation = buildCorrelation({ batch, round, phase, sequence, messageBytes: 256 });
  const token = `loadtest-${batch}:${round}:${phase}:${sequence}`;
  if (token.length > 90 || correlation.length === 0) throw new Error('message token exceeds the production column');
  return token;
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
}

function assertNonNegativeInteger(value, name) {
  if (!Number.isInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`);
}

function assertFinite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${name} must be finite`);
}

function assertPositive(value, name) {
  assertFinite(value, name);
  if (value <= 0) throw new Error(`${name} must be positive`);
}

function assertNonNegative(value, name) {
  assertFinite(value, name);
  if (value < 0) throw new Error(`${name} must be non-negative`);
}
