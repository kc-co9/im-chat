import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import test, { afterEach } from 'node:test';

import WebSocket from 'ws';

import { buildCorrelation } from '../lib/correlation.js';
import { buildMessageToken } from '../lib/message-scheduler.js';
import { buildAckFrame, parseFrame, parseHttpResult } from '../lib/protocol.js';
import { readManifest } from '../tools/lib/manifest.mjs';
import { startMockRuntime } from '../tools/mock-runtime.mjs';

const cleanups = [];

afterEach(async () => {
  await Promise.all(cleanups.splice(0).map((cleanup) => cleanup()));
});

test('signIn returns string IDs and token-authenticated websocket routes with deterministic ownership', async () => {
  const runtime = await startMockRuntime({ userCount: 4, groupSize: 2 });
  cleanups.push(() => runtime.close());
  const login = await signIn(runtime.ready.httpUrl, 3);

  assert.equal(login.userId, '9007199254740996');
  assert.equal(typeof login.userId, 'string');
  const socket = await openWebSocket(`${runtime.ready.websocketUrl}?token=${login.accessToken}`);
  const route = await getResult(`${runtime.ready.routeStatusUrl}/${login.userId}`);
  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);

  assert.deepEqual(route, {
    userId: login.userId,
    active: true,
    cleanupState: 'active',
  });
  assert.deepEqual(evidence.opens, [{
    ordinal: 3,
    userId: login.userId,
    cohortIndex: 1,
    memberIndex: 1,
  }]);
  assert.equal(evidence.activeRoutes, 1);
  assert.equal(evidence.handshakeFailures, 0);

  const closed = once(socket, 'close');
  socket.close(1000, 'controlled');
  await closed;
  await waitFor(async () => !(await getResult(
    `${runtime.ready.routeStatusUrl}/${login.userId}`,
  )).active);
});

test('websocket handshake rejects an invalid token with HTTP 401 and no active route', async () => {
  const runtime = await startMockRuntime({ userCount: 2, groupSize: 2 });
  cleanups.push(() => runtime.close());

  const status = await rejectedHandshake(`${runtime.ready.websocketUrl}?token=invalid`);
  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);

  assert.equal(status, 401);
  assert.equal(evidence.handshakeFailures, 1);
  assert.equal(evidence.activeRoutes, 0);
  assert.deepEqual(evidence.opens, []);
});

test('configured unexpected disconnect retains the route until delayed cleanup completes', async () => {
  const runtime = await startMockRuntime({
    userCount: 2,
    groupSize: 2,
    unexpectedDisconnect: { ordinal: 0, afterMs: 10 },
    routeCleanupDelayMs: 25,
  });
  cleanups.push(() => runtime.close());
  const login = await signIn(runtime.ready.httpUrl, 0);
  const socket = await openWebSocket(`${runtime.ready.websocketUrl}?token=${login.accessToken}`);

  const [code, reason] = await once(socket, 'close');
  const pending = await getResult(`${runtime.ready.routeStatusUrl}/${login.userId}`);
  assert.equal(code, 1011);
  assert.equal(reason.toString(), 'mock unexpected disconnect');
  assert.deepEqual(pending, {
    userId: login.userId,
    active: true,
    cleanupState: 'pending',
  });

  await waitFor(async () => !(await getResult(
    `${runtime.ready.routeStatusUrl}/${login.userId}`,
  )).active);
  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);
  assert.deepEqual(evidence.unexpectedDisconnects, [{ ordinal: 0, userId: login.userId }]);
  assert.deepEqual(evidence.routeCleanups, [{ ordinal: 0, userId: login.userId, outcome: 'cleaned' }]);
});

test('configured route cleanup timeout remains observable without deleting the active route', async () => {
  const runtime = await startMockRuntime({
    userCount: 2,
    groupSize: 2,
    routeCleanupTimeoutOrdinals: [1],
    routeCleanupTimeoutMs: 15,
  });
  cleanups.push(() => runtime.close());
  const login = await signIn(runtime.ready.httpUrl, 1);
  const socket = await openWebSocket(`${runtime.ready.websocketUrl}?token=${login.accessToken}`);
  const closed = once(socket, 'close');
  socket.close(1000, 'controlled');
  await closed;

  await waitFor(async () => (await getResult(
    `${runtime.ready.routeStatusUrl}/${login.userId}`,
  )).cleanupState === 'timed_out');
  const route = await getResult(`${runtime.ready.routeStatusUrl}/${login.userId}`);
  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);

  assert.equal(route.active, true);
  assert.equal(route.cleanupState, 'timed_out');
  assert.deepEqual(evidence.routeCleanups, [{
    ordinal: 1,
    userId: login.userId,
    outcome: 'timed_out',
  }]);
});

test('private send responds, pushes to the next cohort member, and accepts its receipt ACK', async () => {
  const runtime = await startMockRuntime({ userCount: 4, groupSize: 4 });
  cleanups.push(() => runtime.close());
  const logins = await Promise.all(Array.from({ length: 4 }, (_, ordinal) => (
    signIn(runtime.ready.httpUrl, ordinal)
  )));
  const sockets = await Promise.all(logins.map(({ accessToken }) => (
    openWebSocket(`${runtime.ready.websocketUrl}?token=${accessToken}`)
  )));
  const messageContent = buildCorrelation({
    batch: 'mock-private',
    round: '1',
    phase: 'measure',
    sequence: '7',
  });
  const messageToken = buildMessageToken({
    batch: 'mock-private', round: '1', phase: 'measure', sequence: '7',
  });
  const responsePromise = nextFrame(sockets[0]);
  const pushPromise = nextFrame(sockets[1]);

  sockets[0].send(JSON.stringify({
    version: '1',
    cmd: 'message.private.send',
    seq: 'send-7',
    traceId: 'trace-send-7',
    body: {
      chatId: '6100000000000000000',
      messageToken,
      messageType: 'TEXT',
      messageContent,
    },
  }));

  assert.deepEqual(parseFrame(await responsePromise), {
    version: '1',
    type: 'response',
    cmd: 'message.private.send',
    seq: 'send-7',
    traceId: 'trace-send-7',
    code: 0,
    message: 'OK',
    body: {},
  });
  const push = JSON.parse(await pushPromise);
  const { sendTime, ...notification } = push.body.notification;
  assert.match(sendTime, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}$/);
  assert.deepEqual(push, {
    version: '1',
    type: 'push',
    cmd: 'message.private.sent',
    seq: null,
    traceId: null,
    code: null,
    message: null,
    body: {
      eventId: push.body.receiptId,
      receiptId: push.body.receiptId,
      notification: { ...notification, sendTime },
    },
  });
  assert.deepEqual(notification, {
    messageId: push.body.receiptId.split(':')[3],
    chatId: '6100000000000000001',
    senderId: logins[0].userId,
    receiverId: logins[1].userId,
    messageType: 'TEXT',
    messageContent,
  });
  assert.equal(
    push.body.receiptId,
    `PRIVATE_MESSAGE_SEND:${logins[1].userId}:6100000000000000001:${notification.messageId}`,
  );

  const ackResponsePromise = nextFrame(sockets[1]);
  sockets[1].send(JSON.stringify(buildAckFrame({
    receiptId: push.body.receiptId,
    sequence: 'ack-7',
    traceId: 'trace-ack-7',
  })));
  assert.deepEqual(parseFrame(await ackResponsePromise), {
    version: '1',
    type: 'response',
    cmd: 'notification.ack',
    seq: 'ack-7',
    traceId: 'trace-ack-7',
    code: 0,
    message: 'OK',
    body: {},
  });

  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);
  for (const activity of [evidence.messageSends, evidence.messageDeliveries, evidence.messageAcks]) {
    assert.equal(activity.count, 1);
    assert.equal(activity.recent.length, 1);
    assert.equal(activity.recent[0].ordinal, '7');
    assert.match(activity.recent[0].tokenDigest, /^sha256:[0-9a-f]{16}$/);
  }
  const serializedEvidence = JSON.stringify(evidence);
  assert.equal(serializedEvidence.includes(messageContent), false);
  for (const login of logins) assert.equal(serializedEvidence.includes(login.accessToken), false);
  assert.equal(serializedEvidence.includes('mock-password'), false);
});

test('group send excludes the sender and accounts every other cohort member ACK', async () => {
  const runtime = await startMockRuntime({ userCount: 4, groupSize: 4 });
  cleanups.push(() => runtime.close());
  const logins = await Promise.all(Array.from({ length: 4 }, (_, ordinal) => signIn(runtime.ready.httpUrl, ordinal)));
  const sockets = await Promise.all(logins.map(({ accessToken }) => (
    openWebSocket(`${runtime.ready.websocketUrl}?token=${accessToken}`)
  )));
  const messageContent = buildCorrelation({
    batch: 'mock-group', round: '1', phase: 'measure', sequence: '7',
  });
  const messageToken = buildMessageToken({
    batch: 'mock-group', round: '1', phase: 'measure', sequence: '7',
  });
  const responsePromise = nextFrame(sockets[3]);
  const pushPromises = [0, 1, 2].map((index) => nextFrame(sockets[index]));
  sockets[3].send(JSON.stringify({
    version: '1', cmd: 'message.group.send', seq: 'group-7', traceId: 'trace-group-7',
    body: {
      chatId: '6200000000000000003', messageToken, messageType: 'TEXT', messageContent,
    },
  }));

  assert.equal(parseFrame(await responsePromise).code, 0);
  const pushes = await Promise.all(pushPromises.map(async (promise) => JSON.parse(await promise)));
  assert.deepEqual(pushes.map((push) => push.body.notification.receiverId), logins.slice(0, 3).map(({ userId }) => userId));
  assert.equal(new Set(pushes.map((push) => push.body.receiptId)).size, 3);
  assert.equal(pushes.every((push) => push.cmd === 'message.group.sent'), true);

  await Promise.all(pushes.map(async (push, index) => {
    const response = nextFrame(sockets[index]);
    sockets[index].send(JSON.stringify(buildAckFrame({
      receiptId: push.body.receiptId,
      sequence: `group-ack-${index}`,
      traceId: `group-ack-trace-${index}`,
    })));
    assert.equal(parseFrame(await response).code, 0);
  }));
  const evidence = await getResult(`${runtime.ready.httpUrl}/mock/evidence`);
  assert.equal(evidence.messageSends.count, 1);
  assert.equal(evidence.messageDeliveries.count, 3);
  assert.equal(evidence.messageAcks.count, 3);
});

test('CLI publishes non-sensitive ready JSON and exits cleanly on SIGTERM', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'im-load-mock-runtime-'));
  cleanups.push(() => rm(directory, { recursive: true, force: true }));
  const readyFile = join(directory, 'ready.json');
  const seedDirectory = join(directory, 'seed');
  const child = spawn(process.execPath, [
    new URL('../tools/mock-runtime.mjs', import.meta.url).pathname,
    '--ready-file', readyFile,
    '--users', '2',
    '--group-size', '2',
    '--seed-directory', seedDirectory,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  child.stdout.setEncoding('utf8');
  child.stderr.setEncoding('utf8');
  child.stdout.on('data', (chunk) => { stdout += chunk; });
  child.stderr.on('data', (chunk) => { stderr += chunk; });
  cleanups.push(async () => {
    if (child.exitCode === null && child.signalCode === null) {
      const exited = once(child, 'exit');
      child.kill('SIGKILL');
      await exited.catch(() => undefined);
    }
  });

  const ready = await waitFor(async () => {
    try {
      return JSON.parse(await readFile(readyFile, 'utf8'));
    } catch (error) {
      if (error.code === 'ENOENT' || error instanceof SyntaxError) return undefined;
      throw error;
    }
  });
  assert.equal(ready.boundary, 'mock-runtime');
  assert.match(ready.httpUrl, /^http:\/\/127\.0\.0\.1:\d+$/);
  assert.match(ready.websocketUrl, /^ws:\/\/127\.0\.0\.1:\d+\/ws$/);
  assert.equal(new URL(ready.httpUrl).port === '0', false);
  await waitFor(async () => stdout.trim() || undefined);
  assert.equal(stdout.includes('accessToken'), false);
  assert.equal(stdout.includes('password'), false);
  assert.deepEqual(JSON.parse(stdout.trim()), ready);
  const manifest = await readManifest(join(seedDirectory, 'manifest.json'));
  const credentials = JSON.parse(await readFile(join(seedDirectory, 'credentials.json'), 'utf8'));
  assert.equal(manifest.users.length, 2);
  assert.equal(manifest.cohorts.length, 1);
  assert.equal(credentials.credentials.length, 2);
  assert.equal((await stat(join(seedDirectory, 'credentials.json'))).mode & 0o777, 0o600);

  const exited = once(child, 'exit');
  child.kill('SIGTERM');
  const [exitCode, signal] = await exited;
  assert.equal(exitCode, 0, stderr);
  assert.equal(signal, null, stderr);
});

async function signIn(httpUrl, ordinal) {
  const response = await fetch(`${httpUrl}/account/user/signIn`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      email: `loadtest-mock-${ordinal.toString(36).padStart(3, '0')}@example.invalid`,
      password: `mock-password-${ordinal}`,
    }),
  });
  assert.equal(response.status, 200);
  return parseHttpResult(await response.text());
}

async function getResult(url) {
  const response = await fetch(url);
  assert.equal(response.status, 200);
  return parseHttpResult(await response.text());
}

function openWebSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const timeout = setTimeout(() => reject(new Error('websocket open timed out')), 1_000);
    socket.once('open', () => {
      clearTimeout(timeout);
      resolve(socket);
    });
    socket.once('error', (error) => {
      clearTimeout(timeout);
      reject(error);
    });
  });
}

function rejectedHandshake(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const timeout = setTimeout(() => reject(new Error('websocket rejection timed out')), 1_000);
    socket.once('unexpected-response', (_request, response) => {
      clearTimeout(timeout);
      response.resume();
      resolve(response.statusCode);
    });
    socket.once('open', () => {
      clearTimeout(timeout);
      reject(new Error('invalid token unexpectedly opened websocket'));
    });
    socket.on('error', () => undefined);
  });
}

function nextFrame(socket, timeoutMs = 1_000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      socket.off('message', onMessage);
      reject(new Error('websocket frame timed out'));
    }, timeoutMs);
    const onMessage = (payload) => {
      clearTimeout(timeout);
      resolve(payload.toString());
    };
    socket.once('message', onMessage);
  });
}

async function waitFor(probe, timeoutMs = 1_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await probe();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  throw new Error(`condition not met within ${timeoutMs}ms`);
}
