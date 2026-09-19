import { randomUUID } from 'node:crypto';
import { mkdir, open, rename, rm } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const MAX_LONG = '9223372036854775807';
const MAX_CONCURRENCY = 16;
const POLL_INTERVAL_MS = 100;
const MAX_TIMER_MS = 2_147_483_647;

export async function main(argv = process.argv.slice(2)) {
  const options = parseOptions(argv);
  const credentialPath = requiredOption(options, 'credentials');
  const outputPath = requiredOption(options, 'output');
  if (resolve(credentialPath) === resolve(outputPath)) {
    throw new Error('credentials and output paths must differ');
  }
  const target = parseTarget(requiredOption(options, 'url'));
  const timeoutMs = parseTimeout(requiredOption(options, 'timeout-seconds'));
  const allUserIds = await readUserIds(credentialPath);
  const userCount = parseUserCount(requiredOption(options, 'user-count'), allUserIds.length);
  const userIds = allUserIds.slice(0, userCount);

  // A failed run must not leave stale evidence that could be attributed to this invocation.
  await rm(outputPath, { force: true });
  const startedAt = Date.now();
  const deadline = startedAt + timeoutMs;
  let remainingRouteCount;
  let complete = true;

  try {
    while (true) {
      if (remainingRouteCount !== undefined && Date.now() >= deadline) {
        break;
      }
      remainingRouteCount = await queryRemainingRoutes({
        userIds,
        target,
        deadline,
      });
      if (remainingRouteCount === 0 || Date.now() >= deadline) {
        break;
      }
      await delay(Math.min(POLL_INTERVAL_MS, Math.max(1, deadline - Date.now())));
    }
  } catch {
    complete = false;
    remainingRouteCount = null;
  }

  await publishEvidence(outputPath, {
    version: 1,
    boundary: 'connection-route-cleanup',
    userCount: userIds.length,
    remainingRouteCount,
    complete,
    ...(complete ? {} : { failureCategory: 'query_incomplete' }),
    checkedAt: new Date().toISOString(),
  });
}

function parseOptions(argv) {
  const allowed = new Set(['credentials', 'url', 'timeout-seconds', 'output', 'user-count']);
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    if (typeof flag !== 'string' || !flag.startsWith('--')) {
      throw new Error('unexpected positional argument');
    }
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`unknown option --${name}`);
    if (Object.hasOwn(options, name)) throw new Error(`duplicate option --${name}`);
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`missing value for --${name}`);
    }
    options[name] = value;
    index += 1;
  }
  return options;
}

function parseUserCount(value, available) {
  if (!/^[1-9]\d*$/.test(value)) throw new Error('user-count must be a positive integer');
  const count = Number(value);
  if (!Number.isSafeInteger(count) || count > available) {
    throw new Error('user-count exceeds available credentials');
  }
  return count;
}

function requiredOption(options, name) {
  const value = options[name];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`--${name} is required`);
  }
  return value;
}

function parseTarget(value) {
  let target;
  try {
    target = new URL(value);
  } catch {
    throw new Error('url must be a valid loopback HTTP origin');
  }
  const hostname = target.hostname.toLowerCase();
  const loopback = hostname === '[::1]'
    || hostname === '::1'
    || /^127(?:\.\d{1,3}){3}$/.test(hostname)
      && hostname.split('.').slice(1).every((part) => Number(part) <= 255);
  if (target.protocol !== 'http:' || !loopback
      || target.username !== '' || target.password !== ''
      || target.search !== '' || target.hash !== ''
      || (target.pathname !== '' && target.pathname !== '/')) {
    throw new Error('url must be a credential-free loopback HTTP origin');
  }
  return target;
}

function parseTimeout(value) {
  const seconds = Number(value);
  const milliseconds = Math.ceil(seconds * 1_000);
  if (!Number.isFinite(seconds) || seconds <= 0 || milliseconds > MAX_TIMER_MS) {
    throw new Error('timeout-seconds must be a positive bounded number');
  }
  return milliseconds;
}

async function readUserIds(filePath) {
  let handle;
  let source;
  try {
    handle = await open(filePath, 'r');
    const metadata = await handle.stat();
    if (!metadata.isFile() || (metadata.mode & 0o777) !== 0o600) {
      throw new Error('credentials must be a regular file with mode 0600');
    }
    source = JSON.parse(await handle.readFile('utf8'));
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error('credentials must contain valid JSON');
    }
    throw error;
  } finally {
    await handle?.close();
  }
  if (!isPlainObject(source) || !Array.isArray(source.credentials)
      || source.credentials.length === 0) {
    throw new Error('credentials must contain a nonempty credentials array');
  }
  const userIds = source.credentials.map((credential) => {
    if (!isPlainObject(credential)) {
      throw new Error('each credential must be an object with a canonical userId');
    }
    return canonicalLong(credential.userId);
  });
  if (new Set(userIds).size !== userIds.length) {
    throw new Error('credential userIds must be unique');
  }
  return userIds;
}

function canonicalLong(value) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)
      || value.length > MAX_LONG.length
      || (value.length === MAX_LONG.length && value > MAX_LONG)) {
    throw new Error('credential userId must be a canonical positive decimal string within Java Long range');
  }
  return value;
}

async function queryRemainingRoutes({ userIds, target, deadline }) {
  let nextIndex = 0;
  let remaining = 0;
  let firstFailure;
  const workers = Array.from(
    { length: Math.min(MAX_CONCURRENCY, userIds.length) },
    async () => {
      while (firstFailure === undefined) {
        const index = nextIndex;
        nextIndex += 1;
        if (index >= userIds.length) return;
        try {
          const routeCount = await queryUserRoutes({
            userId: userIds[index],
            target,
            timeoutMs: Math.max(1, deadline - Date.now()),
          });
          remaining += routeCount;
        } catch (error) {
          firstFailure ??= error;
        }
      }
    },
  );
  await Promise.all(workers);
  if (firstFailure !== undefined) throw firstFailure;
  return remaining;
}

async function queryUserRoutes({ userId, target, timeoutMs }) {
  const url = new URL('/management/connections', target);
  url.searchParams.set('userId', userId);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Math.min(timeoutMs, MAX_TIMER_MS));
  let response;
  let payload;
  try {
    response = await fetch(url, {
      method: 'GET',
      headers: { accept: 'application/json' },
      redirect: 'error',
      signal: controller.signal,
    });
    payload = await response.text();
  } catch {
    throw new Error('route cleanup request failed');
  } finally {
    clearTimeout(timeout);
  }
  if (!response.ok) {
    throw new Error('route cleanup endpoint returned a non-success HTTP status');
  }
  let result;
  try {
    result = JSON.parse(payload);
  } catch {
    throw new Error('route cleanup endpoint returned invalid JSON');
  }
  if (!isPlainObject(result) || !Number.isInteger(result.code)) {
    throw new Error('route cleanup endpoint returned an invalid HttpResult');
  }
  if (result.code !== 0) {
    throw new Error(`route cleanup endpoint returned business code ${result.code}`);
  }
  if (!Array.isArray(result.data)) {
    throw new Error('route cleanup endpoint data must be an array');
  }
  return result.data.length;
}

async function publishEvidence(filePath, evidence) {
  const directory = dirname(filePath);
  await mkdir(directory, { recursive: true });
  const temporaryPath = `${directory}/.${basename(filePath)}.${process.pid}.${randomUUID()}.tmp`;
  let handle;
  try {
    handle = await open(temporaryPath, 'wx', 0o600);
    await handle.writeFile(`${JSON.stringify(evidence, null, 2)}\n`);
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(temporaryPath, filePath);
    await syncDirectory(directory);
  } catch (error) {
    await handle?.close().catch(() => undefined);
    await rm(temporaryPath, { force: true }).catch(() => undefined);
    throw error;
  }
}

async function syncDirectory(directory) {
  const handle = await open(directory, 'r');
  try {
    await handle.sync();
  } finally {
    await handle.close();
  }
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
