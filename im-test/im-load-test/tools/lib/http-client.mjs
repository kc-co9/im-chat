import { resolveConfig } from '../../lib/config.js';
import { parseHttpResult } from '../../lib/protocol.js';

const DEFAULT_TIMEOUT_MS = 5_000;

export class GatewayBusinessError extends Error {
  constructor(code, businessMessage) {
    super(`HTTP business code ${code}: ${businessMessage}`);
    this.name = 'GatewayBusinessError';
    this.code = code;
  }
}

const ROUTES = Object.freeze({
  signUp: ['POST', '/account/user/signUp'],
  signIn: ['POST', '/account/user/signIn'],
  userDetail: ['GET', '/account/user/userDetail'],
  signOut: ['POST', '/account/user/signOut'],
  addFriend: ['POST', '/social/friend/addFriend'],
  friendList: ['GET', '/social/friend/friendList'],
  getChatList: ['GET', '/message/chat/getChatList'],
  createGroup: ['POST', '/social/group/createGroup'],
  getGroupList: ['GET', '/social/group/getGroupList'],
});

export function createGatewayHttpClient({
  config,
  fetchImpl = globalThis.fetch,
  logger = () => undefined,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  timers = globalThis,
} = {}) {
  const safeConfig = revalidateConfig(config);
  const target = new URL(safeConfig.targets.http);
  assertFunction(fetchImpl, 'fetchImpl');
  assertFunction(logger, 'logger');
  assertPositiveInteger(timeoutMs, 'timeoutMs');

  const call = async (operation, input) => {
    const [method, path] = ROUTES[operation];
    const { operationId, accessToken, signal, ...body } = input;
    assertOperationId(operationId);
    logger({ operationId });
    return request({
      fetchImpl,
      timers,
      timeoutMs,
      signal,
      url: new URL(path, target),
      method,
      accessToken,
      body: method === 'POST' ? body : undefined,
    });
  };

  return Object.freeze({
    signUp: (input) => call('signUp', input),
    signIn: (input) => call('signIn', input),
    userDetail: (input) => call('userDetail', input),
    signOut: (input) => call('signOut', input),
    addFriend: (input) => call('addFriend', input),
    friendList: (input) => call('friendList', input),
    getChatList: (input) => call('getChatList', input),
    createGroup: (input) => call('createGroup', input),
    getGroupList: (input) => call('getGroupList', input),
  });
}

function revalidateConfig(config) {
  const connectionLevels = config?.levels?.connection;
  const messageLevels = config?.levels?.message;
  if (Array.isArray(connectionLevels) && Array.isArray(messageLevels)) {
    return resolveConfig(config);
  }
  return resolveConfig({
    ...config,
    levels: {
      connection: connectionLevels?.requested,
      message: messageLevels?.requested,
    },
  });
}

async function request({
  fetchImpl,
  timers,
  timeoutMs,
  signal,
  url,
  method,
  accessToken,
  body,
}) {
  const controller = new AbortController();
  let timedOut = false;
  const timeout = timers.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const abortFromCaller = () => controller.abort();
  signal?.addEventListener('abort', abortFromCaller, { once: true });
  if (signal?.aborted) controller.abort();

  try {
    const headers = { accept: 'application/json' };
    if (body !== undefined) headers['content-type'] = 'application/json';
    if (accessToken !== undefined) {
      assertNonEmptyString(accessToken, 'accessToken');
      headers.authorization = `Bearer ${accessToken}`;
    }
    const response = await fetchImpl(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual',
      signal: controller.signal,
    });
    if (response.status >= 300 && response.status < 400) {
      throw new Error(`HTTP redirect ${response.status} rejected`);
    }
    if (!response.ok) {
      throw new Error(`HTTP status ${response.status}`);
    }
    const payload = await response.text();
    try {
      return parseHttpResult(payload);
    } catch (error) {
      const match = /^HTTP business code (-?\d+): (.*)$/.exec(error.message);
      if (match !== null) throw new GatewayBusinessError(Number(match[1]), match[2]);
      throw error;
    }
  } catch (error) {
    if (timedOut) {
      throw new Error(`HTTP request timed out after ${timeoutMs}ms`);
    }
    if (signal?.aborted) {
      throw new Error('HTTP request aborted');
    }
    throw error;
  } finally {
    timers.clearTimeout(timeout);
    signal?.removeEventListener('abort', abortFromCaller);
  }
}

function assertOperationId(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 128
      || !/^[A-Za-z0-9][A-Za-z0-9._:-]*$/.test(value)) {
    throw new Error('operationId must use a bounded safe charset');
  }
}

function assertPositiveInteger(value, name) {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
}

function assertNonEmptyString(value, name) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${name} must be a non-empty string`);
  }
}

function assertFunction(value, name) {
  if (typeof value !== 'function') {
    throw new Error(`${name} must be a function`);
  }
}
