import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { afterEach, test } from 'node:test';

import { resolveConfig } from '../lib/config.js';
import { GatewayBusinessError, createGatewayHttpClient } from '../tools/lib/http-client.mjs';

const defaults = JSON.parse(await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'));
const servers = [];

afterEach(async () => {
  await Promise.all(servers.splice(0).map((server) => new Promise((resolve) => server.close(resolve))));
});

async function listen(handler) {
  const server = createServer(handler);
  servers.push(server);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  return `http://127.0.0.1:${port}`;
}

function config(http) {
  return resolveConfig(defaults, { targets: { http } });
}

function result(response, data = {}) {
  response.writeHead(200, { 'content-type': 'application/json' });
  response.end(JSON.stringify({ code: 0, msg: 'success', data }));
}

test('uses production Gateway routes, bearer authentication, and lossless long IDs', async () => {
  const requests = [];
  const target = await listen(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    requests.push({ method: request.method, url: request.url, authorization: request.headers.authorization, body });
    const payloads = {
      '/account/user/signUp': {},
      '/account/user/signIn': {
        userId: 9007199254740993n,
        accessToken: 'access-secret',
        accessTokenExpiresAt: '2026-09-15T00:00:00Z',
        refreshToken: 'refresh-secret',
        refreshTokenExpiresAt: '2026-09-16T00:00:00Z',
      },
      '/account/user/userDetail': { userId: 9007199254740993n, email: 'a@example.test', username: 'alice' },
      '/account/user/signOut': null,
      '/social/friend/addFriend': {},
      '/message/chat/getChatList': { chatList: [{ chatId: 9007199254740995n, chatName: 'bob', chatType: 'PRIVATE' }] },
      '/social/group/createGroup': { groupId: 9007199254740997n },
      '/social/group/getGroupList': {
        groupList: [{
          groupId: 9007199254740997n,
          chatId: 9007199254740999n,
          ownerId: 9007199254740993n,
          groupName: 'group',
          memberCount: 2,
        }],
      },
    };
    const data = payloads[request.url];
    assert.notEqual(data, undefined);
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ code: 0, msg: 'success', data }, (_, value) => (
      typeof value === 'bigint' ? value.toString() : value
    )).replace(/"(900719925474099[3579])"/g, '$1'));
  });
  const client = createGatewayHttpClient({ config: config(target) });

  await client.signUp({ operationId: 'signup-1', email: 'a@example.test', username: 'alice', password: 'pw' });
  const login = await client.signIn({ operationId: 'login-1', email: 'a@example.test', password: 'pw' });
  const detail = await client.userDetail({ operationId: 'detail-1', accessToken: 'access-secret' });
  await client.signOut({ operationId: 'logout-1', accessToken: 'access-secret' });
  await client.addFriend({ operationId: 'friend-1', accessToken: 'access-secret', friendUserId: '9007199254740994' });
  const chats = await client.getChatList({ operationId: 'chats-1', accessToken: 'access-secret' });
  const group = await client.createGroup({ operationId: 'group-1', accessToken: 'access-secret', memberIds: ['9007199254740994'], groupName: 'group' });
  const groups = await client.getGroupList({ operationId: 'groups-1', accessToken: 'access-secret' });

  assert.equal(login.userId, '9007199254740993');
  assert.equal(detail.userId, '9007199254740993');
  assert.equal(chats.chatList[0].chatId, '9007199254740995');
  assert.equal(group.groupId, '9007199254740997');
  assert.equal(groups.groupList[0].chatId, '9007199254740999');
  assert.equal(groups.groupList[0].ownerId, '9007199254740993');
  assert.deepEqual(requests.map(({ method, url }) => `${method} ${url}`), [
    'POST /account/user/signUp',
    'POST /account/user/signIn',
    'GET /account/user/userDetail',
    'POST /account/user/signOut',
    'POST /social/friend/addFriend',
    'GET /message/chat/getChatList',
    'POST /social/group/createGroup',
    'GET /social/group/getGroupList',
  ]);
  assert.equal(requests[3].authorization, 'Bearer access-secret');
  assert.deepEqual(JSON.parse(requests[6].body), {
    memberIds: ['9007199254740994'],
    groupName: 'group',
  });
});

test('rejects nonzero business codes and redirect responses', async () => {
  let redirected = false;
  const target = await listen((request, response) => {
    if (request.url === '/account/user/signIn') {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ code: 1001, msg: 'denied', data: {} }));
      return;
    }
    if (request.url === '/redirected') redirected = true;
    response.writeHead(302, { location: '/redirected' });
    response.end();
  });
  const client = createGatewayHttpClient({ config: config(target) });

  await assert.rejects(
    client.signIn({ operationId: 'login-failed', email: 'a@example.test', password: 'pw' }),
    /business code 1001.*denied/i,
  );
  await assert.rejects(
    client.signUp({ operationId: 'signup-redirect', email: 'a@example.test', username: 'alice', password: 'pw' }),
    /redirect|302/i,
  );
  assert.equal(redirected, false);
});

test('exposes the production business code without leaking response credentials', async () => {
  const target = await listen((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ code: 10001, msg: 'AUTH_FAIL', data: { accessToken: 'do-not-leak' } }));
  });
  const client = createGatewayHttpClient({ config: config(target) });

  await assert.rejects(
    client.signIn({ operationId: 'login-auth-fail', email: 'a@example.test', password: 'pw' }),
    (error) => {
      assert.equal(error instanceof GatewayBusinessError, true);
      assert.equal(error.code, 10001);
      assert.equal(error.message, 'HTTP business code 10001: AUTH_FAIL');
      assert.equal(error.message.includes('do-not-leak'), false);
      return true;
    },
  );
});

test('aborts a request at the configured timeout', async () => {
  const target = await listen(() => undefined);
  const client = createGatewayHttpClient({ config: config(target), timeoutMs: 5 });

  await assert.rejects(
    client.signUp({ operationId: 'signup-timeout', email: 'a@example.test', username: 'alice', password: 'pw' }),
    /timed out/i,
  );
});

test('emits operation-only logs without credentials', async () => {
  const logs = [];
  const target = await listen((request, response) => result(response));
  const client = createGatewayHttpClient({
    config: config(target),
    logger: (entry) => logs.push(entry),
  });

  await client.signUp({
    operationId: 'signup-log-safe',
    email: 'loadtest-batch-user@example.test',
    username: 'loadtest-batch-user',
    password: 'password-secret',
  });

  assert.deepEqual(logs, [{ operationId: 'signup-log-safe' }]);
  const serialized = JSON.stringify(logs);
  for (const secret of ['password-secret', 'Bearer', 'authorization', 'accessToken']) {
    assert.equal(serialized.includes(secret), false);
  }
});
