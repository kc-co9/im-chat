import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import { setTimeout } from 'k6/timers';
import { WebSocket } from 'k6/websockets';

const SOCKET_CONNECTING = 0;
const SOCKET_OPEN = 1;
const BATCH_ID = boundedTagEnv('BATCH_ID');
const ROUND_ID = boundedTagEnv('ROUND_ID');
const GROUP_SIZE = integerEnv('GROUP_SIZE', 10);
const REQUESTED_USERS = integerEnv('REQUESTED_USERS', 20);
const EFFECTIVE_USERS = integerEnv('EFFECTIVE_USERS', REQUESTED_USERS - (REQUESTED_USERS % GROUP_SIZE));
const CONNECTION_RATE = positiveNumberEnv('CONNECTION_RATE', 20);
const LATE_SLOT_MS = numberEnv('LATE_SLOT_MS', 200);
const STABLE_SECONDS = numberEnv('STABLE_SECONDS', 60);
const MEASURE_SECONDS = numberEnv('MEASURE_SECONDS', 300);
const DISCONNECT_SECONDS = numberEnv('DISCONNECT_SECONDS', 60);
const ROUTE_CLEANUP_SECONDS = numberEnv('ROUTE_CLEANUP_SECONDS', 10);
const ROUND_START_MS = numberEnv('ROUND_START_MS', Date.now() + 1_000);
const SUMMARY_PATH = requiredEnv('SUMMARY_PATH');
const CREDENTIAL_FILE = requiredEnv('CREDENTIAL_FILE');
const WEBSOCKET_URL = targetUrl(requiredEnv('WEBSOCKET_URL'), ['ws:', 'wss:']);
const RAMP_SECONDS = EFFECTIVE_USERS / CONNECTION_RATE;
const MAX_DURATION_SECONDS = Math.ceil(
  Math.max(0, ROUND_START_MS - Date.now()) / 1_000
    + RAMP_SECONDS
    + STABLE_SECONDS
    + MEASURE_SECONDS
    + DISCONNECT_SECONDS
    + ROUTE_CLEANUP_SECONDS
    + 5,
);

if (EFFECTIVE_USERS < GROUP_SIZE || EFFECTIVE_USERS % GROUP_SIZE !== 0) {
  throw new Error('EFFECTIVE_USERS must contain complete cohorts');
}
if (REQUESTED_USERS < EFFECTIVE_USERS) {
  throw new Error('REQUESTED_USERS must be at least EFFECTIVE_USERS');
}

const credentials = new SharedArray('connection credentials', () => {
  const document = JSON.parse(open(CREDENTIAL_FILE));
  if (document?.version !== 1 || document.batchId !== BATCH_ID
      || !Array.isArray(document.credentials)) {
    throw new Error('invalid connection credential manifest');
  }
  if (document.credentials.length < EFFECTIVE_USERS) {
    throw new Error('authentication barrier has fewer credentials than EFFECTIVE_USERS');
  }
  const selected = document.credentials.slice(0, EFFECTIVE_USERS).map((credential, index) => {
    if (typeof credential?.userId !== 'string' || !/^[1-9][0-9]*$/.test(credential.userId)
        || typeof credential.accessToken !== 'string' || credential.accessToken.length === 0) {
      throw new Error(`invalid credential at ordinal ${index}`);
    }
    return { userId: credential.userId, token: credential.accessToken };
  });
  if (new Set(selected.map(({ userId }) => userId)).size !== selected.length) {
    throw new Error('connection credential users must be unique');
  }
  return selected;
});

const plannedConnections = new Counter('im_connection_planned');
const attemptedConnections = new Counter('im_connection_attempted');
const skippedConnections = new Counter('im_connection_skipped');
const openedConnections = new Counter('im_connection_opened');
const businessHandshakeFailures = new Counter('im_connection_handshake_business_failed');
const transportHandshakeFailures = new Counter('im_connection_handshake_transport_failed');
const unexpectedDisconnects = new Counter('im_connection_unexpected_disconnect');
const measurementOnline = new Counter('im_connection_measurement_online');
const controlledCloses = new Counter('im_connection_controlled_close');
const scheduleDelay = new Trend('im_connection_schedule_delay_ms', true);

export const options = {
  scenarios: {
    connection_capacity: {
      executor: 'per-vu-iterations',
      vus: EFFECTIVE_USERS / GROUP_SIZE,
      iterations: 1,
      maxDuration: `${MAX_DURATION_SECONDS}s`,
      gracefulStop: '0s',
    },
  },
  systemTags: ['scenario', 'name', 'status', 'method'],
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
};

export default function connectionCapacity() {
  const cohortIndex = __VU - 1;
  const cohortOffset = cohortIndex * GROUP_SIZE;
  if (cohortOffset < 0 || cohortOffset + GROUP_SIZE > EFFECTIVE_USERS) {
    throw new Error(`VU ${__VU} has no complete cohort`);
  }

  const sockets = new Array(GROUP_SIZE);
  const lifecycles = new Array(GROUP_SIZE);
  const online = new Set();
  plannedConnections.add(GROUP_SIZE, tags('ramp'));

  for (let memberIndex = 0; memberIndex < GROUP_SIZE; memberIndex += 1) {
    const ordinal = cohortIndex * GROUP_SIZE + memberIndex;
    const targetAtMs = ROUND_START_MS + (ordinal * 1_000) / CONNECTION_RATE;
    setTimeout(() => {
      const latenessMs = Math.max(0, Date.now() - targetAtMs);
      scheduleDelay.add(latenessMs, tags('ramp'));
      if (latenessMs > LATE_SLOT_MS) {
        skippedConnections.add(1, tags('ramp'));
        return;
      }
      attemptedConnections.add(1, tags('ramp'));
      const credential = credentials[ordinal];
      const socket = new WebSocket(withToken(WEBSOCKET_URL, credential.token));
      sockets[memberIndex] = socket;
      const lifecycle = {
        handshakeState: 'pending',
        closeRequested: false,
        controlledCloseInitiated: false,
        closeClassified: false,
      };
      lifecycles[memberIndex] = lifecycle;

      const recordHandshakeFailure = (kind) => {
        if (lifecycle.handshakeState !== 'pending') return;
        lifecycle.handshakeState = 'failed';
        if (kind === 'business') {
          businessHandshakeFailures.add(1, tags('ramp'));
        } else {
          transportHandshakeFailures.add(1, tags('ramp'));
        }
      };

      socket.addEventListener('open', () => {
        if (lifecycle.handshakeState !== 'pending') {
          socket.close(1000, 'load-test terminal handshake state');
          return;
        }
        lifecycle.handshakeState = 'opened';
        online.add(memberIndex);
        openedConnections.add(1, tags('ramp'));
        if (lifecycle.closeRequested) {
          lifecycle.controlledCloseInitiated = true;
          socket.close(1000, 'load-test controlled close');
        }
      });
      socket.addEventListener('error', (event) => {
        if (lifecycle.handshakeState === 'opened') return;
        recordHandshakeFailure(isBusinessHandshakeFailure(event) ? 'business' : 'transport');
      });
      socket.addEventListener('close', () => {
        online.delete(memberIndex);
        if (lifecycle.handshakeState === 'pending') recordHandshakeFailure('transport');
        if (lifecycle.handshakeState !== 'opened' || lifecycle.closeClassified) return;
        lifecycle.closeClassified = true;
        if (lifecycle.controlledCloseInitiated) {
          controlledCloses.add(1, tags('disconnect'));
        } else {
          unexpectedDisconnects.add(1, tags('measure'));
        }
      });
    }, Math.max(0, targetAtMs - Date.now()));
  }

  const measurementEndMs = ROUND_START_MS
    + RAMP_SECONDS * 1_000
    + (STABLE_SECONDS + MEASURE_SECONDS) * 1_000;
  setTimeout(() => {
    measurementOnline.add(online.size, tags('measure'));
    for (let memberIndex = 0; memberIndex < GROUP_SIZE; memberIndex += 1) {
      const closeDelayMs = (memberIndex * DISCONNECT_SECONDS * 1_000) / GROUP_SIZE;
      setTimeout(() => {
        const socket = sockets[memberIndex];
        const lifecycle = lifecycles[memberIndex];
        if (socket === undefined || lifecycle === undefined) return;
        if (socket.readyState === SOCKET_OPEN) {
          lifecycle.controlledCloseInitiated = true;
          socket.close(1000, 'load-test controlled close');
        } else if (socket.readyState === SOCKET_CONNECTING) {
          lifecycle.closeRequested = true;
        }
      }, closeDelayMs);
    }
  }, Math.max(0, measurementEndMs - Date.now()));
}

export function handleSummary(data) {
  const planned = counterValue(data, 'im_connection_planned');
  const attempted = counterValue(data, 'im_connection_attempted');
  const skipped = counterValue(data, 'im_connection_skipped');
  const opened = counterValue(data, 'im_connection_opened');
  const online = counterValue(data, 'im_connection_measurement_online');
  const unexpected = counterValue(data, 'im_connection_unexpected_disconnect');
  const controlled = counterValue(data, 'im_connection_controlled_close');
  const scheduleP95Ms = metricValue(data, 'im_connection_schedule_delay_ms', 'p(95)');
  const summary = {
    version: 1,
    boundary: 'k6-connection',
    input: {
      requestedUsers: REQUESTED_USERS,
      effectiveUsers: EFFECTIVE_USERS,
      batchId: BATCH_ID,
      roundId: ROUND_ID,
      groupSize: GROUP_SIZE,
      connectionRate: CONNECTION_RATE,
      roundStartMs: ROUND_START_MS,
      stableSeconds: STABLE_SECONDS,
      measureSeconds: MEASURE_SECONDS,
      disconnectSeconds: DISCONNECT_SECONDS,
    },
    metrics: {
      plannedConnections: planned,
      attemptedConnections: attempted,
      skippedConnections: skipped,
      skipRate: ratio(skipped, planned),
      scheduleP95Ms,
      connectionSuccesses: opened,
      connectionSuccessRate: ratio(opened, planned),
      businessHandshakeFailures: counterValue(data, 'im_connection_handshake_business_failed'),
      transportHandshakeFailures: counterValue(data, 'im_connection_handshake_transport_failed'),
      measurementEndOnline: online,
      onlineRate: ratio(online, planned),
      unexpectedDisconnects: unexpected,
      unexpectedDisconnectRate: ratio(unexpected, opened),
      controlledCloses: controlled,
    },
  };
  return { [SUMMARY_PATH]: `${JSON.stringify(summary, null, 2)}\n` };
}

function withToken(raw, token) {
  return `${raw}?token=${encodeURIComponent(token)}`;
}

function isBusinessHandshakeFailure(event) {
  const text = `${event?.error ?? ''} ${event?.message ?? ''}`;
  return /\b(?:401|403)\b/.test(text);
}

function tags(phase) {
  return { batch: BATCH_ID, round: ROUND_ID, phase };
}

function counterValue(data, name) {
  return metricValue(data, name, 'count');
}

function metricValue(data, name, field) {
  const value = data?.metrics?.[name]?.values?.[field];
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function ratio(numerator, denominator) {
  return denominator > 0 ? numerator / denominator : 0;
}

function targetUrl(raw, protocols) {
  const match = typeof raw === 'string'
    ? raw.match(/^(wss?):\/\/(localhost|127\.0\.0\.1|\[::1\])(?::[1-9][0-9]{0,4})?(\/[^?#]*)$/)
    : null;
  if (match === null || !protocols.includes(`${match[1]}:`)) {
    throw new Error('WEBSOCKET_URL must be a query-free local websocket URL');
  }
  const portMatch = raw.match(/\]:(\d+)|^[a-z]+:\/\/[^/:]+:(\d+)/);
  const port = portMatch === null ? undefined : Number(portMatch[1] ?? portMatch[2]);
  if (port !== undefined && port > 65_535) throw new Error('WEBSOCKET_URL port is out of range');
  return raw;
}

function boundedTagEnv(name) {
  const value = requiredEnv(name);
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(value)) {
    throw new Error(`${name} is not a bounded metric tag`);
  }
  return value;
}

function requiredEnv(name) {
  const value = __ENV[name];
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${name} is required`);
  return value;
}

function integerEnv(name, fallback) {
  const value = numberEnv(name, fallback);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function numberEnv(name, fallback) {
  const raw = __ENV[name];
  const value = raw === undefined ? fallback : Number(raw);
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new Error(`${name} must be a non-negative finite number`);
  }
  return value;
}

function positiveNumberEnv(name, fallback) {
  const value = numberEnv(name, fallback);
  if (value <= 0) throw new Error(`${name} must be positive`);
  return value;
}
