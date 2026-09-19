import assert from 'node:assert/strict';
import { mkdtemp, readFile, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, test } from 'node:test';
import { WebSocketServer } from 'ws';

import { resolveConfig } from '../lib/config.js';
import { openSetupWebSockets } from '../tools/lib/setup-websocket.mjs';

const defaults = JSON.parse(await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'));
const cleanups = [];

afterEach(async () => {
  await Promise.all(cleanups.splice(0).map((cleanup) => cleanup()));
});

async function runtime({
  ackCode = 0,
  ackOnWrongSocket = false,
  suppressAck = false,
  rejectUpgrades = 0,
} = {}) {
  const server = createServer();
  let remainingRejectedUpgrades = rejectUpgrades;
  const webSocketServer = new WebSocketServer({
    server,
    verifyClient(info, done) {
      if (remainingRejectedUpgrades > 0) {
        remainingRejectedUpgrades -= 1;
        done(false, 503, 'route not ready');
      } else {
        done(true);
      }
    },
  });
  const sockets = new Map();
  const closed = new Set();
  let resolveAllClosed;
  const allClosed = new Promise((resolve) => { resolveAllClosed = resolve; });
  const acks = [];
  webSocketServer.on('connection', (socket, request) => {
    const token = new URL(request.url, 'ws://localhost').searchParams.get('token');
    const userId = token.replace('token-', '');
    sockets.set(userId, socket);
    socket.on('close', () => {
      closed.add(userId);
      if (closed.size === 2) resolveAllClosed();
    });
    socket.on('message', (payload) => {
      const frame = JSON.parse(payload.toString());
      acks.push({ userId, frame });
      if (suppressAck) return;
      const responseSocket = ackOnWrongSocket ? sockets.get('101') : socket;
      responseSocket.send(JSON.stringify({
        version: '1', type: 'response', cmd: 'notification.ack', seq: frame.seq,
        traceId: frame.traceId, code: ackCode, message: ackCode === 0 ? 'success' : 'ack failed', body: {},
      }));
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  cleanups.push(async () => {
    for (const socket of sockets.values()) socket.terminate();
    await new Promise((resolve) => webSocketServer.close(resolve));
    await new Promise((resolve) => server.close(resolve));
  });
  return {
    config: resolveConfig(defaults, { targets: { websocket: `ws://127.0.0.1:${port}/ws` } }),
    sockets,
    closed,
    allClosed,
    acks,
  };
}

function push(receiverId, chatId, messageId = '7000000000000000001') {
  const receiptId = `GROUP_MESSAGE_SEND:${receiverId}:${chatId}:${messageId}`;
  return JSON.stringify({
    version: '1', type: 'push', cmd: 'message.group.sent', seq: null, traceId: 'server-trace',
    code: null, message: null,
    body: {
      eventId: receiptId,
      receiptId,
      notification: { messageContent: 'group created' },
    },
  });
}

async function open(runtimeState, scheduler, options = {}) {
  return openSetupWebSockets({
    config: runtimeState.config,
    credentials: ['101', '102'].map((userId) => ({ userId, accessToken: `token-${userId}` })),
    scheduler,
    retryHorizonMs: 5,
    quietMs: 5,
    sequence: (() => { let value = 0; return () => `setup-${++value}`; })(),
    traceId: (() => { let value = 0; return () => `trace-${++value}`; })(),
    ...options,
  });
}

test('waits for each non-owner notification and its successful ACK', async () => {
  const state = await runtime();
  const setup = await open(state);
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  state.sockets.get('102').send(push('102', '8000000000000000001'));
  const result = await barrier;

  assert.deepEqual(result, {
    messageId: '7000000000000000001',
    memberChats: [{ userId: '102', chatId: '8000000000000000001' }],
    receiptIds: ['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001'],
  });
  assert.equal(state.acks.length, 1);
  assert.deepEqual(state.acks[0].frame.body, {
    chatId: '8000000000000000001',
    messageId: '7000000000000000001',
    receiptType: 'GROUP_MESSAGE_SEND',
  });
  await setup.close();
  await state.allClosed;
  assert.deepEqual([...state.closed].sort(), ['101', '102']);
});

test('retries setup websocket opens while service routes converge', async () => {
  const state = await runtime({ rejectUpgrades: 2 });
  const setup = await open(state, globalThis, { openAttempts: 3, openRetryMs: 5 });

  assert.deepEqual([...state.sockets.keys()].sort(), ['101', '102']);
  await setup.close();
});

for (const scenario of [
  {
    name: 'rejects an owner notification',
    send(state) { state.sockets.get('101').send(push('101', '8000000000000000000')); },
    pattern: /owner.*notification/i,
  },
  {
    name: 'rejects a different receipt for the same receiver',
    send(state) {
      state.sockets.get('102').send(push('102', '8000000000000000001'));
      state.sockets.get('102').send(push('102', '8000000000000000001', '7000000000000000002'));
    },
    pattern: /duplicate|disagree/i,
  },
  {
    name: 'rejects a receipt delivered on the wrong receiver socket',
    send(state) { state.sockets.get('102').send(push('999', '8000000000000000001')); },
    pattern: /receiver/i,
  },
  {
    name: 'rejects an unknown receipt type',
    send(state) {
      state.sockets.get('102').send(push('102', '8000000000000000001').replace('GROUP_MESSAGE_SEND', 'UNKNOWN'));
    },
    pattern: /receiptId|supported/i,
  },
]) {
  test(scenario.name, async () => {
    const state = await runtime();
    const setup = await open(state);
    const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
    scenario.send(state);
    await assert.rejects(barrier, scenario.pattern);
    await setup.close();
  });
}

test('idempotently ACKs a retry of the same group notification', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  const setup = await open(state, undefined, {
    batchId: 'ack-same-retry', receiptJournalPath: journalPath,
  });
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  const notification = push('102', '8000000000000000001');

  state.sockets.get('102').send(notification);
  state.sockets.get('102').send(notification);
  const result = await barrier;

  assert.equal(state.acks.length, 2);
  assert.deepEqual(result.receiptIds, ['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001']);
  const records = (await readFile(journalPath, 'utf8')).trim().split('\n').map(JSON.parse);
  assert.equal(records.filter(({ type }) => type === 'intent').length, 1);
  await setup.close();
});

test('rejects a missing notification at the bounded barrier', async () => {
  const state = await runtime();
  let expire;
  const scheduler = {
    setTimeout(callback) { expire = callback; return 1; },
    clearTimeout() {},
  };
  const setup = await open(state, scheduler);
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  expire();

  await assert.rejects(barrier, /missing.*notification|timed out/i);
  await setup.close();
});

test('notification deadline no longer races the quiet window after every ACK completes', async () => {
  const state = await runtime();
  let timerId = 0;
  const timers = new Map();
  const scheduler = {
    setTimeout(callback, delay) {
      const id = ++timerId;
      timers.set(id, { callback, delay, active: true });
      return id;
    },
    clearTimeout(id) {
      const timer = timers.get(id);
      if (timer !== undefined) timer.active = false;
    },
    run(delay) {
      for (const timer of timers.values()) {
        if (timer.active && timer.delay === delay) {
          timer.active = false;
          timer.callback();
        }
      }
    },
  };
  const setup = await open(state, scheduler, { timeoutMs: 10 });
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  state.sockets.get('102').send(push('102', '8000000000000000001'));
  while (![...timers.values()].some(({ active, delay }) => active && delay === 5)) {
    await new Promise((resolve) => setImmediate(resolve));
  }
  scheduler.run(10);
  scheduler.run(5);

  await barrier;
  await setup.close();
});

test('rejects a nonzero ACK response and closes every socket', async () => {
  const state = await runtime({ ackCode: -1 });
  const setup = await open(state);
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  state.sockets.get('102').send(push('102', '8000000000000000001'));

  await assert.rejects(barrier, /frame business code -1|ack failed/i);
  await setup.close();
  await state.allClosed;
  assert.deepEqual([...state.closed].sort(), ['101', '102']);
});

test('rejects an ACK response received on a different user socket', async () => {
  const state = await runtime({ ackOnWrongSocket: true });
  const setup = await open(state);
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  state.sockets.get('102').send(push('102', '8000000000000000001'));

  await assert.rejects(barrier, /ACK response.*socket|receiver/i);
  await setup.close();
});

test('restarts the quiet horizon for a retry arriving after ACK completion', async () => {
  const state = await runtime();
  const setup = await open(state, undefined, { retryHorizonMs: 20, quietMs: 20 });
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  setImmediate(() => state.sockets.get('102').send(push('102', '8000000000000000001')));

  const result = await barrier;

  assert.equal(state.acks.length, 2);
  assert.deepEqual(result.receiptIds, ['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001']);
  await setup.close();
});

test('requires the quiet interval to cover the complete retry horizon', async () => {
  const state = await runtime();
  await assert.rejects(
    open(state, undefined, { retryHorizonMs: 20, quietMs: 19 }),
    /quiet.*retry horizon/i,
  );
});

async function ackJournalPath() {
  return join(await mkdtemp(join(tmpdir(), 'im-load-ack-')), 'setup-receipts.jsonl');
}

test('fsyncs a 0600 ACK intent before send and resends it after restart without another push', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  const first = await open(state, undefined, {
    batchId: 'ack-intent-crash',
    receiptJournalPath: journalPath,
    hooks: { afterAckIntent() { throw new Error('crash after ack intent'); } },
  });
  const firstBarrier = first.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  await assert.rejects(firstBarrier, /crash after ack intent/);
  await first.close();
  assert.equal(state.acks.length, 0);
  assert.equal((await stat(journalPath)).mode & 0o777, 0o600);

  const second = await open(state, undefined, {
    batchId: 'ack-intent-crash', receiptJournalPath: journalPath,
  });
  const result = await second.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  assert.equal(state.acks.length, 1);
  assert.deepEqual(result.receiptIds, ['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001']);
  await second.close();
});

test('resends an ACK after restart when response arrived before completion was fsynced', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  const first = await open(state, undefined, {
    batchId: 'ack-response-crash',
    receiptJournalPath: journalPath,
    hooks: { afterAckResponse() { throw new Error('crash after ack response'); } },
  });
  const firstBarrier = first.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  await assert.rejects(firstBarrier, /crash after ack response/);
  await first.close();
  assert.equal(state.acks.length, 1);

  const second = await open(state, undefined, {
    batchId: 'ack-response-crash', receiptJournalPath: journalPath,
  });
  await second.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  assert.equal(state.acks.length, 2);
  const records = (await readFile(journalPath, 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(records.map(({ type }) => type), ['intent', 'completed']);
  await second.close();
});

test('durably seeds DB-reconciled receipts and completes ACK without another push', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  const setup = await open(state, undefined, {
    batchId: 'ack-db-seed', receiptJournalPath: journalPath,
  });
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  await setup.seedKnownReceipts(['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001']);
  const result = await barrier;

  assert.equal(state.acks.length, 1);
  assert.deepEqual(result.receiptIds, ['GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001']);
  const records = (await readFile(journalPath, 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(records.map(({ type }) => type), ['intent', 'completed']);
  await setup.close();
});

test('repairs a torn final ACK journal record and resends the last durable intent', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  const receiptId = 'GROUP_MESSAGE_SEND:102:8000000000000000001:7000000000000000001';
  const durableIntent = JSON.stringify({
    version: 1, batchId: 'ack-torn-tail', type: 'intent', receiverId: '102', receiptId,
  });
  const { writeFile } = await import('node:fs/promises');
  await writeFile(journalPath, `${durableIntent}\n{"version":1,"batchId":"ack-torn`, { mode: 0o600 });

  const setup = await open(state, undefined, {
    batchId: 'ack-torn-tail', receiptJournalPath: journalPath,
  });
  const result = await setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });

  assert.deepEqual(result.receiptIds, [receiptId]);
  assert.equal(state.acks.length, 1);
  const content = await readFile(journalPath, 'utf8');
  assert.equal(content.endsWith('\n'), true);
  assert.deepEqual(content.trim().split('\n').map((line) => JSON.parse(line).type), ['intent', 'completed']);
  await setup.close();
});

test('close drains an entered push to a stable durable intent without sending on closing sockets', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  let releaseIntent;
  let markIntentEntered;
  const intentEntered = new Promise((resolve) => { markIntentEntered = resolve; });
  const intentGate = new Promise((resolve) => { releaseIntent = resolve; });
  const setup = await open(state, undefined, {
    batchId: 'ack-close-drain',
    receiptJournalPath: journalPath,
    hooks: {
      async afterAckIntent() {
        markIntentEntered();
        await intentGate;
      },
    },
  });
  const barrier = setup.waitForGroupCreated({ ownerUserId: '101', memberUserIds: ['102'] });
  const barrierOutcome = barrier.catch((error) => error);
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  await intentEntered;

  let closeFinished = false;
  const closing = setup.close().then(() => { closeFinished = true; });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(closeFinished, false);
  releaseIntent();
  await closing;

  assert.match((await barrierOutcome).message, /closed/i);
  const completed = await readFile(journalPath, 'utf8');
  assert.deepEqual(completed.trim().split('\n').map((line) => JSON.parse(line).type), ['intent']);
  assert.equal(state.acks.length, 0);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(await readFile(journalPath, 'utf8'), completed);
});

test('close drains a response handler that already entered through durable completion', async () => {
  const state = await runtime();
  const journalPath = await ackJournalPath();
  let releaseResponse;
  let markResponseEntered;
  const responseEntered = new Promise((resolve) => { markResponseEntered = resolve; });
  const responseGate = new Promise((resolve) => { releaseResponse = resolve; });
  const setup = await open(state, undefined, {
    batchId: 'ack-response-drain',
    receiptJournalPath: journalPath,
    hooks: {
      async afterAckResponse() {
        markResponseEntered();
        await responseGate;
      },
    },
  });
  const barrierOutcome = setup.waitForGroupCreated({
    ownerUserId: '101', memberUserIds: ['102'],
  }).catch((error) => error);
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  await responseEntered;

  let closeFinished = false;
  const closing = setup.close().then(() => { closeFinished = true; });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(closeFinished, false);
  releaseResponse();
  await closing;

  assert.match((await barrierOutcome).message, /closed/i);
  const completed = await readFile(journalPath, 'utf8');
  assert.deepEqual(completed.trim().split('\n').map((line) => JSON.parse(line).type), ['intent', 'completed']);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(await readFile(journalPath, 'utf8'), completed);
});

test('close returns without waiting for a future ACK response and leaves a recoverable intent', async () => {
  const state = await runtime({ suppressAck: true });
  const journalPath = await ackJournalPath();
  const setup = await open(state, undefined, {
    batchId: 'ack-no-response', receiptJournalPath: journalPath,
  });
  const barrierOutcome = setup.waitForGroupCreated({
    ownerUserId: '101', memberUserIds: ['102'],
  }).catch((error) => error);
  state.sockets.get('102').send(push('102', '8000000000000000001'));
  while (state.acks.length === 0) await new Promise((resolve) => setImmediate(resolve));

  const closing = setup.close();
  let timeout;
  const returnedWithoutResponse = await Promise.race([
    closing.then(() => true),
    new Promise((resolve) => { timeout = setTimeout(() => resolve(false), 250); }),
  ]);
  clearTimeout(timeout);
  if (!returnedWithoutResponse) {
    const ack = state.acks[0].frame;
    state.sockets.get('102').send(JSON.stringify({
      version: '1', type: 'response', cmd: 'notification.ack', seq: ack.seq,
      traceId: ack.traceId, code: 0, message: 'success', body: {},
    }));
  }
  await closing;

  assert.match((await barrierOutcome).message, /closed/i);
  assert.equal(returnedWithoutResponse, true);
  const pending = await readFile(journalPath, 'utf8');
  assert.deepEqual(pending.trim().split('\n').map((line) => JSON.parse(line).type), ['intent']);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(await readFile(journalPath, 'utf8'), pending);
});
