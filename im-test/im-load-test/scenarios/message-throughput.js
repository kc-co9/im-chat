import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { setTimeout } from 'k6/timers';
import { WebSocket } from 'k6/websockets';

import { parseCorrelation } from '../lib/correlation.js';
import { createDeliveryAccounting } from '../lib/delivery-accounting.js';
import { buildMessageToken, messageSlot } from '../lib/message-scheduler.js';

const SOCKET_OPEN = 1;
const BATCH_ID = boundedId('BATCH_ID');
const ROUND_ID = boundedId('ROUND_ID');
const ROUND_NUMBER = decimalEnv('ROUND_NUMBER');
const GROUP_SIZE = positiveIntegerEnv('GROUP_SIZE', 10);
const EFFECTIVE_USERS = positiveIntegerEnv('EFFECTIVE_USERS', 20);
const TARGET_RATE = positiveNumberEnv('TARGET_RATE', 50);
const MESSAGE_BYTES = positiveIntegerEnv('MESSAGE_BYTES', 256);
const LATE_SLOT_MS = nonNegativeNumberEnv('LATE_SLOT_MS', 200);
const WARMUP_SECONDS = nonNegativeNumberEnv('WARMUP_SECONDS', 60);
const MEASURE_SECONDS = positiveNumberEnv('MEASURE_SECONDS', 300);
const DRAIN_SECONDS = nonNegativeNumberEnv('DRAIN_SECONDS', 60);
const BACKLOG_SAMPLE_SECONDS = positiveNumberEnv('BACKLOG_SAMPLE_SECONDS', 60);
const FORCE_CLOSE_GRACE_SECONDS = positiveNumberEnv('FORCE_CLOSE_GRACE_SECONDS', 2);
const MAX_TRACKED_SENDS_PER_VU = positiveIntegerEnv('MAX_TRACKED_SENDS_PER_VU', 50_000);
const MAX_TRACKED_DELIVERIES_PER_VU = positiveIntegerEnv('MAX_TRACKED_DELIVERIES_PER_VU', 50_000);
const MAX_COMPLETED_RECEIPTS_PER_VU = positiveIntegerEnv('MAX_COMPLETED_RECEIPTS_PER_VU', 20_000);
const ROUND_START_MS = nonNegativeNumberEnv('ROUND_START_MS', Date.now() + 1_000);
const MANIFEST_FILE = requiredEnv('MANIFEST_FILE');
const CREDENTIAL_FILE = requiredEnv('CREDENTIAL_FILE');
const SUMMARY_PATH = requiredEnv('SUMMARY_PATH');
const WEBSOCKET_URL = websocketTarget(requiredEnv('WEBSOCKET_URL'));
const COHORT_COUNT = EFFECTIVE_USERS / GROUP_SIZE;
const MEASURE_START_MS = ROUND_START_MS + WARMUP_SECONDS * 1_000;
const MEASURE_END_MS = MEASURE_START_MS + MEASURE_SECONDS * 1_000;
const DRAIN_END_MS = MEASURE_END_MS + DRAIN_SECONDS * 1_000;
const WARMUP_MESSAGES = Math.floor(TARGET_RATE * WARMUP_SECONDS);
const MEASURE_MESSAGES = Math.floor(TARGET_RATE * MEASURE_SECONDS);
const MAX_DURATION_SECONDS = Math.ceil(
  Math.max(0, ROUND_START_MS - Date.now()) / 1_000
    + WARMUP_SECONDS + MEASURE_SECONDS + DRAIN_SECONDS + 5,
);

if (!Number.isInteger(COHORT_COUNT) || COHORT_COUNT < 1) {
  throw new Error('EFFECTIVE_USERS must contain complete cohorts');
}
if (MESSAGE_BYTES !== 256) throw new Error('MESSAGE_BYTES must be exactly 256');

const cohorts = new SharedArray('message cohorts', () => loadCohorts());

const sendsMetric = new Counter('im_message_sends');
const sendSuccessMetric = new Counter('im_message_send_successes');
const expectedDeliveryMetric = new Counter('im_message_expected_deliveries');
const deliveryAckMetric = new Counter('im_message_delivery_ack_successes');
const unknownReceiverMetric = new Counter('im_message_unknown_receivers');
const duplicateMetric = new Counter('im_message_duplicates');
const duplicateAckMetric = new Counter('im_message_duplicate_acks');
const ackFailureMetric = new Counter('im_message_ack_failures');
const drainResidualMetric = new Counter('im_message_drain_residual');
const postDrainExcessMetric = new Counter('im_message_post_drain_excess');
const skippedMetric = new Counter('im_message_skipped');
const scheduledMetric = new Counter('im_message_scheduled');
const sendExceptionMetric = new Counter('im_message_send_exceptions');
const sendFailurePrivateMetric = new Counter('im_message_send_failures_private');
const sendFailureGroupMetric = new Counter('im_message_send_failures_group');
const sendBusyFailureMetric = new Counter('im_message_send_busy_failures');
const sendRepeatFailureMetric = new Counter('im_message_send_repeat_failures');
const sendOtherBusinessFailureMetric = new Counter('im_message_send_other_business_failures');
const sendResponseTimeoutMetric = new Counter('im_message_send_response_timeouts');
const connectionBarrierFailureMetric = new Counter('im_message_connection_barrier_failures');
const trackingOverflowMetric = new Counter('im_message_tracking_overflows');
const forcedStopMetric = new Counter('im_message_forced_stops');
const scheduleDelayMetric = new Trend('im_message_schedule_delay_ms', true);
const sendResponseLatencyMetric = new Trend('im_message_send_response_latency_ms', true);
const receiveLatencyMetric = new Trend('im_message_receive_latency_ms', true);
const backlogMetrics = [1, 2, 3].map((bucket) => new Counter(`im_message_backlog_${bucket}`));

export const options = {
  scenarios: {
    message_throughput: {
      executor: 'per-vu-iterations',
      vus: COHORT_COUNT,
      iterations: 1,
      maxDuration: `${MAX_DURATION_SECONDS}s`,
      gracefulStop: '0s',
    },
  },
  systemTags: ['scenario', 'name', 'status', 'method'],
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
};

export default function messageThroughput() {
  const cohortIndex = __VU - 1;
  const cohort = cohorts[cohortIndex];
  if (cohort === undefined) throw new Error(`VU ${__VU} has no message cohort`);
  const accounting = createDeliveryAccounting({
    groupSize: GROUP_SIZE,
    measureStartMs: MEASURE_START_MS,
    measureEndMs: MEASURE_END_MS,
    drainEndMs: DRAIN_END_MS,
    maxTrackedDeliveries: MAX_TRACKED_DELIVERIES_PER_VU,
    maxCompletedReceipts: MAX_COMPLETED_RECEIPTS_PER_VU,
    trackLatencies: false,
  });
  const sockets = new Map();
  const sendStates = new Map();
  const sendBySequence = new Map();
  const ackBySequence = new Map();
  const openedUsers = new Set();
  let skipped = 0;
  let sendExceptions = 0;
  let ackSequence = 0;
  let connectionBarrierFailures = 0;
  let trackingOverflows = 0;

  for (const member of cohort.members) {
    const socket = new WebSocket(withToken(WEBSOCKET_URL, member.token));
    sockets.set(member.userId, socket);
    socket.addEventListener('open', () => openedUsers.add(member.userId));
    socket.addEventListener('close', () => openedUsers.delete(member.userId));
    socket.addEventListener('message', (event) => {
      try {
        handleFrame(member, socket, JSON.parse(event.data));
      } catch (_) {
        sendExceptions += 1;
      }
    });
    socket.addEventListener('error', () => {
      sendExceptions += 1;
    });
  }

  setTimeout(() => {
    if (openedUsers.size !== GROUP_SIZE
        || [...sockets.values()].some((socket) => socket.readyState !== SOCKET_OPEN)) {
      connectionBarrierFailures = 1;
      return;
    }
    schedulePhase('warmup', ROUND_START_MS, WARMUP_MESSAGES);
    schedulePhase('measure', MEASURE_START_MS, MEASURE_MESSAGES);
  }, Math.max(0, ROUND_START_MS - Date.now()));
  for (let bucket = 1; bucket * BACKLOG_SAMPLE_SECONDS <= MEASURE_SECONDS; bucket += 1) {
    const sampleAt = MEASURE_START_MS + BACKLOG_SAMPLE_SECONDS * 1_000 * bucket;
    setTimeout(() => accounting.recordBacklogSample(), Math.max(0, sampleAt - Date.now()));
  }
  setTimeout(finish, Math.max(0, DRAIN_END_MS - Date.now()));

  function schedulePhase(phase, phaseStartMs, totalMessages) {
    let ordinal = cohortIndex;
    const scheduleNext = () => {
      if (ordinal >= totalMessages) return;
      const planned = messageSlot({
        ordinal,
        cohortIndex,
        groupSize: GROUP_SIZE,
        measureStartMs: phaseStartMs,
        targetRate: TARGET_RATE,
        nowMs: Date.now(),
        lateSlotMs: LATE_SLOT_MS,
        batch: BATCH_ID,
        round: ROUND_NUMBER,
        phase,
        messageBytes: MESSAGE_BYTES,
      });
      setTimeout(() => {
        const slot = messageSlot({
          ordinal,
          cohortIndex,
          groupSize: GROUP_SIZE,
          measureStartMs: phaseStartMs,
          targetRate: TARGET_RATE,
          nowMs: Date.now(),
          lateSlotMs: LATE_SLOT_MS,
          batch: BATCH_ID,
          round: ROUND_NUMBER,
          phase,
          messageBytes: MESSAGE_BYTES,
        });
        if (phase === 'measure') {
          scheduledMetric.add(1, metricTags(phase, slot.messageType));
          scheduleDelayMetric.add(slot.timing.latenessMs, metricTags(phase, slot.messageType));
        }
        if (slot.timing.skipped) {
          if (phase === 'measure') skipped += 1;
        } else {
          dispatch(slot);
        }
        ordinal += COHORT_COUNT;
        scheduleNext();
      }, planned.timing.waitMs);
    };
    scheduleNext();
  }

  function dispatch(slot) {
    const sender = cohort.members[slot.senderIndex];
    const receivers = slot.receiverIndexes.map((index) => cohort.members[index]);
    const senderSocket = sockets.get(sender.userId);
    const receiverChats = expectedChats(slot, sender, receivers);
    const event = {
      token: slot.messageToken,
      messageType: slot.messageType,
      senderId: sender.userId,
      receiverIds: receivers.map(({ userId }) => userId),
      receiverChats,
      success: false,
      phase: slot.phase ?? parseCorrelation(slot.body).phase,
      sentAtMs: Date.now(),
    };
    if (event.phase === 'measure') sendsMetric.add(1, metricTags(event.phase, slot.messageType));
    const sequence = `send-${event.phase}-${slot.ordinal}`;
    if (sendStates.size >= MAX_TRACKED_SENDS_PER_VU) {
      if (event.phase === 'measure') trackingOverflows += 1;
      accounting.recordSend(event);
      return;
    }
    const state = {
      event,
      outcome: 'pending',
      buffered: [],
      expectedChats: receiverChats,
      firstReceivers: new Set(),
      ackedReceivers: new Set(),
    };
    sendStates.set(slot.messageToken, state);
    sendBySequence.set(sequence, state);
    if (senderSocket?.readyState !== SOCKET_OPEN) {
      state.outcome = false;
      recordSendFailure(state, 'generator');
      accounting.recordSend(event);
      sendStates.delete(slot.messageToken);
      sendBySequence.delete(sequence);
      sendExceptions += 1;
      return;
    }
    const chatId = slot.messageType === 'private'
      ? sender.privateChats[receivers[0].userId]
      : sender.groupChatId;
    try {
      senderSocket.send(JSON.stringify({
        version: '1',
        cmd: slot.messageType === 'private' ? 'message.private.send' : 'message.group.send',
        seq: sequence,
        traceId: `loadtest-${BATCH_ID}-${ROUND_ID}-${slot.ordinal}`,
        body: {
          chatId,
          messageToken: slot.messageToken,
          messageType: 'TEXT',
          messageContent: slot.body,
        },
      }));
    } catch (_) {
      state.outcome = false;
      recordSendFailure(state, 'generator');
      accounting.recordSend(event);
      sendStates.delete(slot.messageToken);
      sendBySequence.delete(sequence);
      sendExceptions += 1;
    }
  }

  function handleFrame(member, socket, frame) {
    if (frame?.type === 'response' || frame?.type === 'error') {
      if (typeof frame.seq !== 'string') return;
      const send = sendBySequence.get(frame.seq);
      if (send !== undefined) {
        sendBySequence.delete(frame.seq);
        if (send.event.phase === 'measure') {
          sendResponseLatencyMetric.add(
            Date.now() - send.event.sentAtMs,
            metricTags(send.event.phase, send.event.messageType),
          );
        }
        send.event.success = frame.type === 'response' && frame.code === 0;
        send.outcome = send.event.success;
        if (!send.outcome) recordSendFailure(send, 'business', frame.body?.code);
        accounting.recordSend(send.event);
        for (const buffered of send.buffered) {
          processPush(buffered.member, buffered.socket, buffered.frame, send, buffered.receivedAtMs);
        }
        send.buffered.length = 0;
        if (!send.outcome) sendStates.delete(send.event.token);
        return;
      }
      const ack = ackBySequence.get(frame.seq);
      if (ack !== undefined) {
        ackBySequence.delete(frame.seq);
        accounting.recordAck({ ...ack, code: frame.code, ackedAtMs: Date.now(), phase: ack.phase });
        if (frame.type === 'response' && frame.code === 0) {
          ack.state.ackedReceivers.add(ack.receiverId);
          if (ack.state.ackedReceivers.size === ack.state.event.receiverIds.length) {
            sendStates.delete(ack.token);
          }
        }
      }
      return;
    }
    if (frame?.type !== 'push') return;
    const receivedAtMs = Date.now();
    const notification = frame.body?.notification;
    const correlation = parseCorrelation(notification?.messageContent, { messageBytes: MESSAGE_BYTES });
    const token = buildMessageToken(correlation);
    const state = sendStates.get(token);
    if (state?.outcome === 'pending') {
      if (state.buffered.length >= MAX_TRACKED_DELIVERIES_PER_VU) {
        trackingOverflows += 1;
        return;
      }
      state.buffered.push({ member, socket, frame, receivedAtMs });
      return;
    }
    processPush(member, socket, frame, state, receivedAtMs);
  }

  function processPush(member, socket, frame, state, receivedAtMs) {
    const notification = frame.body?.notification;
    const correlation = parseCorrelation(notification?.messageContent, { messageBytes: MESSAGE_BYTES });
    const token = buildMessageToken(correlation);
    const receipt = parseReceipt(frame.body?.receiptId);
    const phase = correlation.phase;
    if (state === undefined || receipt.receiverId !== member.userId
        || state.expectedChats[member.userId] !== receipt.chatId) {
      accounting.recordDelivery({
        token, receiverId: member.userId, receiptId: frame.body?.receiptId,
        receivedAtMs, phase,
      });
      return;
    }
    const first = !state.firstReceivers.has(member.userId);
    accounting.recordDelivery({
      token, receiverId: member.userId, receiptId: frame.body.receiptId,
      receivedAtMs, phase,
    });
    if (first && phase === 'measure') {
      state.firstReceivers.add(member.userId);
      receiveLatencyMetric.add(receivedAtMs - state.event.sentAtMs, metricTags(phase, state.event.messageType));
    }
    const sequence = `ack-${++ackSequence}`;
    const ack = {
      token,
      receiverId: member.userId,
      receiptId: frame.body.receiptId,
      phase,
      state,
    };
    if (ackBySequence.size >= MAX_TRACKED_DELIVERIES_PER_VU) {
      trackingOverflows += 1;
      return;
    }
    ackBySequence.set(sequence, ack);
    socket.send(JSON.stringify({
      version: '1',
      cmd: 'notification.ack',
      seq: sequence,
      traceId: `loadtest-ack-${BATCH_ID}-${ROUND_ID}-${ackSequence}`,
      body: {
        chatId: receipt.chatId,
        messageId: receipt.messageId,
        receiptType: receipt.receiptType,
      },
    }));
  }

  function expectedChats(slot, sender, receivers) {
    return Object.fromEntries(receivers.map((receiver) => [
      receiver.userId,
      slot.messageType === 'private' ? receiver.privateChats[sender.userId] : receiver.groupChatId,
    ]));
  }

  function finish() {
    for (const state of sendStates.values()) {
      if (state.outcome !== 'pending') continue;
      state.event.success = false;
      state.outcome = false;
      recordSendFailure(state, 'timeout');
      accounting.recordSend(state.event);
      for (const buffered of state.buffered) {
        processPush(buffered.member, buffered.socket, buffered.frame, state, buffered.receivedAtMs);
      }
      state.buffered.length = 0;
    }
    const snapshot = accounting.snapshot();
    const tags = metricTags('measure', 'all');
    sendSuccessMetric.add(snapshot.sendSuccesses, tags);
    expectedDeliveryMetric.add(snapshot.expectedDeliveries, tags);
    deliveryAckMetric.add(snapshot.deliveryAckSuccesses, tags);
    unknownReceiverMetric.add(snapshot.unknownReceivers, tags);
    duplicateMetric.add(snapshot.duplicates, tags);
    duplicateAckMetric.add(snapshot.duplicateAcks, tags);
    ackFailureMetric.add(snapshot.ackFailures, tags);
    drainResidualMetric.add(snapshot.drainResidual, tags);
    postDrainExcessMetric.add(snapshot.postDrainExcess, tags);
    skippedMetric.add(skipped, tags);
    sendExceptionMetric.add(sendExceptions, tags);
    connectionBarrierFailureMetric.add(connectionBarrierFailures, tags);
    trackingOverflowMetric.add(trackingOverflows + snapshot.trackingOverflows, tags);
    snapshot.minuteBacklog.forEach((value, index) => backlogMetrics[index].add(value, tags));
    for (const socket of sockets.values()) {
      if (socket.readyState === SOCKET_OPEN) socket.close(1000, 'message round complete');
    }
    setTimeout(() => {
      if (openedUsers.size === 0) return;
      forcedStopMetric.add(openedUsers.size, tags);
      exec.test.abort('message websocket close grace period expired');
    }, FORCE_CLOSE_GRACE_SECONDS * 1_000);
  }

  function recordSendFailure(state, reason, code) {
    if (state.event.phase !== 'measure') return;
    const tags = metricTags(state.event.phase, state.event.messageType);
    if (state.event.messageType === 'private') sendFailurePrivateMetric.add(1, tags);
    else sendFailureGroupMetric.add(1, tags);
    if (reason === 'timeout') sendResponseTimeoutMetric.add(1, tags);
    if (reason === 'business' && String(code) === '10010') sendBusyFailureMetric.add(1, tags);
    else if (reason === 'business' && String(code) === '10007') sendRepeatFailureMetric.add(1, tags);
    else if (reason === 'business') sendOtherBusinessFailureMetric.add(1, tags);
  }
}

export function handleSummary(data) {
  const sends = count(data, 'im_message_sends');
  const scheduled = count(data, 'im_message_scheduled');
  const summary = {
    version: 1,
    boundary: 'k6-message',
    input: {
      batchId: BATCH_ID,
      roundId: ROUND_ID,
      effectiveUsers: EFFECTIVE_USERS,
      groupSize: GROUP_SIZE,
      targetRate: TARGET_RATE,
      messageBytes: MESSAGE_BYTES,
      warmupSeconds: WARMUP_SECONDS,
      measureSeconds: MEASURE_SECONDS,
      drainSeconds: DRAIN_SECONDS,
      backlogSampleSeconds: BACKLOG_SAMPLE_SECONDS,
      forceCloseGraceSeconds: FORCE_CLOSE_GRACE_SECONDS,
      maxTrackedSendsPerVu: MAX_TRACKED_SENDS_PER_VU,
      maxTrackedDeliveriesPerVu: MAX_TRACKED_DELIVERIES_PER_VU,
      maxCompletedReceiptsPerVu: MAX_COMPLETED_RECEIPTS_PER_VU,
    },
    metrics: {
      sends,
      sendSuccesses: count(data, 'im_message_send_successes'),
      expectedDeliveries: count(data, 'im_message_expected_deliveries'),
      deliveryAckSuccesses: count(data, 'im_message_delivery_ack_successes'),
      unknownReceivers: count(data, 'im_message_unknown_receivers'),
      duplicates: count(data, 'im_message_duplicates'),
      duplicateAcks: count(data, 'im_message_duplicate_acks'),
      ackFailures: count(data, 'im_message_ack_failures'),
      drainResidual: count(data, 'im_message_drain_residual'),
      postDrainExcess: count(data, 'im_message_post_drain_excess'),
      skipped: count(data, 'im_message_skipped'),
      skipRate: ratio(count(data, 'im_message_skipped'), scheduled),
      actualTargetRate: ratio(sends, TARGET_RATE * MEASURE_SECONDS),
      scheduleP95Ms: value(data, 'im_message_schedule_delay_ms', 'p(95)'),
      sendResponseLatencyP95Ms: value(data, 'im_message_send_response_latency_ms', 'p(95)'),
      sendResponseLatencyP99Ms: value(data, 'im_message_send_response_latency_ms', 'p(99)'),
      receiveLatencyP95Ms: value(data, 'im_message_receive_latency_ms', 'p(95)'),
      receiveLatencyP99Ms: value(data, 'im_message_receive_latency_ms', 'p(99)'),
      minuteBacklog: [1, 2, 3].map((bucket) => count(data, `im_message_backlog_${bucket}`)),
      sendExceptions: count(data, 'im_message_send_exceptions'),
      sendFailuresPrivate: count(data, 'im_message_send_failures_private'),
      sendFailuresGroup: count(data, 'im_message_send_failures_group'),
      sendBusyFailures: count(data, 'im_message_send_busy_failures'),
      sendRepeatFailures: count(data, 'im_message_send_repeat_failures'),
      sendOtherBusinessFailures: count(data, 'im_message_send_other_business_failures'),
      sendResponseTimeouts: count(data, 'im_message_send_response_timeouts'),
      connectionBarrierFailures: count(data, 'im_message_connection_barrier_failures'),
      trackingOverflows: count(data, 'im_message_tracking_overflows'),
      forcedStops: count(data, 'im_message_forced_stops'),
    },
  };
  return { [SUMMARY_PATH]: `${JSON.stringify(summary, null, 2)}\n` };
}

function loadCohorts() {
  const manifest = JSON.parse(open(MANIFEST_FILE));
  const credentials = JSON.parse(open(CREDENTIAL_FILE));
  if (manifest?.batchId !== BATCH_ID || credentials?.batchId !== BATCH_ID
      || !Array.isArray(manifest.cohorts) || !Array.isArray(credentials.credentials)) {
    throw new Error('message input manifests are invalid');
  }
  const credentialByUser = Object.fromEntries(credentials.credentials.map((credential) => (
    [credential.userId, credential]
  )));
  const privateChats = Object.fromEntries(manifest.privateChats.map((chat) => (
    [`${chat.userId}:${chat.peerUserId}`, chat.chatId]
  )));
  const groupChats = Object.fromEntries(manifest.groupChats.map((chat) => (
    [`${chat.groupId}:${chat.userId}`, chat.chatId]
  )));
  return manifest.cohorts.slice(0, COHORT_COUNT).map((cohort) => ({
    cohortId: cohort.cohortId,
    groupId: cohort.groupId,
    members: cohort.userIds.map((userId) => {
      const credential = credentialByUser[userId];
      if (credential === undefined || typeof credential.accessToken !== 'string') {
        throw new Error(`credential is missing for ${userId}`);
      }
      const privateByPeer = {};
      for (const peerId of cohort.userIds) {
        const chatId = privateChats[`${userId}:${peerId}`];
        if (chatId !== undefined) privateByPeer[peerId] = chatId;
      }
      const groupChatId = groupChats[`${cohort.groupId}:${userId}`];
      if (groupChatId === undefined) throw new Error(`group chat is missing for ${userId}`);
      return { userId, token: credential.accessToken, privateChats: privateByPeer, groupChatId };
    }),
  }));
}

function parseReceipt(receiptId) {
  if (typeof receiptId !== 'string') throw new Error('receiptId must be a string');
  const [receiptType, receiverId, chatId, messageId, extra] = receiptId.split(':');
  if (!['PRIVATE_MESSAGE_SEND', 'GROUP_MESSAGE_SEND'].includes(receiptType) || extra !== undefined) {
    throw new Error('receiptId type is invalid');
  }
  for (const value of [receiverId, chatId, messageId]) {
    if (!/^[1-9][0-9]*$/.test(value)) throw new Error('receiptId contains a noncanonical ID');
  }
  return { receiptType, receiverId, chatId, messageId };
}

function withToken(raw, token) {
  return `${raw}?token=${encodeURIComponent(token)}`;
}

function metricTags(phase, messageType) {
  return { batch: BATCH_ID, round: ROUND_ID, phase, message_type: messageType };
}

function count(data, name) {
  return value(data, name, 'count');
}

function value(data, name, field) {
  const metric = data?.metrics?.[name]?.values?.[field];
  return typeof metric === 'number' && Number.isFinite(metric) ? metric : 0;
}

function ratio(numerator, denominator) {
  return denominator > 0 ? numerator / denominator : 0;
}

function websocketTarget(raw) {
  if (typeof raw !== 'string'
      || !/^(wss?):\/\/(localhost|127\.0\.0\.1|\[::1\])(?::[1-9][0-9]{0,4})?\/[^?#]*$/.test(raw)) {
    throw new Error('WEBSOCKET_URL must be a query-free local websocket URL');
  }
  return raw;
}

function boundedId(name) {
  const value = requiredEnv(name);
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(value)) throw new Error(`${name} is invalid`);
  return value;
}

function decimalEnv(name) {
  const value = requiredEnv(name);
  if (!/^(?:0|[1-9][0-9]{0,5})$/.test(value)) throw new Error(`${name} must be a bounded decimal`);
  return value;
}

function requiredEnv(name) {
  const value = __ENV[name];
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${name} is required`);
  return value;
}

function positiveIntegerEnv(name, fallback) {
  const value = positiveNumberEnv(name, fallback);
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function positiveNumberEnv(name, fallback) {
  const value = nonNegativeNumberEnv(name, fallback);
  if (value <= 0) throw new Error(`${name} must be positive`);
  return value;
}

function nonNegativeNumberEnv(name, fallback) {
  const raw = __ENV[name];
  const value = raw === undefined ? fallback : Number(raw);
  if (!Number.isFinite(value) || value < 0) throw new Error(`${name} must be non-negative`);
  return value;
}
