// Test fixture only: /mock diagnostics and controls do not model production APIs.
import { createHash, randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

import WebSocket, { WebSocketServer } from 'ws';

import { parseCorrelation } from '../lib/correlation.js';
import { buildMessageToken } from '../lib/message-scheduler.js';
import { publishCredentialManifest, publishManifest } from './lib/manifest.mjs';

const LOOPBACK_HOST = '127.0.0.1';
const USER_ID_BASE = 9_007_199_254_740_993n;
const DEFAULT_USER_COUNT = 2;
const DEFAULT_GROUP_SIZE = 2;
const DEFAULT_CLEANUP_DELAY_MS = 0;
const DEFAULT_CLEANUP_TIMEOUT_MS = 100;
const DEFAULT_CLOSE_TIMEOUT_MS = 1_000;
const MAX_REQUEST_BYTES = 16 * 1024;

export async function startMockRuntime(options = {}) {
  const config = validateOptions(options);
  const fixtureData = buildMockSeedData(config.userCount, config.groupSize);
  const privateChatByUsers = new Map(fixtureData.manifest.privateChats.map((chat) => (
    [`${chat.userId}:${chat.peerUserId}`, chat.chatId]
  )));
  const groupChatByUser = new Map(fixtureData.manifest.groupChats.map((chat) => [chat.userId, chat.chatId]));
  const credentialsByToken = new Map();
  const credentialsByOrdinal = new Map();
  const routes = new Map();
  const sockets = new Set();
  const transportSockets = new Set();
  const timers = new Set();
  const pendingReceipts = new Map();
  const completedReceipts = new Map();
  let nextMessageId = 6_400_000_000_000_000_000n;
  const evidence = {
    opens: [],
    handshakeFailures: 0,
    unexpectedDisconnects: [],
    routeCleanups: [],
    messageSends: activityEvidence(),
    messageDeliveries: activityEvidence(),
    messageAcks: activityEvidence(),
    messageAckFailures: activityEvidence(),
  };
  let closing = false;
  let closePromise;

  const server = createServer((request, response) => {
    void handleRequest(request, response).catch(() => {
      if (!response.headersSent) sendError(response, 500, 'mock runtime request failed');
      else response.destroy();
    });
  });
  const websocketServer = new WebSocketServer({ noServer: true });

  server.on('connection', (socket) => {
    transportSockets.add(socket);
    socket.once('close', () => transportSockets.delete(socket));
  });

  server.on('upgrade', (request, socket, head) => {
    if (closing) {
      rejectUpgrade(socket, 503, 'Service Unavailable');
      return;
    }

    const target = new URL(request.url ?? '/', 'http://mock-runtime.invalid');
    if (target.pathname !== '/ws') {
      rejectUpgrade(socket, 404, 'Not Found');
      return;
    }

    const credential = credentialsByToken.get(target.searchParams.get('token'));
    if (credential === undefined) {
      evidence.handshakeFailures += 1;
      rejectUpgrade(socket, 401, 'Unauthorized');
      return;
    }

    websocketServer.handleUpgrade(request, socket, head, (websocket) => {
      websocketServer.emit('connection', websocket, request, credential);
    });
  });

  websocketServer.on('connection', (socket, _request, credential) => {
    sockets.add(socket);
    const existing = routes.get(credential.userId);
    if (existing?.cleanupTimer !== undefined) clearTrackedTimer(existing.cleanupTimer);

    const route = {
      ordinal: credential.ordinal,
      userId: credential.userId,
      active: true,
      cleanupState: 'active',
      socket,
      cleanupTimer: undefined,
      cleanupScheduled: false,
    };
    routes.set(credential.userId, route);
    evidence.opens.push(publicOwnership(credential));
    socket.on('message', (payload) => {
      try {
        handleSocketFrame(socket, credential, payload.toString());
      } catch (error) {
        sendFrame(socket, frameError(undefined, error.message));
      }
    });

    let unexpectedTimer;
    if (config.unexpectedDisconnect?.ordinal === credential.ordinal) {
      unexpectedTimer = setTrackedTimer(() => {
        if (closing || socket.readyState !== WebSocket.OPEN) return;
        markPending(route);
        evidence.unexpectedDisconnects.push({
          ordinal: route.ordinal,
          userId: route.userId,
        });
        socket.close(1011, 'mock unexpected disconnect');
      }, config.unexpectedDisconnect.afterMs);
    }

    socket.once('close', () => {
      sockets.delete(socket);
      if (unexpectedTimer !== undefined) clearTrackedTimer(unexpectedTimer);
      if (!closing && routes.get(route.userId) === route) scheduleRouteCleanup(route);
    });
  });

  await listen(server);
  const address = server.address();
  if (address === null || typeof address === 'string') {
    await closeHttpServer(server);
    throw new Error('mock runtime did not bind a TCP port');
  }

  const httpUrl = `http://${LOOPBACK_HOST}:${address.port}`;
  const ready = Object.freeze({
    boundary: 'mock-runtime',
    httpUrl,
    websocketUrl: `ws://${LOOPBACK_HOST}:${address.port}/ws`,
    routeStatusUrl: `${httpUrl}/mock/routes`,
  });

  return Object.freeze({
    ready,
    close() {
      if (closePromise !== undefined) return closePromise;
      closePromise = closeRuntime();
      return closePromise;
    },
  });

  async function handleRequest(request, response) {
    const target = new URL(request.url ?? '/', 'http://mock-runtime.invalid');

    if (request.method === 'POST' && target.pathname === '/account/user/signIn') {
      const body = await readJsonBody(request);
      const ordinal = parseOrdinal(body.email);
      if (ordinal === undefined || ordinal >= config.userCount
          || body.password !== `mock-password-${ordinal}`) {
        sendError(response, 401, 'invalid mock credentials');
        return;
      }

      let credential = credentialsByOrdinal.get(ordinal);
      if (credential === undefined) {
        credential = Object.freeze({
          ordinal,
          userId: (USER_ID_BASE + BigInt(ordinal)).toString(),
          accessToken: randomBytes(24).toString('base64url'),
          cohortIndex: Math.floor(ordinal / config.groupSize),
          memberIndex: ordinal % config.groupSize,
        });
        credentialsByOrdinal.set(ordinal, credential);
        credentialsByToken.set(credential.accessToken, credential);
      }
      sendResult(response, {
        userId: credential.userId,
        accessToken: credential.accessToken,
        refreshToken: randomBytes(24).toString('base64url'),
      });
      return;
    }

    if (request.method === 'GET' && target.pathname === '/mock/evidence') {
      sendResult(response, {
        boundary: 'mock-runtime',
        opens: evidence.opens.map((entry) => ({ ...entry })),
        activeRoutes: [...routes.values()].filter((route) => route.active).length,
        handshakeFailures: evidence.handshakeFailures,
        unexpectedDisconnects: evidence.unexpectedDisconnects.map((entry) => ({ ...entry })),
        routeCleanups: evidence.routeCleanups.map((entry) => ({ ...entry })),
        messageSends: cloneActivity(evidence.messageSends),
        messageDeliveries: cloneActivity(evidence.messageDeliveries),
        messageAcks: cloneActivity(evidence.messageAcks),
        messageAckFailures: cloneActivity(evidence.messageAckFailures),
      });
      return;
    }

    const routeMatch = request.method === 'GET'
      ? target.pathname.match(/^\/mock\/routes\/([1-9]\d*)$/)
      : null;
    if (routeMatch !== null) {
      const userId = routeMatch[1];
      const route = routes.get(userId);
      sendResult(response, route === undefined
        ? { userId, active: false, cleanupState: 'cleaned' }
        : { userId, active: route.active, cleanupState: route.cleanupState });
      return;
    }

    sendError(response, 404, 'mock route not found');
  }

  function handleSocketFrame(socket, credential, payload) {
    const frame = JSON.parse(payload);
    if (frame?.version !== '1' || typeof frame.cmd !== 'string'
        || typeof frame.seq !== 'string' || frame.seq.length === 0
        || typeof frame.traceId !== 'string' || frame.traceId.length === 0
        || frame.body === null || typeof frame.body !== 'object' || Array.isArray(frame.body)) {
      throw new Error('malformed mock request frame');
    }
    if (frame.cmd === 'message.private.send') {
      handlePrivateSend(socket, credential, frame);
      return;
    }
    if (frame.cmd === 'message.group.send') {
      handleGroupSend(socket, credential, frame);
      return;
    }
    if (frame.cmd === 'notification.ack') {
      handleNotificationAck(socket, credential, frame);
      return;
    }
    throw new Error('unsupported mock frame command');
  }

  function handlePrivateSend(socket, credential, frame) {
    const body = validateSendBody(frame.body);
    const cohortStart = Math.floor(credential.ordinal / config.groupSize) * config.groupSize;
    const memberIndex = credential.ordinal % config.groupSize;
    const receiverOrdinal = cohortStart + ((memberIndex + 1) % config.groupSize);
    const receiverId = (USER_ID_BASE + BigInt(receiverOrdinal)).toString();
    const receiverRoute = routes.get(receiverId);
    const messageId = (nextMessageId++).toString();
    const receiverChatId = privateChatByUsers.get(`${receiverId}:${credential.userId}`);
    if (receiverChatId === undefined) throw new Error('mock private chat topology is missing');
    const receiptId = `PRIVATE_MESSAGE_SEND:${receiverId}:${receiverChatId}:${messageId}`;
    recordActivity(evidence.messageSends, body.correlation.sequence, body.messageToken);
    if (config.messageFault === 'send-timeout' && body.correlation.sequence === '0') return;
    sendFrame(socket, frameOk(frame));
    if (config.messageFault === 'missing' && body.correlation.sequence === '0') return;
    if (receiverRoute?.active !== true || receiverRoute.socket.readyState !== WebSocket.OPEN) return;
    pendingReceipts.set(receiptId, {
      receiverId,
      chatId: receiverChatId,
      messageId,
      receiptType: 'PRIVATE_MESSAGE_SEND',
      sequence: body.correlation.sequence,
      messageToken: body.messageToken,
    });
    const push = notificationFrame({
      command: 'message.private.sent',
      receiptId,
      messageId,
      chatId: receiverChatId,
      senderId: credential.userId,
      receiverId,
      messageContent: body.messageContent,
    });
    sendFrame(receiverRoute.socket, push);
    recordActivity(evidence.messageDeliveries, body.correlation.sequence, body.messageToken);
    if (config.messageFault === 'duplicate' && body.correlation.sequence === '0') {
      sendFrame(receiverRoute.socket, push);
      recordActivity(evidence.messageDeliveries, body.correlation.sequence, body.messageToken);
    }
    if (config.messageFault === 'unknown' && body.correlation.sequence === '0') {
      const unknownOrdinal = cohortStart + ((memberIndex + 2) % config.groupSize);
      const unknownId = (USER_ID_BASE + BigInt(unknownOrdinal)).toString();
      const unknownRoute = routes.get(unknownId);
      if (unknownRoute?.active === true && unknownRoute.socket.readyState === WebSocket.OPEN) {
        const unknownChatId = groupChatByUser.get(unknownId);
        const unknownReceipt = `PRIVATE_MESSAGE_SEND:${unknownId}:${unknownChatId}:${messageId}`;
        sendFrame(unknownRoute.socket, notificationFrame({
          command: 'message.private.sent',
          receiptId: unknownReceipt,
          messageId,
          chatId: unknownChatId,
          senderId: credential.userId,
          receiverId: unknownId,
          messageContent: body.messageContent,
        }));
        recordActivity(evidence.messageDeliveries, body.correlation.sequence, body.messageToken);
      }
    }
  }

  function handleNotificationAck(socket, credential, frame) {
    const body = frame.body;
    const receiptId = `${body.receiptType}:${credential.userId}:${body.chatId}:${body.messageId}`;
    const pending = pendingReceipts.get(receiptId);
    if (pending === undefined) {
      const completed = completedReceipts.get(receiptId);
      if (completed?.receiverId === credential.userId) {
        sendFrame(socket, frameOk(frame));
        return;
      }
      throw new Error('mock ACK does not match a pending receipt');
    }
    if (pending.receiverId !== credential.userId
        || pending.chatId !== String(body.chatId) || pending.messageId !== String(body.messageId)
        || pending.receiptType !== body.receiptType) {
      throw new Error('mock ACK does not match a pending receipt');
    }
    if (config.messageFault === 'ack-failure' && pending.sequence === '0') {
      pendingReceipts.delete(receiptId);
      recordActivity(evidence.messageAckFailures, pending.sequence, pending.messageToken);
      sendFrame(socket, frameError(frame, 'mock ACK failure'));
      return;
    }
    pendingReceipts.delete(receiptId);
    completedReceipts.set(receiptId, pending);
    recordActivity(evidence.messageAcks, pending.sequence, pending.messageToken);
    sendFrame(socket, frameOk(frame));
  }

  function handleGroupSend(socket, credential, frame) {
    const body = validateSendBody(frame.body);
    const cohortStart = Math.floor(credential.ordinal / config.groupSize) * config.groupSize;
    const messageId = (nextMessageId++).toString();
    recordActivity(evidence.messageSends, body.correlation.sequence, body.messageToken);
    if (config.messageFault === 'send-busy' && body.correlation.sequence === '7') {
      sendFrame(socket, frameError(frame, 'mock busy failure', 10010));
      return;
    }
    if (config.messageFault === 'send-repeat' && body.correlation.sequence === '7') {
      sendFrame(socket, frameError(frame, 'mock repeat failure', 10007));
      return;
    }
    const delayedResponse = config.messageFault === 'response-after-push'
      && body.correlation.sequence === '7';
    if (!delayedResponse) sendFrame(socket, frameOk(frame));
    for (let offset = 0; offset < config.groupSize; offset += 1) {
      const receiverOrdinal = cohortStart + offset;
      if (receiverOrdinal === credential.ordinal) continue;
      const receiverId = (USER_ID_BASE + BigInt(receiverOrdinal)).toString();
      const receiverRoute = routes.get(receiverId);
      if (receiverRoute?.active !== true || receiverRoute.socket.readyState !== WebSocket.OPEN) continue;
      const receiverChatId = groupChatByUser.get(receiverId);
      if (receiverChatId === undefined) throw new Error('mock group chat topology is missing');
      const receiptId = `GROUP_MESSAGE_SEND:${receiverId}:${receiverChatId}:${messageId}`;
      pendingReceipts.set(receiptId, {
        receiverId,
        chatId: receiverChatId,
        messageId,
        receiptType: 'GROUP_MESSAGE_SEND',
        sequence: body.correlation.sequence,
        messageToken: body.messageToken,
      });
      sendFrame(receiverRoute.socket, notificationFrame({
        command: 'message.group.sent',
        receiptId,
        messageId,
        chatId: receiverChatId,
        senderId: credential.userId,
        receiverId,
        messageContent: body.messageContent,
      }));
      recordActivity(evidence.messageDeliveries, body.correlation.sequence, body.messageToken);
    }
    if (delayedResponse) setTimeout(() => sendFrame(socket, frameOk(frame)), 600);
  }

  function validateSendBody(body) {
    if (typeof body.chatId !== 'string' || !/^[1-9][0-9]*$/.test(body.chatId)
        || body.messageType !== 'TEXT' || typeof body.messageContent !== 'string'
        || typeof body.messageToken !== 'string') {
      throw new Error('invalid mock message send body');
    }
    const correlation = parseCorrelation(body.messageContent, { messageBytes: 256 });
    const expectedToken = buildMessageToken(correlation);
    if (body.messageToken !== expectedToken) throw new Error('messageToken does not match messageContent');
    return { ...body, correlation };
  }

  function scheduleRouteCleanup(route) {
    if (route.cleanupScheduled) return;
    route.cleanupScheduled = true;
    markPending(route);
    const timesOut = config.routeCleanupTimeoutOrdinals.has(route.ordinal);
    const delayMs = timesOut ? config.routeCleanupTimeoutMs : config.routeCleanupDelayMs;
    route.cleanupTimer = setTrackedTimer(() => {
      route.cleanupTimer = undefined;
      if (closing || routes.get(route.userId) !== route) return;
      if (timesOut) {
        route.cleanupState = 'timed_out';
        evidence.routeCleanups.push({
          ordinal: route.ordinal,
          userId: route.userId,
          outcome: 'timed_out',
        });
        return;
      }
      route.active = false;
      route.cleanupState = 'cleaned';
      routes.delete(route.userId);
      evidence.routeCleanups.push({
        ordinal: route.ordinal,
        userId: route.userId,
        outcome: 'cleaned',
      });
    }, delayMs);
  }

  async function closeRuntime() {
    closing = true;
    for (const timer of timers) clearTimeout(timer);
    timers.clear();
    routes.clear();
    credentialsByToken.clear();
    credentialsByOrdinal.clear();

    for (const socket of sockets) socket.terminate();
    sockets.clear();

    const shutdown = Promise.allSettled([
      closeWebSocketServer(websocketServer),
      closeHttpServer(server),
    ]);
    await settleWithin(shutdown, config.closeTimeoutMs);

    for (const socket of transportSockets) socket.destroy();
    transportSockets.clear();
  }

  function setTrackedTimer(callback, delayMs) {
    const timer = setTimeout(() => {
      timers.delete(timer);
      callback();
    }, delayMs);
    timers.add(timer);
    return timer;
  }

  function clearTrackedTimer(timer) {
    clearTimeout(timer);
    timers.delete(timer);
  }
}

function validateOptions(options) {
  if (options === null || typeof options !== 'object' || Array.isArray(options)) {
    throw new Error('mock runtime options must be an object');
  }
  const userCount = options.userCount ?? DEFAULT_USER_COUNT;
  const groupSize = options.groupSize ?? DEFAULT_GROUP_SIZE;
  const routeCleanupDelayMs = options.routeCleanupDelayMs ?? DEFAULT_CLEANUP_DELAY_MS;
  const routeCleanupTimeoutMs = options.routeCleanupTimeoutMs ?? DEFAULT_CLEANUP_TIMEOUT_MS;
  const closeTimeoutMs = options.closeTimeoutMs ?? DEFAULT_CLOSE_TIMEOUT_MS;

  assertPositiveInteger(userCount, 'userCount');
  assertPositiveInteger(groupSize, 'groupSize');
  if (groupSize < 2 || groupSize > userCount) {
    throw new Error('groupSize must be between 2 and userCount');
  }
  assertNonNegativeInteger(routeCleanupDelayMs, 'routeCleanupDelayMs');
  assertNonNegativeInteger(routeCleanupTimeoutMs, 'routeCleanupTimeoutMs');
  assertPositiveInteger(closeTimeoutMs, 'closeTimeoutMs');

  const routeCleanupTimeoutOrdinals = new Set(options.routeCleanupTimeoutOrdinals ?? []);
  if (!Array.isArray(options.routeCleanupTimeoutOrdinals ?? [])) {
    throw new Error('routeCleanupTimeoutOrdinals must be an array');
  }
  for (const ordinal of routeCleanupTimeoutOrdinals) {
    assertOrdinal(ordinal, userCount, 'routeCleanupTimeoutOrdinals');
  }

  let unexpectedDisconnect;
  if (options.unexpectedDisconnect !== undefined) {
    const value = options.unexpectedDisconnect;
    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
      throw new Error('unexpectedDisconnect must be an object');
    }
    assertOrdinal(value.ordinal, userCount, 'unexpectedDisconnect.ordinal');
    assertNonNegativeInteger(value.afterMs, 'unexpectedDisconnect.afterMs');
    unexpectedDisconnect = Object.freeze({ ordinal: value.ordinal, afterMs: value.afterMs });
  }
  const messageFault = options.messageFault;
  if (messageFault !== undefined
      && !['duplicate', 'missing', 'unknown', 'ack-failure', 'send-timeout', 'send-busy', 'send-repeat', 'response-after-push'].includes(messageFault)) {
    throw new Error('messageFault is invalid');
  }

  return Object.freeze({
    userCount,
    groupSize,
    routeCleanupDelayMs,
    routeCleanupTimeoutMs,
    closeTimeoutMs,
    routeCleanupTimeoutOrdinals,
    unexpectedDisconnect,
    messageFault,
  });
}

function publicOwnership(credential) {
  return {
    ordinal: credential.ordinal,
    userId: credential.userId,
    cohortIndex: credential.cohortIndex,
    memberIndex: credential.memberIndex,
  };
}

function activityEvidence() {
  return { count: 0, recent: [] };
}

function recordActivity(activity, ordinal, messageToken) {
  activity.count += 1;
  activity.recent.push({
    ordinal,
    tokenDigest: `sha256:${createHash('sha256').update(messageToken).digest('hex').slice(0, 16)}`,
  });
  if (activity.recent.length > 100) activity.recent.shift();
}

function cloneActivity(activity) {
  return { count: activity.count, recent: activity.recent.map((entry) => ({ ...entry })) };
}

function frameOk(request) {
  return {
    version: '1',
    type: 'response',
    cmd: request.cmd,
    seq: request.seq,
    traceId: request.traceId,
    code: 0,
    message: 'OK',
    body: {},
  };
}

function frameError(request, message, code = -1) {
  return {
    version: '1',
    type: 'error',
    cmd: request?.cmd ?? null,
    seq: request?.seq ?? null,
    traceId: request?.traceId ?? null,
    code: -1,
    message,
    body: { code: String(code) },
  };
}

function notificationFrame({ command, receiptId, messageId, chatId, senderId, receiverId, messageContent }) {
  return {
    version: '1',
    type: 'push',
    cmd: command,
    seq: null,
    traceId: null,
    code: null,
    message: null,
    body: {
      notification: {
        messageId,
        chatId,
        senderId,
        receiverId,
        messageType: 'TEXT',
        messageContent,
        sendTime: new Date().toISOString().replace(/Z$/, ''),
      },
      eventId: receiptId,
      receiptId,
    },
  };
}

function sendFrame(socket, frame) {
  if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(frame));
}

function markPending(route) {
  if (route.cleanupState === 'active') route.cleanupState = 'pending';
}

function parseOrdinal(email) {
  if (typeof email !== 'string') return undefined;
  const match = email.match(/^loadtest-mock-([0-9a-z]+)@example\.invalid$/);
  if (match === null) return undefined;
  const ordinal = Number.parseInt(match[1], 36);
  return Number.isSafeInteger(ordinal) && ordinal >= 0 ? ordinal : undefined;
}

async function readJsonBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_REQUEST_BYTES) throw new Error('mock request body is too large');
    chunks.push(chunk);
  }
  const payload = Buffer.concat(chunks).toString('utf8');
  const value = JSON.parse(payload);
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('mock request body must be a JSON object');
  }
  return value;
}

function sendResult(response, data) {
  sendJson(response, 200, { code: 0, msg: 'success', data });
}

function sendError(response, statusCode, message) {
  sendJson(response, statusCode, { code: statusCode, msg: message, data: null });
}

function sendJson(response, statusCode, value) {
  const payload = JSON.stringify(value);
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
    connection: 'close',
  });
  response.end(payload);
}

function rejectUpgrade(socket, statusCode, statusText) {
  socket.end(
    `HTTP/1.1 ${statusCode} ${statusText}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`,
  );
}

async function listen(server) {
  server.listen(0, LOOPBACK_HOST);
  await once(server, 'listening');
}

function closeWebSocketServer(server) {
  return new Promise((resolvePromise) => {
    server.close(() => resolvePromise());
  });
}

function closeHttpServer(server) {
  return new Promise((resolvePromise) => {
    if (!server.listening) {
      resolvePromise();
      return;
    }
    server.close(() => resolvePromise());
    server.closeAllConnections?.();
  });
}

async function settleWithin(promise, timeoutMs) {
  let timeout;
  await Promise.race([
    promise,
    new Promise((resolvePromise) => {
      timeout = setTimeout(resolvePromise, timeoutMs);
    }),
  ]);
  clearTimeout(timeout);
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
}

function assertNonNegativeInteger(value, name) {
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
}

function assertOrdinal(value, userCount, name) {
  if (!Number.isInteger(value) || value < 0 || value >= userCount) {
    throw new Error(`${name} must be an ordinal within userCount`);
  }
}

function parseCliArgs(args) {
  const values = new Map();
  const supported = new Set([
    '--ready-file',
    '--users',
    '--group-size',
    '--seed-directory',
    '--unexpected-disconnect-ordinal',
    '--unexpected-disconnect-after-ms',
    '--route-cleanup-timeout-ordinal',
    '--route-cleanup-timeout-ms',
    '--message-fault',
  ]);
  for (let index = 0; index < args.length; index += 2) {
    const name = args[index];
    const value = args[index + 1];
    if (!supported.has(name)) throw new Error(`unknown argument: ${name}`);
    if (values.has(name)) throw new Error(`duplicate argument: ${name}`);
    if (value === undefined || value.startsWith('--')) throw new Error(`missing value for argument: ${name}`);
    values.set(name, value);
  }

  const readyFile = values.get('--ready-file');
  if (readyFile === undefined || readyFile.length === 0) {
    throw new Error('--ready-file is required');
  }
  const unexpectedOrdinal = values.get('--unexpected-disconnect-ordinal');
  const unexpectedAfterMs = values.get('--unexpected-disconnect-after-ms');
  if ((unexpectedOrdinal === undefined) !== (unexpectedAfterMs === undefined)) {
    throw new Error('unexpected disconnect ordinal and delay must be configured together');
  }
  const routeTimeoutOrdinal = values.get('--route-cleanup-timeout-ordinal');
  return {
    readyFile,
    seedDirectory: values.get('--seed-directory'),
    userCount: parseCliPositiveInteger(values.get('--users') ?? String(DEFAULT_USER_COUNT), '--users'),
    groupSize: parseCliPositiveInteger(values.get('--group-size') ?? String(DEFAULT_GROUP_SIZE), '--group-size'),
    unexpectedDisconnect: unexpectedOrdinal === undefined ? undefined : {
      ordinal: parseCliNonNegativeInteger(unexpectedOrdinal, '--unexpected-disconnect-ordinal'),
      afterMs: parseCliNonNegativeInteger(unexpectedAfterMs, '--unexpected-disconnect-after-ms'),
    },
    routeCleanupTimeoutOrdinals: routeTimeoutOrdinal === undefined
      ? []
      : [parseCliNonNegativeInteger(routeTimeoutOrdinal, '--route-cleanup-timeout-ordinal')],
    routeCleanupTimeoutMs: parseCliNonNegativeInteger(
      values.get('--route-cleanup-timeout-ms') ?? String(DEFAULT_CLEANUP_TIMEOUT_MS),
      '--route-cleanup-timeout-ms',
    ),
    messageFault: values.get('--message-fault'),
  };
}

function parseCliPositiveInteger(value, name) {
  if (!/^[1-9]\d*$/.test(value)) throw new Error(`${name} must be a positive integer`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${name} must be a safe integer`);
  return parsed;
}

function parseCliNonNegativeInteger(value, name) {
  if (!/^(?:0|[1-9]\d*)$/.test(value)) throw new Error(`${name} must be a non-negative integer`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${name} must be a safe integer`);
  return parsed;
}

async function runCli() {
  const args = parseCliArgs(process.argv.slice(2));
  const runtime = await startMockRuntime(args);
  if (args.seedDirectory !== undefined) {
    await publishMockSeed(args.seedDirectory, args.userCount, args.groupSize);
  }
  const readyJson = JSON.stringify(runtime.ready);
  await writeFile(args.readyFile, readyJson, { encoding: 'utf8', mode: 0o600 });
  process.stdout.write(`${readyJson}\n`);

  let stopping = false;
  const stop = async () => {
    if (stopping) return;
    stopping = true;
    await runtime.close();
    process.removeListener('SIGTERM', stop);
    process.removeListener('SIGINT', stop);
    process.exitCode = 0;
  };
  process.on('SIGTERM', stop);
  process.on('SIGINT', stop);
}

async function publishMockSeed(directory, userCount, groupSize) {
  await mkdir(directory, { recursive: true });
  const fixtureData = buildMockSeedData(userCount, groupSize);
  await publishManifest(`${directory}/manifest.json`, fixtureData.manifest);
  await publishCredentialManifest(`${directory}/credentials.json`, fixtureData.credentialManifest);
}

function buildMockSeedData(userCount, groupSize) {
  const batchId = 'mock';
  const prefix = `loadtest-${batchId}`;
  const users = [];
  const cohorts = [];
  const groups = [];
  const relations = [];
  const privateChats = [];
  const groupChats = [];
  const messages = [];
  const receiptIds = [];
  const credentials = [];
  let privateChatId = 6_100_000_000_000_000_000n;
  let groupChatId = 6_200_000_000_000_000_000n;

  for (let ordinal = 0; ordinal < userCount; ordinal += 1) {
    const userId = (USER_ID_BASE + BigInt(ordinal)).toString();
    const suffix = ordinal.toString(36).padStart(3, '0');
    const email = `${prefix}-${suffix}@example.invalid`;
    const cohortIndex = Math.floor(ordinal / groupSize);
    users.push({ userId, email, cohortId: `cohort-${String(cohortIndex + 1).padStart(4, '0')}` });
    credentials.push({
      userId,
      email,
      password: `mock-password-${ordinal}`,
      accessToken: `unused-seed-access-${ordinal}`,
      refreshToken: `unused-seed-refresh-${ordinal}`,
    });
  }

  for (let cohortIndex = 0; cohortIndex < userCount / groupSize; cohortIndex += 1) {
    const cohortUsers = users.slice(cohortIndex * groupSize, (cohortIndex + 1) * groupSize);
    const cohortId = cohortUsers[0].cohortId;
    const groupId = (6_000_000_000_000_000_000n + BigInt(cohortIndex)).toString();
    const messageId = (6_300_000_000_000_000_000n + BigInt(cohortIndex)).toString();
    const groupName = `${prefix}-g${cohortIndex.toString(36).padStart(3, '0')}`;
    const seenEdges = new Set();
    const chats = [];

    for (let index = 0; index < cohortUsers.length; index += 1) {
      const left = cohortUsers[index];
      const right = cohortUsers[(index + 1) % cohortUsers.length];
      const edge = [left.userId, right.userId].sort().join(':');
      if (!seenEdges.has(edge)) {
        seenEdges.add(edge);
        relations.push(
          { userId: left.userId, friendUserId: right.userId },
          { userId: right.userId, friendUserId: left.userId },
        );
        privateChats.push(
          { chatId: (privateChatId++).toString(), userId: left.userId, peerUserId: right.userId },
          { chatId: (privateChatId++).toString(), userId: right.userId, peerUserId: left.userId },
        );
      }
      const chat = { chatId: (groupChatId++).toString(), groupId, userId: left.userId };
      chats.push(chat);
      groupChats.push(chat);
    }

    cohorts.push({ cohortId, userIds: cohortUsers.map(({ userId }) => userId), groupId });
    groups.push({
      groupId,
      name: groupName,
      ownerUserId: cohortUsers[0].userId,
      memberUserIds: cohortUsers.map(({ userId }) => userId),
    });
    messages.push({
      messageId,
      type: 'group',
      senderUserId: cohortUsers[0].userId,
      correlationToken: `system:group_created:${groupId}`,
      groupId,
    });
    for (const chat of chats.slice(1)) {
      receiptIds.push(`GROUP_MESSAGE_SEND:${chat.userId}:${chat.chatId}:${messageId}`);
    }
  }

  const manifest = {
    version: 1,
    batchId,
    prefix,
    schemas: {
      account: 'im_chat_account',
      social: 'im_chat_social',
      message: 'im_chat_message',
    },
    users,
    cohorts,
    groups,
    relations,
    privateChats,
    groupChats,
    messages,
    receiptIds,
  };
  const credentialManifest = {
    version: 1,
    batchId,
    credentials,
  };
  return { manifest, credentialManifest };
}

const mainPath = process.argv[1] === undefined ? undefined : resolve(process.argv[1]);
if (mainPath === fileURLToPath(import.meta.url)) {
  runCli().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
