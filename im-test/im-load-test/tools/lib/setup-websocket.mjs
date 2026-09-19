import * as nodeFileSystem from 'node:fs/promises';
import { dirname } from 'node:path';
import WebSocket from 'ws';

import { resolveConfig } from '../../lib/config.js';
import { buildAckFrame, parseFrame } from '../../lib/protocol.js';

const DEFAULT_TIMEOUT_MS = 5_000;
const DEFAULT_RETRY_HORIZON_MS = 2_000;

export async function openSetupWebSockets({
  config,
  credentials,
  WebSocketClass = WebSocket,
  scheduler = globalThis,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  retryHorizonMs = DEFAULT_RETRY_HORIZON_MS,
  quietMs = retryHorizonMs,
  openAttempts = 1,
  openRetryMs = 0,
  sequence = defaultSequence(),
  traceId = defaultTraceId(),
  batchId,
  receiptJournalPath,
  fileSystem = {},
  hooks = {},
} = {}) {
  const safeConfig = revalidateConfig(config);
  validateCredentials(credentials);
  assertPositiveInteger(timeoutMs, 'timeoutMs');
  assertPositiveInteger(openAttempts, 'openAttempts');
  assertNonNegativeInteger(openRetryMs, 'openRetryMs');
  assertNonNegativeInteger(retryHorizonMs, 'retryHorizonMs');
  if (!Number.isInteger(quietMs) || quietMs < retryHorizonMs) {
    throw new Error('quiet interval must cover the complete retry horizon');
  }
  const fs = { ...nodeFileSystem, ...fileSystem };
  const ackState = receiptJournalPath === undefined
    ? emptyAckState()
    : await recoverAckJournal(fs, receiptJournalPath, batchId);
  const sockets = new Map();
  let activeBarrier = null;
  let closed = false;

  const failBarrier = (error) => activeBarrier?.reject(error);
  try {
    await Promise.all(credentials.map(async ({ userId, accessToken }) => {
      const target = new URL(safeConfig.targets.websocket);
      target.searchParams.set('token', accessToken);
      let lastError;
      for (let attempt = 1; attempt <= openAttempts; attempt += 1) {
        const socket = new WebSocketClass(target);
        sockets.set(userId, socket);
        socket.on('message', (payload) => {
          if (closed) return;
          Promise.resolve(activeBarrier?.onMessage(userId, payload.toString())).catch(failBarrier);
        });
        socket.on('error', () => failBarrier(new Error(`setup websocket failed for user ${userId}`)));
        socket.on('close', () => {
          if (!closed) failBarrier(new Error(`setup websocket closed before completion for user ${userId}`));
        });
        try {
          await waitForOpen(socket, userId, timeoutMs, scheduler);
          return;
        } catch (error) {
          lastError = error;
          sockets.delete(userId);
          socket.terminate();
          if (attempt < openAttempts) {
            await new Promise((resolve) => scheduler.setTimeout(resolve, openRetryMs));
          }
        }
      }
      throw lastError;
    }));
  } catch (error) {
    closed = true;
    await closeSockets(sockets, scheduler);
    throw error;
  }

  return Object.freeze({
    waitForGroupCreated({ ownerUserId, memberUserIds }) {
      if (closed) throw new Error('setup websockets are closed');
      if (activeBarrier !== null) throw new Error('a setup notification barrier is already active');
      const barrier = createGroupBarrier({
        ownerUserId, memberUserIds, sockets, scheduler, timeoutMs, quietMs, sequence, traceId,
        ackState, fs, receiptJournalPath, batchId, hooks,
      });
      activeBarrier = barrier;
      return barrier.start().finally(() => {
        if (activeBarrier === barrier) activeBarrier = null;
      });
    },
    async seedKnownReceipts(receiptIds) {
      if (activeBarrier === null) {
        if (Array.isArray(receiptIds) && receiptIds.every((receiptId) => {
          const record = ackState.receipts.get(receiptId);
          return record?.completed === true;
        })) return;
        throw new Error('setup notification barrier is not active');
      }
      await activeBarrier.seedKnownReceipts(receiptIds);
    },
    async close() {
      if (closed) return;
      closed = true;
      const barrier = activeBarrier;
      barrier?.beginClose(new Error('setup websockets closed before barrier completion'));
      await barrier?.drain();
      await closeSockets(sockets, scheduler);
      await new Promise((resolve) => setImmediate(resolve));
      sockets.clear();
    },
  });
}

function createGroupBarrier({
  ownerUserId, memberUserIds, sockets, scheduler, timeoutMs, quietMs, sequence, traceId,
  ackState, fs, receiptJournalPath, batchId, hooks,
}) {
  assertCanonicalId(ownerUserId, 'ownerUserId');
  if (!Array.isArray(memberUserIds) || memberUserIds.length === 0) {
    throw new Error('memberUserIds must be a non-empty array');
  }
  const expected = new Set(memberUserIds);
  if (expected.size !== memberUserIds.length || expected.has(ownerUserId)) {
    throw new Error('memberUserIds must be unique and exclude owner');
  }
  for (const userId of [ownerUserId, ...memberUserIds]) {
    assertCanonicalId(userId, 'setup userId');
    if (!sockets.has(userId)) throw new Error(`missing setup websocket for user ${userId}`);
  }

  const received = new Map();
  const receiptIds = new Set();
  const pendingAcks = new Map();
  let messageId;
  let settled = false;
  let closing = false;
  let quietTimer;
  let resolvePromise;
  let rejectPromise;
  let messageQueue = Promise.resolve();
  const promise = new Promise((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });
  const timeout = scheduler.setTimeout(
    () => reject(new Error('missing group notification before setup barrier timed out')),
    timeoutMs,
  );

  const cleanup = () => {
    scheduler.clearTimeout(timeout);
    if (quietTimer !== undefined) scheduler.clearTimeout(quietTimer);
  };
  const reject = (error) => {
    if (settled) return;
    settled = true;
    cleanup();
    rejectPromise(error);
  };
  const finishAfterQuietWindow = () => {
    if (received.size !== expected.size || pendingAcks.size !== 0 || settled) return;
    scheduler.clearTimeout(timeout);
    if (quietTimer !== undefined) scheduler.clearTimeout(quietTimer);
    quietTimer = scheduler.setTimeout(() => {
      if (settled) return;
      settled = true;
      cleanup();
      resolvePromise({
        messageId,
        memberChats: memberUserIds.map((userId) => ({ userId, chatId: received.get(userId).chatId })),
        receiptIds: memberUserIds.map((userId) => received.get(userId).receiptId),
      });
    }, quietMs);
  };
  const sendAck = (receiverId, receiptId) => {
    if (closing) return;
    if (quietTimer !== undefined) {
      scheduler.clearTimeout(quietTimer);
      quietTimer = undefined;
    }
    const ackSequence = sequence();
    pendingAcks.set(ackSequence, receiverId);
    sockets.get(receiverId).send(JSON.stringify(buildAckFrame({
      receiptId, sequence: ackSequence, traceId: traceId(),
    })));
  };
  const recordReceipt = (receiverId, receiptId) => {
    const parts = parseReceipt(receiptId);
    if (parts.receiverId !== receiverId) throw new Error('ACK journal receiver mismatch');
    if (!expected.has(receiverId)) throw new Error(`notification receiver ${receiverId} is not a cohort member`);
    if (received.has(receiverId) || receiptIds.has(receiptId)) {
      throw new Error(`duplicate group notification for receiver ${receiverId}`);
    }
    if (messageId !== undefined && messageId !== parts.messageId) {
      throw new Error('group notifications disagree on messageId');
    }
    messageId = parts.messageId;
    received.set(receiverId, { chatId: parts.chatId, receiptId });
    receiptIds.add(receiptId);
  };

  return {
    async start() {
      try {
        for (const record of ackState.receipts.values()) {
          if (!expected.has(record.receiverId)) continue;
          recordReceipt(record.receiverId, record.receiptId);
          if (!record.completed) sendAck(record.receiverId, record.receiptId);
        }
        finishAfterQuietWindow();
      } catch (error) {
        reject(error);
      }
      return promise;
    },
    reject,
    beginClose(error) {
      closing = true;
      reject(error);
    },
    async drain() {
      await messageQueue;
    },
    seedKnownReceipts(receiptIdsToSeed) {
      messageQueue = messageQueue.then(async () => {
        if (!Array.isArray(receiptIdsToSeed)) throw new Error('known receiptIds must be an array');
        for (const receiptId of receiptIdsToSeed) {
          const { receiverId } = parseReceipt(receiptId);
          const existing = received.get(receiverId);
          if (existing !== undefined) {
            if (existing.receiptId !== receiptId) throw new Error(`duplicate group notification for receiver ${receiverId}`);
            continue;
          }
          recordReceipt(receiverId, receiptId);
          const recovered = ackState.receipts.get(receiptId);
          if (recovered === undefined) {
            if (receiptJournalPath !== undefined) {
              await appendAckRecord(fs, receiptJournalPath, {
                version: 1, batchId, type: 'intent', receiverId, receiptId,
              });
            }
            ackState.receipts.set(receiptId, { receiverId, receiptId, completed: false });
            sendAck(receiverId, receiptId);
          } else if (!recovered.completed) {
            sendAck(receiverId, receiptId);
          }
        }
        finishAfterQuietWindow();
      }).catch(reject);
      return messageQueue;
    },
    onMessage(socketUserId, payload) {
      messageQueue = messageQueue.then(async () => {
        if (settled) return;
        const frame = parseFrame(payload);
        if (frame.type === 'response') {
          const receiverId = pendingAcks.get(frame.seq);
          if (receiverId === undefined) throw new Error(`unknown ACK response sequence ${frame.seq}`);
          if (receiverId !== socketUserId) {
            throw new Error(`ACK response receiver ${receiverId} does not match socket user ${socketUserId}`);
          }
          parseFrame(payload, { type: 'response', cmd: 'notification.ack', seq: frame.seq });
          await hooks.afterAckResponse?.({ receiverId });
          const receiptId = received.get(receiverId).receiptId;
          const receiptState = ackState.receipts.get(receiptId);
          if (!receiptState.completed && receiptJournalPath !== undefined) {
            await appendAckRecord(fs, receiptJournalPath, {
              version: 1, batchId, type: 'completed', receiverId, receiptId,
            });
          }
          receiptState.completed = true;
          pendingAcks.delete(frame.seq);
          await hooks.afterAckCompleted?.({ receiverId });
          finishAfterQuietWindow();
          return;
        }
        const receiverId = frame.authoritativeIds.receiverId;
        if (receiverId !== socketUserId) {
          throw new Error(`notification receiver ${receiverId} does not match socket user ${socketUserId}`);
        }
        if (receiverId === ownerUserId) throw new Error('owner must not receive group creation notification');
        const existing = received.get(receiverId);
        if (existing !== undefined) {
          if (existing.receiptId !== frame.body.receiptId) {
            throw new Error(`duplicate group notification for receiver ${receiverId}`);
          }
          sendAck(receiverId, frame.body.receiptId);
          return;
        }
        recordReceipt(receiverId, frame.body.receiptId);
        if (receiptJournalPath !== undefined) {
          await appendAckRecord(fs, receiptJournalPath, {
            version: 1, batchId, type: 'intent', receiverId, receiptId: frame.body.receiptId,
          });
        }
        ackState.receipts.set(frame.body.receiptId, {
          receiverId, receiptId: frame.body.receiptId, completed: false,
        });
        await hooks.afterAckIntent?.({ receiverId });
        sendAck(receiverId, frame.body.receiptId);
      }).catch(reject);
      return messageQueue;
    },
  };
}

async function recoverAckJournal(fs, filePath, batchId) {
  assertNonEmptyString(batchId, 'batchId');
  const state = emptyAckState();
  let content;
  try {
    content = await fs.readFile(filePath, 'utf8');
  } catch (error) {
    if (error?.code === 'ENOENT') return state;
    throw error;
  }
  if (content !== '' && !content.endsWith('\n')) {
    content = await repairAckJournalTail(fs, filePath, content);
  }
  for (const line of content.split('\n').filter(Boolean)) {
    const record = JSON.parse(line);
    validateAckRecord(record, batchId);
    const current = state.receipts.get(record.receiptId);
    if (record.type === 'intent') {
      if (current !== undefined) throw new Error('duplicate setup ACK intent');
      state.receipts.set(record.receiptId, { ...record, completed: false });
    } else {
      if (current === undefined || current.receiverId !== record.receiverId || current.completed) {
        throw new Error('invalid setup ACK completion');
      }
      current.completed = true;
    }
  }
  return state;
}

async function repairAckJournalTail(fs, filePath, content) {
  const lastNewline = content.lastIndexOf('\n');
  const tail = content.slice(lastNewline + 1);
  try {
    JSON.parse(tail);
    const handle = await fs.open(filePath, 'a', 0o600);
    try {
      await handle.writeFile('\n');
      await handle.sync();
    } finally {
      await handle.close();
    }
    return `${content}\n`;
  } catch {
    const repaired = content.slice(0, lastNewline + 1);
    const handle = await fs.open(filePath, 'r+');
    try {
      await handle.truncate(Buffer.byteLength(repaired));
      await handle.sync();
    } finally {
      await handle.close();
    }
    const directory = await fs.open(dirname(filePath), 'r');
    try {
      await directory.sync();
    } finally {
      await directory.close();
    }
    return repaired;
  }
}

async function appendAckRecord(fs, filePath, record) {
  validateAckRecord(record, record.batchId);
  await fs.mkdir(dirname(filePath), { recursive: true });
  const handle = await fs.open(filePath, 'a', 0o600);
  try {
    if (typeof handle.chmod === 'function') await handle.chmod(0o600);
    await handle.writeFile(`${JSON.stringify(record)}\n`);
    await handle.sync();
  } finally {
    await handle.close();
  }
  const directory = await fs.open(dirname(filePath), 'r');
  try {
    await directory.sync();
  } finally {
    await directory.close();
  }
}

function validateAckRecord(record, batchId) {
  if (record?.version !== 1 || record.batchId !== batchId
      || !['intent', 'completed'].includes(record.type)) throw new Error('invalid setup ACK record');
  assertCanonicalId(record.receiverId, 'receiverId');
  if (parseReceipt(record.receiptId).receiverId !== record.receiverId) {
    throw new Error('setup ACK record receiver mismatch');
  }
}

function parseReceipt(receiptId) {
  if (typeof receiptId !== 'string') throw new Error('receiptId must be a string');
  const [type, receiverId, chatId, messageId, extra] = receiptId.split(':');
  if (type !== 'GROUP_MESSAGE_SEND' || extra !== undefined) throw new Error('unsupported setup receiptId');
  [receiverId, chatId, messageId].forEach((value) => assertCanonicalId(value, 'receiptId segment'));
  return { receiverId, chatId, messageId };
}

function emptyAckState() {
  return { receipts: new Map() };
}

function waitForOpen(socket, userId, timeoutMs, scheduler) {
  if (socket.readyState === socket.OPEN) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timeout = scheduler.setTimeout(() => {
      cleanup();
      reject(new Error(`setup websocket open timed out for user ${userId}`));
    }, timeoutMs);
    const cleanup = () => {
      scheduler.clearTimeout(timeout);
      socket.off('open', onOpen);
      socket.off('error', onError);
    };
    const onOpen = () => { cleanup(); resolve(); };
    const onError = () => { cleanup(); reject(new Error(`setup websocket open failed for user ${userId}`)); };
    socket.once('open', onOpen);
    socket.once('error', onError);
  });
}

async function closeSockets(sockets, scheduler) {
  await Promise.all([...sockets.values()].map((socket) => new Promise((resolve) => {
    if (socket.readyState === socket.CLOSED) return resolve();
    let timeout;
    const done = () => {
      if (timeout !== undefined) scheduler.clearTimeout(timeout);
      socket.off('close', done);
      resolve();
    };
    socket.once('close', done);
    if (socket.readyState === socket.CONNECTING) socket.terminate();
    else socket.close();
    timeout = scheduler.setTimeout(() => { socket.terminate(); done(); }, 1_000);
  })));
}

function revalidateConfig(config) {
  const connectionLevels = config?.levels?.connection;
  const messageLevels = config?.levels?.message;
  return resolveConfig(Array.isArray(connectionLevels) ? config : {
    ...config,
    levels: { connection: connectionLevels?.requested, message: messageLevels?.requested },
  });
}

function validateCredentials(credentials) {
  if (!Array.isArray(credentials) || credentials.length === 0) throw new Error('credentials must be non-empty');
  const users = new Set();
  for (const credential of credentials) {
    assertCanonicalId(credential?.userId, 'credential userId');
    assertNonEmptyString(credential.accessToken, 'credential accessToken');
    if (users.has(credential.userId)) throw new Error('duplicate credential userId');
    users.add(credential.userId);
  }
}

function defaultSequence() {
  let value = 0;
  return () => `setup-ack-${++value}`;
}

function defaultTraceId() {
  let value = 0;
  return () => `setup-trace-${++value}`;
}

function assertCanonicalId(value, name) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new Error(`${name} must be a canonical positive decimal string`);
  }
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
}

function assertNonNegativeInteger(value, name) {
  if (!Number.isInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`);
}

function assertNonEmptyString(value, name) {
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${name} must be a non-empty string`);
}
