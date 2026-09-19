import assert from 'node:assert/strict';
import { appendFile, mkdtemp, readFile, stat, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, test } from 'node:test';
import { WebSocketServer } from 'ws';

import { resolveConfig } from '../lib/config.js';
import { createJournal, recoverJournal } from '../tools/lib/journal.mjs';
import { GatewayBusinessError, createGatewayHttpClient } from '../tools/lib/http-client.mjs';
import { readManifest } from '../tools/lib/manifest.mjs';
import { createPreparationPlan, loginRound, logoutRound, prepareBatch } from '../tools/lib/preparation.mjs';
import { main as dataMain } from '../tools/data.mjs';

const defaults = JSON.parse(await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'));
const FAST_SETUP = Object.freeze({ retryHorizonMs: 5, quietMs: 5 });
const cleanups = [];

afterEach(async () => {
  await Promise.all(cleanups.splice(0).map((cleanup) => cleanup()));
});

async function mockRuntime({ lose = new Set(), publishChats = true, transformGroupList = (items) => items } = {}) {
  let next = 9007199254740993n;
  const users = new Map();
  const tokens = new Map();
  const friendships = new Set();
  const privateChats = new Map();
  const groups = [];
  const sockets = new Map();
  const closed = new Set();
  const mutationCounts = { signUp: 0, addFriend: 0, createGroup: 0 };
  const lost = new Set();
  const httpServer = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    const payload = body === '' ? {} : JSON.parse(body);
    const token = request.headers.authorization?.replace('Bearer ', '');
    const currentUser = tokens.get(token);
    const send = (data = {}) => {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ code: 0, msg: 'success', data }));
    };
    const loseResponse = (operation) => {
      if (lose.has(operation) && !lost.has(operation)) {
        lost.add(operation);
        response.destroy();
        return true;
      }
      return false;
    };

    if (request.url === '/account/user/signUp') {
      mutationCounts.signUp += 1;
      if (users.has(payload.email)) return send();
      const userId = String(next++);
      users.set(payload.email, { userId, ...payload });
      if (!loseResponse('signUp')) send();
      return;
    }
    if (request.url === '/account/user/signIn') {
      const user = users.get(payload.email);
      if (user === undefined || user.password !== payload.password) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ code: 10001, msg: 'AUTH_FAIL', data: {} }));
        return;
      }
      const accessToken = `access-${user.userId}`;
      tokens.set(accessToken, user.userId);
      send({ userId: user.userId, accessToken, refreshToken: `refresh-${user.userId}` });
      return;
    }
    if (request.url === '/account/user/userDetail') {
      const user = [...users.values()].find(({ userId }) => userId === currentUser);
      send({ userId: user.userId, email: user.email, username: user.username });
      return;
    }
    if (request.url === '/social/friend/addFriend') {
      mutationCounts.addFriend += 1;
      const pair = [currentUser, payload.friendUserId].sort();
      const key = pair.join(':');
      friendships.add(key);
      if (publishChats && !privateChats.has(key)) {
        privateChats.set(key, new Map([
          [pair[0], String(next++)],
          [pair[1], String(next++)],
        ]));
      }
      if (!loseResponse('addFriend')) send();
      return;
    }
    if (request.url === '/social/friend/friendList') {
      const friends = [...friendships].flatMap((key) => {
        const pair = key.split(':');
        return pair.includes(currentUser) ? [{ userId: pair.find((id) => id !== currentUser) }] : [];
      });
      send({ friends });
      return;
    }
    if (request.url === '/message/chat/getChatList') {
      const chatList = [];
      for (const [key, chats] of privateChats) {
        const pair = key.split(':');
        if (pair.includes(currentUser)) {
          const peerId = pair.find((id) => id !== currentUser);
          const peer = [...users.values()].find(({ userId }) => userId === peerId);
          chatList.push({ chatId: chats.get(currentUser), chatName: peer.username, chatType: 'PRIVATE' });
        }
      }
      send({ chatList });
      return;
    }
    if (request.url === '/social/group/createGroup') {
      mutationCounts.createGroup += 1;
      const existing = groups.find(({ groupName }) => groupName === payload.groupName);
      if (existing !== undefined) return send({ groupId: existing.groupId });
      const groupId = String(next++);
      const memberIds = [currentUser, ...payload.memberIds];
      const chats = new Map(memberIds.map((userId) => [userId, String(next++)]));
      const messageId = String(next++);
      const group = { groupId, groupName: payload.groupName, ownerId: currentUser, memberIds, chats, messageId };
      groups.push(group);
      for (const memberId of payload.memberIds) {
        const receiptId = `GROUP_MESSAGE_SEND:${memberId}:${chats.get(memberId)}:${messageId}`;
        sockets.get(memberId)?.send(JSON.stringify({
          version: '1', type: 'push', cmd: 'message.group.sent', seq: null, traceId: 'server',
          code: null, message: null,
          body: { eventId: receiptId, receiptId, notification: { messageContent: 'group created' } },
        }));
      }
      if (!loseResponse('createGroup')) send({ groupId });
      return;
    }
    if (request.url === '/social/group/getGroupList') {
      const groupList = groups.filter(({ memberIds }) => memberIds.includes(currentUser)).map((group) => ({
        groupId: group.groupId,
        chatId: group.chats.get(currentUser),
        groupName: group.groupName,
        ownerId: group.ownerId,
        memberCount: group.memberIds.length,
        unreadMessageCount: 0,
      }));
      send({ groupList: transformGroupList(groupList, currentUser) });
      return;
    }
    response.writeHead(404).end();
  });
  await new Promise((resolve) => httpServer.listen(0, '127.0.0.1', resolve));

  const wsHttpServer = createServer();
  const wsServer = new WebSocketServer({ server: wsHttpServer });
  wsServer.on('connection', (socket, request) => {
    const accessToken = new URL(request.url, 'ws://localhost').searchParams.get('token');
    const userId = tokens.get(accessToken);
    sockets.set(userId, socket);
    socket.on('close', () => closed.add(userId));
    socket.on('message', (raw) => {
      const frame = JSON.parse(raw.toString());
      socket.send(JSON.stringify({
        version: '1', type: 'response', cmd: 'notification.ack', seq: frame.seq,
        traceId: frame.traceId, code: 0, message: 'success', body: {},
      }));
    });
  });
  await new Promise((resolve) => wsHttpServer.listen(0, '127.0.0.1', resolve));
  const httpPort = httpServer.address().port;
  const wsPort = wsHttpServer.address().port;
  cleanups.push(async () => {
    for (const socket of sockets.values()) socket.terminate();
    await new Promise((resolve) => wsServer.close(resolve));
    await new Promise((resolve) => wsHttpServer.close(resolve));
    await new Promise((resolve) => httpServer.close(resolve));
  });
  return {
    config: resolveConfig(defaults, {
      targets: {
        http: `http://127.0.0.1:${httpPort}`,
        websocket: `ws://127.0.0.1:${wsPort}/ws`,
      },
      groupSize: 2,
      levels: { connection: [2], message: [2] },
    }),
    users,
    friendships,
    privateChats,
    groups,
    mutationCounts,
    closed,
  };
}

async function paths() {
  const directory = await mkdtemp(join(tmpdir(), 'im-load-prepare-'));
  return {
    directory,
    journal: join(directory, 'journal.jsonl'),
    manifest: join(directory, 'manifest.json'),
    credentials: join(directory, 'credentials.json'),
  };
}

test('response loss after each mutation reconciles without duplicate side effects', async () => {
  const runtime = await mockRuntime({ lose: new Set(['signUp', 'addFriend', 'createGroup']) });
  const output = await paths();

  const prepared = await prepareBatch({
    batchId: 'rsp001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output,
    localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.users.size, 2);
  assert.equal(runtime.friendships.size, 1);
  assert.equal(runtime.groups.length, 1);
  assert.deepEqual(runtime.mutationCounts, { signUp: 2, addFriend: 1, createGroup: 1 });
  assert.equal(prepared.manifest.users.length, 2);
  assert.equal(prepared.manifest.relations.length, 2);
  assert.equal(prepared.manifest.groups.length, 1);
  assert.equal(prepared.manifest.receiptIds.length, 1);
  assert.deepEqual([...runtime.closed].sort(), prepared.manifest.users.map(({ userId }) => userId).sort());
  const recovered = await recoverJournal({ filePath: output.journal, batchId: 'rsp001' });
  assert.equal(recovered.unresolvedIntents.length, 0);
  assert.equal(recovered.completed.length, 4);
  assert.equal(recovered.checkpoints.length, 1);
  assert.deepEqual(await readManifest(output.manifest), prepared.manifest);
  assert.equal((await stat(output.credentials)).mode & 0o777, 0o600);
  const publicText = await readFile(output.manifest, 'utf8');
  const journalText = await readFile(output.journal, 'utf8');
  for (const secret of ['password', 'accessToken', 'refreshToken', 'Bearer']) {
    assert.equal(publicText.includes(secret), false);
    assert.equal(journalText.includes(secret), false);
  }
});

test('resume continues a partially completed batch from journal outcomes', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  let completed = 0;

  await assert.rejects(prepareBatch({
    batchId: 'rsm001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output,
    localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
    hooks: { afterCompleted() { completed += 1; if (completed === 1) throw new Error('injected crash'); } },
  }), /injected crash/);
  const cleanupSeed = await readManifest(output.manifest);
  assert.equal(cleanupSeed.batchId, 'rsm001');
  assert.deepEqual(cleanupSeed.users, []);

  const prepared = await prepareBatch({
    batchId: 'rsm001', resume: true, cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output,
    localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.users.size, 2);
  assert.equal(runtime.friendships.size, 1);
  assert.equal(runtime.groups.length, 1);
  assert.equal(prepared.manifest.cohorts.length, 1);
});

test('fails boundedly when private chat events do not converge', async () => {
  const runtime = await mockRuntime({ publishChats: false });
  const output = await paths();
  const scheduler = { async wait() {} };

  await assert.rejects(prepareBatch({
    batchId: 'nochat', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, scheduler, pollAttempts: 3,
    localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  }), /private chat.*converge/i);
  assert.equal(runtime.friendships.size, 1);
  assert.equal(runtime.groups.length, 0);
});

test('login-round uses bounded concurrency and publishes separate secret and latency files', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  const prepared = await prepareBatch({
    batchId: 'login1', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output,
    localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });
  const roundCredentials = join(output.directory, 'round-credentials.json');
  const summary = join(output.directory, 'round-summary.json');
  let active = 0;
  let peak = 0;
  const baseClient = prepared.httpClient;
  const httpClient = {
    ...baseClient,
    async signIn(input) {
      active += 1;
      peak = Math.max(peak, active);
      try { return await baseClient.signIn(input); } finally { active -= 1; }
    },
  };

  const result = await loginRound({
    batchId: 'login1', roundId: 'round-1', concurrency: 1,
    manifestPath: output.manifest, credentialPath: output.credentials,
    roundCredentialPath: roundCredentials, summaryPath: summary,
    config: runtime.config, httpClient,
    clock: (() => { let now = 1000; return () => (now += 5); })(),
  });

  assert.equal(peak, 1);
  assert.equal(result.summary.successCount, 2);
  assert.equal((await stat(roundCredentials)).mode & 0o777, 0o600);
  const summaryText = await readFile(summary, 'utf8');
  assert.equal(summaryText.includes('access-'), false);
  assert.equal(summaryText.includes('refresh-'), false);
  assert.deepEqual(JSON.parse(summaryText).latencyMs, [5, 5]);
});

test('logout-round signs out every round credential with bounded concurrency', async () => {
  const output = await paths();
  await writeFile(output.credentials, JSON.stringify({
    version: 1,
    batchId: 'logout',
    credentials: [
      { userId: '101', email: 'loadtest-logout-001@lt.dev', password: 'p1', accessToken: 'a1', refreshToken: 'r1' },
      { userId: '102', email: 'loadtest-logout-002@lt.dev', password: 'p2', accessToken: 'a2', refreshToken: 'r2' },
    ],
  }), { mode: 0o600 });
  let active = 0;
  let peak = 0;
  const signedOut = [];
  const result = await logoutRound({
    batchId: 'logout',
    roundId: 'round-1',
    concurrency: 1,
    credentialPath: output.credentials,
    summaryPath: join(output.directory, 'logout.json'),
    httpClient: {
      async signOut({ operationId, accessToken }) {
        active += 1;
        peak = Math.max(peak, active);
        signedOut.push({ operationId, accessToken });
        active -= 1;
      },
    },
  });

  assert.equal(peak, 1);
  assert.equal(result.successCount, 2);
  assert.deepEqual(signedOut.map(({ accessToken }) => accessToken), ['a1', 'a2']);
  assert.equal((await readFile(join(output.directory, 'logout.json'), 'utf8')).includes('a1'), false);
});

test('logout-round treats an already invalidated controlled token as idempotently signed out', async () => {
  const output = await paths();
  await writeFile(output.credentials, JSON.stringify({
    version: 1,
    batchId: 'logout',
    credentials: [
      { userId: '101', email: 'loadtest-logout-001@lt.dev', password: 'p1', accessToken: 'a1', refreshToken: 'r1' },
      { userId: '102', email: 'loadtest-logout-002@lt.dev', password: 'p2', accessToken: 'a2', refreshToken: 'r2' },
    ],
  }), { mode: 0o600 });
  const attempts = [];
  const result = await logoutRound({
    batchId: 'logout',
    roundId: 'round-1',
    concurrency: 1,
    credentialPath: output.credentials,
    summaryPath: join(output.directory, 'logout.json'),
    httpClient: {
      async signOut({ accessToken }) {
        attempts.push(accessToken);
        if (accessToken === 'a1') throw new GatewayBusinessError(10001, 'AUTH_FAIL');
      },
    },
  });

  assert.deepEqual(attempts, ['a1', 'a2']);
  assert.deepEqual(result.signedOutUserIds, ['101', '102']);
  assert.equal(result.successCount, 2);
});

test('logout-round still rejects non-authentication business failures', async () => {
  const output = await paths();
  await writeFile(output.credentials, JSON.stringify({
    version: 1,
    batchId: 'logout',
    credentials: [
      { userId: '101', email: 'loadtest-logout-001@lt.dev', password: 'p1', accessToken: 'a1', refreshToken: 'r1' },
    ],
  }), { mode: 0o600 });

  await assert.rejects(logoutRound({
    batchId: 'logout',
    roundId: 'round-1',
    credentialPath: output.credentials,
    summaryPath: join(output.directory, 'logout.json'),
    httpClient: {
      async signOut() {
        throw new GatewayBusinessError(10000, 'SYS_ERROR');
      },
    },
  }), /business code 10000/i);
});

test('data prepare defaults to 3200 users in full ten-user cohorts under harness output', async () => {
  let received;
  await dataMain(['prepare', '--batch', 'def001'], {
    defaults,
    localReconciler: {},
    makeDirectory: async () => undefined,
    prepareBatch: async (input) => { received = input; },
  });

  assert.equal(received.groupSize, 10);
  assert.equal(received.cohortCount, 320);
  assert.equal(received.paths.manifest, join('.harness', 'load-test', 'def001', 'manifest.json'));
});

test('data prepare accepts an explicit smaller full-cohort smoke size', async () => {
  let received;
  await dataMain(['prepare', '--batch', 'smoke', '--users', '20'], {
    defaults,
    localReconciler: {},
    makeDirectory: async () => undefined,
    prepareBatch: async (input) => { received = input; },
  });

  assert.equal(received.cohortCount, 2);
  await assert.rejects(
    dataMain(['prepare', '--batch', 'bad', '--users', '21'], {
      defaults,
      localReconciler: {},
      makeDirectory: async () => undefined,
      prepareBatch: async () => undefined,
    }),
    /full cohort|multiple/i,
  );
});

test('data prepare builds and closes its reconciler from a 0600 environment-selected targets file', async () => {
  let reconcilerClosed = false;
  let received;
  const targetDocument = JSON.stringify({
    account: 'mysql://u:p@127.0.0.1:3306/im_chat_account',
    social: 'mysql://u:p@127.0.0.1:3306/im_chat_social',
    message: 'mysql://u:p@127.0.0.1:3306/im_chat_message',
  });
  await dataMain(['prepare', '--batch', 'tgt001', '--users', '20'], {
    defaults,
    env: { IM_LOAD_TEST_DATABASE_TARGETS_FILE: '/private/database-targets.json' },
    fileSystem: {
      async readFile(path) {
        assert.equal(path, '/private/database-targets.json');
        return targetDocument;
      },
      async stat() { return { mode: 0o100600 }; },
    },
    makeDirectory: async () => undefined,
    createLocalReconciler({ targets: receivedTargets }) {
      assert.deepEqual(receivedTargets, JSON.parse(targetDocument));
      return { async close() { reconcilerClosed = true; } };
    },
    async prepareBatch(input) {
      received = input;
      assert.equal(typeof input.localReconciler.close, 'function');
    },
  });

  assert.equal(received.cohortCount, 2);
  assert.equal(reconcilerClosed, true);
});

test('data prepare rejects missing or non-0600 database target files before preparation', async () => {
  for (const fixture of [
    { env: {}, mode: 0o100600, pattern: /DATABASE_TARGETS_FILE/ },
    {
      env: { IM_LOAD_TEST_DATABASE_TARGETS_FILE: '/private/database-targets.json' },
      mode: 0o100644,
      pattern: /0600/,
    },
  ]) {
    await assert.rejects(dataMain(['prepare', '--batch', 'unsafe', '--users', '20'], {
      defaults,
      env: fixture.env,
      fileSystem: {
        async readFile() { return '{}'; },
        async stat() { return { mode: fixture.mode }; },
      },
      makeDirectory: async () => undefined,
      prepareBatch: async () => assert.fail('prepare must not run'),
    }), fixture.pattern);
  }
});

test('data commands reject unknown, duplicate, missing, and conflicting options before execution', async () => {
  const cases = [
    { argv: ['prepare', '--user', '20'], pattern: /unknown.*--user/i },
    { argv: ['prepare', '--users', '20', '--users', '30'], pattern: /duplicate.*--users/i },
    { argv: ['prepare', '--users'], pattern: /missing.*--users|requires.*value/i },
    { argv: ['prepare', '--batch', 'abc123', '--resume', 'abc123'], pattern: /batch.*resume.*conflict/i },
    { argv: ['login-round', '--batch', 'abc123', '--round', 'r1', '--users', '20'], pattern: /unknown.*--users/i },
    { argv: ['login-round', '--batch', 'abc123', '--round', 'r1', '--round', 'r2'], pattern: /duplicate.*--round/i },
  ];
  let executions = 0;
  for (const { argv, pattern } of cases) {
    await assert.rejects(dataMain(argv, {
      defaults,
      localReconciler: {},
      makeDirectory: async () => undefined,
      prepareBatch: async () => { executions += 1; },
      loginRound: async () => { executions += 1; },
    }), pattern);
  }
  assert.equal(executions, 0);
});

test('login-round accepts a validated local HTTP target from the environment', async () => {
  let received;
  await dataMain(['login-round', '--batch', 'abc123', '--round', 'r1'], {
    defaults,
    env: { IM_LOAD_TEST_HTTP_URL: 'http://127.0.0.1:43123' },
    loginRound: async (input) => { received = input; },
  });

  assert.equal(received.config.targets.http, 'http://127.0.0.1:43123');
  await assert.rejects(dataMain(['login-round', '--batch', 'abc123', '--round', 'r1'], {
    defaults,
    env: { IM_LOAD_TEST_HTTP_URL: 'http://example.test:43123' },
    loginRound: async () => assert.fail('remote target must be rejected'),
  }), /non-local|unlock/i);
});

test('login-round rejects unsafe or unbounded round IDs before creating output paths', async () => {
  for (const roundId of ['../escape', 'nested/round', '.hidden', 'a'.repeat(65)]) {
    await assert.rejects(dataMain(['login-round', '--batch', 'abc123', '--round', roundId], {
      defaults,
      loginRound: async () => assert.fail('unsafe round must not execute'),
    }), /round.*bounded|round.*invalid/i);
  }
});

function localReconcilerFor(runtime) {
  return {
    async preflight() {},
    async signUp({ email, username }) {
      const user = runtime.users.get(email);
      return user?.username === username
        ? { outcome: 'committed', result: { userId: user.userId }, evidence: { source: 'local-db' } }
        : { outcome: 'not_committed', result: null, evidence: { source: 'local-db' } };
    },
    async addFriend({ userId, friendUserId }) {
      const pair = [userId, friendUserId].sort();
      return runtime.friendships.has(pair.join(':'))
        ? {
          outcome: 'committed',
          result: { userId, friendUserId },
          evidence: { source: 'local-db-social' },
        }
        : { outcome: 'not_committed', result: null, evidence: { source: 'local-db-social' } };
    },
    async privateChatArtifacts({ userId, friendUserId }) {
      const chats = runtime.privateChats.get([userId, friendUserId].sort().join(':'));
      if (chats === undefined) return { outcome: 'pending', evidence: { source: 'local-db-message' } };
      return {
        outcome: 'converged',
        result: {
          privateChats: [
            { chatId: chats.get(userId), userId, peerUserId: friendUserId },
            { chatId: chats.get(friendUserId), userId: friendUserId, peerUserId: userId },
          ],
        },
        evidence: { source: 'local-db-message' },
      };
    },
    async createGroup({ groupName, ownerId, memberIds }) {
      const group = runtime.groups.find((candidate) => candidate.groupName === groupName);
      if (group === undefined) return { outcome: 'not_committed', result: null, evidence: { source: 'local-db-social' } };
      return {
        outcome: 'committed', result: { groupId: group.groupId }, evidence: { source: 'local-db-social' },
      };
    },
    async groupArtifacts({ groupId, ownerId, memberIds }) {
      const group = runtime.groups.find((candidate) => candidate.groupId === groupId);
      if (group === undefined) return { outcome: 'pending', evidence: { source: 'local-db-message' } };
      const memberChats = memberIds.map((userId) => ({ userId, chatId: group.chats.get(userId) }));
      return {
        outcome: 'converged',
        result: { ownerChatId: group.chats.get(ownerId), memberChats },
        evidence: {
          source: 'local-db-message',
          messageId: group.messageId,
          receiptIds: memberChats.map(({ userId, chatId }) => (
            `GROUP_MESSAGE_SEND:${userId}:${chatId}:${group.messageId}`
          )),
        },
      };
    },
  };
}

for (const crashOperation of ['signUp', 'addFriend', 'createGroup']) {
  test(`fresh client resumes unresolved ${crashOperation} through local reconciliation`, async () => {
    const runtime = await mockRuntime();
    const output = await paths();
    let crashed = false;
    await assert.rejects(prepareBatch({
      batchId: { signUp: 'rsu001', addFriend: 'raf001', createGroup: 'rcg001' }[crashOperation],
      cohortCount: 1,
      groupSize: 2,
      config: runtime.config,
      paths: output,
      localReconciler: localReconcilerFor(runtime),
      setupOptions: FAST_SETUP,
      hooks: {
        afterMutation({ operation }) {
          if (!crashed && operation === crashOperation) {
            crashed = true;
            throw new Error(`crash after ${crashOperation}`);
          }
        },
      },
    }), new RegExp(`crash after ${crashOperation}`));
    const baseClient = createGatewayHttpClient({ config: runtime.config });
    let localCalls = 0;
    let authFailureInjected = false;
    const freshClient = {
      ...baseClient,
      async signIn(input) {
        if (crashOperation === 'signUp'
            && input.operationId.includes('reconcile-login')
            && !authFailureInjected) {
          authFailureInjected = true;
          throw new GatewayBusinessError(10001, 'AUTH_FAIL');
        }
        return baseClient.signIn(input);
      },
      async friendList(input) {
        if (crashOperation === 'addFriend') throw new GatewayBusinessError(10001, 'AUTH_FAIL');
        return baseClient.friendList(input);
      },
      async getGroupList(input) {
        if (crashOperation === 'createGroup') throw new GatewayBusinessError(10001, 'AUTH_FAIL');
        return baseClient.getGroupList(input);
      },
    };
    const local = localReconcilerFor(runtime);
    const countedLocal = Object.fromEntries(Object.entries(local).map(([name, method]) => [name, async (input) => {
      if (name === crashOperation) localCalls += 1;
      return method(input);
    }]));

    const result = await prepareBatch({
      batchId: { signUp: 'rsu001', addFriend: 'raf001', createGroup: 'rcg001' }[crashOperation],
      resume: true,
      cohortCount: 1,
      groupSize: 2,
      config: runtime.config,
      paths: output,
      httpClient: freshClient,
      localReconciler: countedLocal,
      setupOptions: { retryHorizonMs: 5, quietMs: 5 },
    });

    assert.equal(result.manifest.cohorts.length, 1);
    assert.deepEqual(runtime.mutationCounts, {
      signUp: 2,
      addFriend: crashOperation === 'addFriend' ? 2 : 1,
      createGroup: 1,
    });
    assert.equal(localCalls, 1);
  });
}

test('group mutation is reconciled committed before notification ACK completion', async () => {
  const output = await paths();
  const batchId = 'gba001';
  const journal = createJournal({ filePath: output.journal, batchId });
  await journal.appendIntent({
    operationId: 'signup:cohort-0001:0', operation: 'signUp',
    ownership: { email: `loadtest-${batchId}-a@example.test`, username: `loadtest-${batchId}-a` },
  });
  await journal.appendInvoked({ operationId: 'signup:cohort-0001:0', attempt: 1 });
  await journal.appendReconciled({
    operationId: 'signup:cohort-0001:0', attempt: 1, outcome: 'committed',
    result: { userId: '101' }, evidence: { source: 'fixture' },
  });
  await journal.appendCompleted({ operationId: 'signup:cohort-0001:0', result: { userId: '101' } });
  await journal.appendIntent({
    operationId: 'signup:cohort-0001:1', operation: 'signUp',
    ownership: { email: `loadtest-${batchId}-b@example.test`, username: `loadtest-${batchId}-b` },
  });
  await journal.appendInvoked({ operationId: 'signup:cohort-0001:1', attempt: 1 });
  await journal.appendReconciled({
    operationId: 'signup:cohort-0001:1', attempt: 1, outcome: 'committed',
    result: { userId: '102' }, evidence: { source: 'fixture' },
  });
  await journal.appendCompleted({ operationId: 'signup:cohort-0001:1', result: { userId: '102' } });
  await journal.appendIntent({
    operationId: 'friend:cohort-0001:0', operation: 'addFriend',
    ownership: { userId: '101', friendUserId: '102' },
  });
  await journal.appendInvoked({ operationId: 'friend:cohort-0001:0', attempt: 1 });
  await journal.appendReconciled({
    operationId: 'friend:cohort-0001:0', attempt: 1, outcome: 'committed',
    result: { userId: '101', friendUserId: '102' },
    evidence: {
      source: 'fixture',
      privateChats: [
        { chatId: '201', userId: '101', peerUserId: '102' },
        { chatId: '202', userId: '102', peerUserId: '101' },
      ],
    },
  });
  await journal.appendCompleted({
    operationId: 'friend:cohort-0001:0', result: { userId: '101', friendUserId: '102' },
  });
  await journal.appendIntent({
    operationId: 'group:cohort-0001', operation: 'createGroup',
    ownership: { ownerId: '101', memberIds: ['102'], groupName: `loadtest-${batchId}-group` },
  });
  await journal.appendInvoked({ operationId: 'group:cohort-0001', attempt: 1 });
  const httpClient = {
    async signIn({ email }) {
      const userId = email.endsWith('-000@lt.dev') ? '101' : '102';
      return { userId, accessToken: `access-${userId}`, refreshToken: `refresh-${userId}` };
    },
    async userDetail({ accessToken }) {
      const userId = accessToken.slice(7);
      const suffix = userId === '101' ? '000' : '001';
      return {
        userId,
        email: `loadtest-${batchId}-${suffix}@lt.dev`,
        username: `loadtest-${batchId}-${suffix}`,
      };
    },
    async getGroupList() { throw Object.assign(new Error('AUTH_FAIL'), { code: 10001 }); },
  };
  const localReconciler = {
    async preflight() {},
    async privateChatArtifacts() {
      return {
        outcome: 'converged',
        result: {
          privateChats: [
            { chatId: '201', userId: '101', peerUserId: '102' },
            { chatId: '202', userId: '102', peerUserId: '101' },
          ],
        },
        evidence: { source: 'fixture' },
      };
    },
    async createGroup() {
      return {
        outcome: 'committed', result: { groupId: '301' }, evidence: { source: 'local-db-social' },
      };
    },
    async groupArtifacts() {
      return {
        outcome: 'converged',
        result: { ownerChatId: '401', memberChats: [{ userId: '102', chatId: '402' }] },
        evidence: { source: 'local-db-message', messageId: '501', receiptIds: ['GROUP_MESSAGE_SEND:102:402:501'] },
      };
    },
  };
  const websocketFactory = async () => ({
    seedKnownReceipts() {},
    waitForGroupCreated: async () => { throw new Error('ACK barrier crashed'); },
    async close() {},
  });

  await assert.rejects(prepareBatch({
    batchId, resume: true, cohortCount: 1, groupSize: 2,
    config: resolveConfig(defaults, { groupSize: 2, levels: { connection: [2], message: [2] } }),
    paths: output, httpClient, localReconciler, websocketFactory,
  }), /ACK barrier crashed/);

  const recovered = await recoverJournal({ filePath: output.journal, batchId });
  const group = recovered.completed.find(({ operation }) => operation === 'createGroup');
  assert.equal(group.result.groupId, '301');
  assert.equal(recovered.unresolvedIntents.some(({ operation }) => operation === 'createGroup'), false);
});

test('owner preflight failure prevents every signup mutation', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  const localReconciler = localReconcilerFor(runtime);
  localReconciler.preflight = async () => { throw new Error('message owner preflight failed'); };

  await assert.rejects(prepareBatch({
    batchId: 'pre001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler,
    setupOptions: FAST_SETUP,
  }), /message owner preflight failed/);

  assert.deepEqual(runtime.mutationCounts, { signUp: 0, addFriend: 0, createGroup: 0 });
});

test('Social-committed group waits for Message convergence without a second mutation', async () => {
  const runtime = await mockRuntime({ lose: new Set(['createGroup']) });
  const output = await paths();
  const local = localReconcilerFor(runtime);
  let artifactChecks = 0;
  const delayedLocal = {
    ...local,
    async groupArtifacts(input) {
      artifactChecks += 1;
      if (artifactChecks === 1) return { outcome: 'pending', evidence: { source: 'local-db-message' } };
      return local.groupArtifacts(input);
    },
  };
  const baseClient = createGatewayHttpClient({ config: runtime.config });
  const client = {
    ...baseClient,
    async getGroupList() { throw new GatewayBusinessError(10001, 'AUTH_FAIL'); },
  };

  const result = await prepareBatch({
    batchId: 'msg001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, httpClient: client, localReconciler: delayedLocal,
    scheduler: { async wait() {} }, pollAttempts: 3, setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.mutationCounts.createGroup, 1);
  assert.equal(artifactChecks, 2);
  assert.equal(result.manifest.messages.length, 1);
});

test('completed group without receipt evidence resumes through DB seed and does not recreate group', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  let crash = false;
  await assert.rejects(prepareBatch({
    batchId: 'cgs001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    websocketFactory: async () => ({
      waitForGroupCreated: () => new Promise(() => undefined),
      async close() {},
    }),
    hooks: {
      afterCompleted({ operation }) {
        if (operation === 'createGroup' && !crash) {
          crash = true;
          throw new Error('crash after group completed');
        }
      },
    },
  }), /crash after group completed/);
  const recoveredBefore = await recoverJournal({ filePath: output.journal, batchId: 'cgs001' });
  const completedGroup = recoveredBefore.completed.find(({ operation }) => operation === 'createGroup');
  assert.equal(completedGroup.reconciliation.evidence.receiptIds, undefined);

  const result = await prepareBatch({
    batchId: 'cgs001', resume: true, cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.mutationCounts.createGroup, 1);
  assert.equal(result.manifest.receiptIds.length, 1);
  const ackRecords = (await readFile(`${output.journal}.setup-receipts.jsonl`, 'utf8'))
    .trim().split('\n').map(JSON.parse);
  assert.deepEqual(ackRecords.map(({ type }) => type), ['intent', 'completed']);
});

test('Social-committed friendship waits for Message artifacts without a second mutation', async () => {
  const runtime = await mockRuntime({ publishChats: false });
  const output = await paths();
  const local = localReconcilerFor(runtime);
  let artifactChecks = 0;
  const delayedLocal = {
    ...local,
    async privateChatArtifacts(input) {
      artifactChecks += 1;
      if (artifactChecks === 1) return { outcome: 'pending', evidence: { source: 'local-db-message' } };
      const pair = [input.userId, input.friendUserId].sort();
      runtime.privateChats.set(pair.join(':'), new Map([[pair[0], '201'], [pair[1], '202']]));
      return local.privateChatArtifacts(input);
    },
  };

  const result = await prepareBatch({
    batchId: 'fmc001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: delayedLocal,
    scheduler: { async wait() {} }, pollAttempts: 3, setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.mutationCounts.addFriend, 1);
  assert.equal(artifactChecks, 2);
  assert.equal(result.manifest.privateChats.length, 2);
});

test('completed friendship resumes Message artifacts with unavailable API without addFriend retry', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  let crashed = false;
  await assert.rejects(prepareBatch({
    batchId: 'cfa001', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
    hooks: {
      afterCompleted({ operation }) {
        if (operation === 'addFriend' && !crashed) {
          crashed = true;
          throw new Error('crash after friend completed');
        }
      },
    },
  }), /crash after friend completed/);
  const baseClient = createGatewayHttpClient({ config: runtime.config });
  const unavailableChatClient = {
    ...baseClient,
    async getChatList() { throw new GatewayBusinessError(10001, 'AUTH_FAIL'); },
  };

  const result = await prepareBatch({
    batchId: 'cfa001', resume: true, cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, httpClient: unavailableChatClient,
    localReconciler: localReconcilerFor(runtime), setupOptions: FAST_SETUP,
  });

  assert.equal(runtime.mutationCounts.addFriend, 1);
  assert.equal(result.manifest.privateChats.length, 2);
});

test('generated signup and group values fit production column lengths', async () => {
  const runtime = await mockRuntime();
  const output = await paths();

  await prepareBatch({
    batchId: 'abc123', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });

  const createdUsers = [...runtime.users.values()];
  assert.deepEqual(createdUsers.map(({ username }) => username), ['loadtest-abc123-000', 'loadtest-abc123-001']);
  assert.equal(createdUsers.every(({ username }) => username.length <= 20), true);
  assert.equal(createdUsers.every(({ email }) => email.length <= 45 && email.endsWith('@lt.dev')), true);
  assert.equal(runtime.groups[0].groupName, 'loadtest-abc123-g000');
  assert.equal(runtime.groups[0].groupName.length <= 20, true);
});

test('rejects invalid batch IDs and excessive users before preflight or mutation', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  let preflights = 0;
  const local = { ...localReconcilerFor(runtime), async preflight() { preflights += 1; } };

  await assert.rejects(prepareBatch({
    batchId: 'long007', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: local,
  }), /batchId.*1.*6|batchId.*lowercase/i);
  await assert.rejects(dataMain(['prepare', '--batch', 'abc123', '--users', '3210'], {
    defaults, localReconciler: {}, makeDirectory: async () => undefined,
    prepareBatch: async () => assert.fail('prepare must not run'),
  }), /maximum|max.*connection|3200/i);
  assert.equal(preflights, 0);
  assert.deepEqual(runtime.mutationCounts, { signUp: 0, addFriend: 0, createGroup: 0 });
});

test('default data batch is a six-character lowercase base36 identifier', async () => {
  let received;
  await dataMain(['prepare', '--users', '20'], {
    defaults,
    randomBatchId: () => 'a1b2c3',
    localReconciler: {},
    makeDirectory: async () => undefined,
    prepareBatch: async (input) => { received = input; },
  });
  assert.equal(received.batchId, 'a1b2c3');
  assert.equal(received.paths.journal, join('.harness', 'load-test', 'a1b2c3', 'journal.jsonl'));
});

test('last supported two-user cohort keeps usernames and group name within production columns', () => {
  const plan = createPreparationPlan('abc123', 1600, 2);
  const last = plan.at(-1);

  assert.equal(last.groupName, 'loadtest-abc123-g18f');
  assert.equal(last.groupName.length, 20);
  assert.deepEqual(last.members.map(({ username }) => username), [
    'loadtest-abc123-2gu',
    'loadtest-abc123-2gv',
  ]);
  assert.equal(last.members.every(({ username }) => username.length <= 19), true);
  assert.equal(last.members.every(({ email }) => email.length <= 45), true);
});

test('resume repairs a torn main WAL before recovery and does not repeat mutations', async () => {
  const runtime = await mockRuntime();
  const output = await paths();
  await prepareBatch({
    batchId: 'torn01', cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });
  const counts = { ...runtime.mutationCounts };
  await appendFile(output.journal, '{"schemaVersion":1,"batchId":"torn');

  await prepareBatch({
    batchId: 'torn01', resume: true, cohortCount: 1, groupSize: 2,
    config: runtime.config, paths: output, localReconciler: localReconcilerFor(runtime),
    setupOptions: FAST_SETUP,
  });

  assert.deepEqual(runtime.mutationCounts, counts);
  assert.equal((await readFile(output.journal, 'utf8')).endsWith('\n'), true);
});

for (const scenario of [
  { name: 'duplicate same-name group', transform: (items) => [...items, ...items], pattern: /exactly one|duplicate/i },
  { name: 'wrong group owner', transform: (items) => items.map((item) => ({ ...item, ownerId: '999' })), pattern: /owner/i },
  { name: 'wrong group member count', transform: (items) => items.map((item) => ({ ...item, memberCount: 99 })), pattern: /memberCount|member count/i },
]) {
  test(`group API reconciliation fails closed for ${scenario.name}`, async () => {
    const runtime = await mockRuntime({ transformGroupList: scenario.transform });
    const output = await paths();
    const local = {
      ...localReconcilerFor(runtime),
      async createGroup() { assert.fail('invalid API facts must not fall back to local mutation reconciliation'); },
    };

    await assert.rejects(prepareBatch({
      batchId: 'api001', cohortCount: 1, groupSize: 2,
      config: runtime.config, paths: output, localReconciler: local,
      setupOptions: FAST_SETUP,
    }), scenario.pattern);
    assert.equal(runtime.mutationCounts.createGroup, 1);
  });
}
