import assert from 'node:assert/strict';
import { chmod, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { afterEach, test } from 'node:test';

const TOOL = new URL('../tools/route-cleanup.mjs', import.meta.url);
const cleanups = [];

afterEach(async () => {
  await Promise.all(cleanups.splice(0).map((cleanup) => cleanup()));
});

async function temporaryPaths(credentials = defaultCredentials()) {
  const directory = await mkdtemp(join(tmpdir(), 'im-load-route-cleanup-'));
  cleanups.push(() => rm(directory, { recursive: true, force: true }));
  const credentialPath = join(directory, 'credentials.json');
  const outputPath = join(directory, 'evidence.json');
  await writeFile(credentialPath, `${JSON.stringify(credentials)}\n`, { mode: 0o600 });
  return { directory, credentialPath, outputPath };
}

function defaultCredentials() {
  return {
    version: 1,
    credentials: [
      {
        userId: '101',
        accessToken: 'access-secret-one',
        refreshToken: 'refresh-secret-one',
      },
      {
        userId: '102',
        accessToken: 'access-secret-two',
        refreshToken: 'refresh-secret-two',
      },
    ],
  };
}

async function listen(handler) {
  const server = createServer(handler);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  cleanups.push(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();
  return `http://127.0.0.1:${port}`;
}

function sendResult(response, { code = 0, data = [], msg = 'success' } = {}) {
  response.writeHead(200, { 'content-type': 'application/json' });
  response.end(JSON.stringify({ code, msg, data }));
}

async function runTool({ credentialPath, outputPath, url, timeoutSeconds = '1', userCount = '2' }) {
  const child = spawn(process.execPath, [
    TOOL.pathname,
    '--credentials', credentialPath,
    '--url', url,
    '--timeout-seconds', timeoutSeconds,
    '--output', outputPath,
    '--user-count', userCount,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  child.stdout.setEncoding('utf8');
  child.stderr.setEncoding('utf8');
  child.stdout.on('data', (chunk) => { stdout += chunk; });
  child.stderr.on('data', (chunk) => { stderr += chunk; });
  const exitCode = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', resolve);
  });
  return { exitCode, stdout, stderr };
}

test('queries only the effective prefix of round credentials', async () => {
  const queried = [];
  const target = await listen((request, response) => {
    queried.push(new URL(request.url, target).searchParams.get('userId'));
    sendResult(response);
  });
  const paths = await temporaryPaths();

  const result = await runTool({ ...paths, url: target, userCount: '1' });

  assert.equal(result.exitCode, 0, result.stderr);
  assert.deepEqual(queried, ['101']);
  assert.equal(JSON.parse(await readFile(paths.outputPath, 'utf8')).userCount, 1);
});

async function assertMissing(path) {
  await assert.rejects(stat(path), (error) => error?.code === 'ENOENT');
}

test('polls production route endpoint until every user route is empty', async () => {
  const calls = new Map();
  const target = await listen((request, response) => {
    assert.equal(request.method, 'GET');
    const requestUrl = new URL(request.url, target);
    assert.equal(requestUrl.pathname, '/management/connections');
    assert.equal(request.headers.authorization, undefined);
    const userId = requestUrl.searchParams.get('userId');
    const count = (calls.get(userId) ?? 0) + 1;
    calls.set(userId, count);
    sendResult(response, { data: count === 1 ? [{ gatewayId: 'gateway-1' }] : [] });
  });
  const paths = await temporaryPaths();

  const result = await runTool({ ...paths, url: target });

  assert.equal(result.exitCode, 0, result.stderr);
  assert.equal(result.stdout, '');
  assert.equal(result.stderr, '');
  assert.deepEqual([...calls.entries()].sort(), [['101', 2], ['102', 2]]);
  assert.equal((await stat(paths.outputPath)).mode & 0o777, 0o600);
  const evidenceText = await readFile(paths.outputPath, 'utf8');
  const evidence = JSON.parse(evidenceText);
  assert.deepEqual({ ...evidence, checkedAt: '<timestamp>' }, {
    version: 1,
    boundary: 'connection-route-cleanup',
    userCount: 2,
    remainingRouteCount: 0,
    complete: true,
    checkedAt: '<timestamp>',
  });
  assert.equal(Number.isNaN(Date.parse(evidence.checkedAt)), false);
  for (const secret of ['101', '102', 'access-secret', 'refresh-secret']) {
    assert.equal(evidenceText.includes(secret), false);
    assert.equal(result.stdout.includes(secret), false);
    assert.equal(result.stderr.includes(secret), false);
  }
});

test('publishes complete deadline evidence with the remaining route count', async () => {
  const target = await listen((request, response) => {
    sendResult(response, { data: [{ gatewayId: 'still-present' }] });
  });
  const paths = await temporaryPaths();

  const result = await runTool({
    ...paths,
    url: target,
    timeoutSeconds: '0.05',
  });

  assert.equal(result.exitCode, 0, result.stderr);
  const evidence = JSON.parse(await readFile(paths.outputPath, 'utf8'));
  assert.equal(evidence.complete, true);
  assert.equal(evidence.userCount, 2);
  assert.equal(evidence.remainingRouteCount, 2);
});

test('rejects non-loopback, credentialed, queried, and non-root targets', async () => {
  for (const url of [
    'https://127.0.0.1:19020',
    'http://192.0.2.1:19020',
    'http://user:secret@127.0.0.1:19020',
    'http://127.0.0.1:19020?secret=value',
    'http://127.0.0.1:19020/prefix',
  ]) {
    const paths = await temporaryPaths();
    const result = await runTool({ ...paths, url });
    assert.notEqual(result.exitCode, 0, url);
    assert.equal(result.stderr.includes('secret'), false);
    await assertMissing(paths.outputPath);
  }
});

test('rejects credential files that are not mode 0600', async () => {
  const paths = await temporaryPaths();
  await chmod(paths.credentialPath, 0o644);

  const result = await runTool({ ...paths, url: 'http://127.0.0.1:19020' });

  assert.notEqual(result.exitCode, 0);
  assert.match(result.stderr, /0600/);
  await assertMissing(paths.outputPath);
});

test('rejects noncanonical, numeric, or duplicate credential user IDs', async () => {
  for (const credentials of [
    { credentials: [{ userId: 101 }] },
    { credentials: [{ userId: '0101' }] },
    { credentials: [{ userId: '101' }, { userId: '101' }] },
    { credentials: [{ userId: '9223372036854775808' }] },
  ]) {
    const paths = await temporaryPaths(credentials);
    const result = await runTool({ ...paths, url: 'http://127.0.0.1:19020' });
    assert.notEqual(result.exitCode, 0);
    await assertMissing(paths.outputPath);
  }
});

test('publishes incomplete evidence for invalid or business-failed runtime responses', async () => {
  const responses = [
    () => ({ status: 200, body: '{not-json' }),
    () => ({ status: 200, body: JSON.stringify({ code: 0, data: {} }) }),
    () => ({ status: 200, body: JSON.stringify({ code: 10001, msg: 'failed', data: [] }) }),
    () => ({ status: 302, headers: { location: '/redirected' }, body: '' }),
  ];
  let responseIndex = 0;
  const target = await listen((request, response) => {
    if (request.url === '/redirected') {
      assert.fail('redirect must not be followed');
    }
    const current = responses[responseIndex++]();
    response.writeHead(current.status, {
      'content-type': 'application/json',
      ...current.headers,
    });
    response.end(current.body);
  });

  for (let index = 0; index < responses.length; index += 1) {
    const paths = await temporaryPaths({
      credentials: [{ userId: '101', accessToken: 'do-not-publish' }],
    });
    const result = await runTool({ ...paths, url: target, userCount: '1' });
    assert.equal(result.exitCode, 0, result.stderr);
    const evidence = JSON.parse(await readFile(paths.outputPath, 'utf8'));
    assert.equal(evidence.complete, false);
    assert.equal(evidence.remainingRouteCount, null);
    assert.equal(evidence.failureCategory, 'query_incomplete');
    assert.equal((await readFile(paths.outputPath, 'utf8')).includes('do-not-publish'), false);
  }
});

test('publishes incomplete evidence when the route endpoint is unreachable', async () => {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  await new Promise((resolve) => server.close(resolve));
  const paths = await temporaryPaths();

  const result = await runTool({
    ...paths,
    url: `http://127.0.0.1:${port}`,
    timeoutSeconds: '0.1',
  });

  assert.equal(result.exitCode, 0, result.stderr);
  assert.equal(JSON.parse(await readFile(paths.outputPath, 'utf8')).complete, false);
});

test('times out a stalled response body with incomplete evidence', { timeout: 2_000 }, async () => {
  const target = await listen((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.flushHeaders();
    setTimeout(() => {
      response.end(JSON.stringify({ code: 0, msg: 'success', data: [] }));
    }, 150);
  });
  const paths = await temporaryPaths({ credentials: [{ userId: '101' }] });

  const result = await runTool({
    ...paths,
    url: target,
    timeoutSeconds: '0.05',
    userCount: '1',
  });

  assert.equal(result.exitCode, 0, result.stderr);
  assert.equal(JSON.parse(await readFile(paths.outputPath, 'utf8')).complete, false);
});
