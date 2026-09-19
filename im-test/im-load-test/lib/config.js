export const SYSTEM_TAGS = Object.freeze(['scenario', 'name', 'status', 'method']);
export const CUSTOM_TAGS = Object.freeze(['batch', 'round', 'phase', 'message_type']);

const PRESSURE_TARGET_PROTOCOLS = Object.freeze({
  http: new Set(['http:', 'https:']),
  websocket: new Set(['ws:', 'wss:']),
  prometheus: new Set(['http:', 'https:']),
});
const CLEANUP_TARGET_PROTOCOLS = Object.freeze({
  mysql: new Set(['mysql:']),
  redis: new Set(['redis:', 'rediss:']),
});
const SECRET_KEY = /(authorization|cookie|credential|password|secret|token)/i;

export function resolveConfig(defaults, overrides = {}) {
  assertPlainObject(defaults, 'defaults');
  assertPlainObject(overrides, 'overrides');
  rejectSecrets(defaults);
  rejectSecrets(overrides);

  const config = merge(defaults, overrides);
  if (config.k6Version !== '2.2.0') {
    throw new Error('k6Version must be 2.2.0');
  }
  if (config.redirects !== 'error') {
    throw new Error('redirects must be rejected');
  }
  if (config.messageBytes !== 256) {
    throw new Error('messageBytes must be exactly 256');
  }
  if (!Number.isInteger(config.groupSize) || config.groupSize < 2) {
    throw new Error('groupSize must be an integer of at least 2');
  }
  if (!isPlainObject(config.messageMix)
      || !Number.isInteger(config.messageMix.private)
      || !Number.isInteger(config.messageMix.group)
      || config.messageMix.private < 0
      || config.messageMix.group < 0
      || config.messageMix.private + config.messageMix.group !== 100) {
    throw new Error('messageMix private and group percentages must total 100');
  }
  if (!isPlainObject(config.maxRounds)
      || !Number.isInteger(config.maxRounds.connection)
      || !Number.isInteger(config.maxRounds.message)
      || config.maxRounds.connection <= 0
      || config.maxRounds.message <= 0) {
    throw new Error('maxRounds connection and message values must be positive integers');
  }
  assertExactTags(config.systemTags, SYSTEM_TAGS, 'systemTags');
  assertExactTags(config.customTags, CUSTOM_TAGS, 'customTags');
  validateTargets(config.targets, config.allowRemotePressure === true);

  config.levels = {
    connection: normalizeLevels(config.levels?.connection, config.groupSize, 'connection'),
    message: normalizeLevels(config.levels?.message, config.groupSize, 'message'),
  };
  if (config.levels.message.effective.some((level) => level > config.maxMessageRate)) {
    throw new Error('effective message levels must not exceed maxMessageRate');
  }
  return deepFreeze(config);
}

function normalizeLevels(levels, groupSize, name) {
  if (!Array.isArray(levels) || levels.some((level) => !Number.isInteger(level) || level <= 0)) {
    throw new Error(`requested ${name} levels must be positive integers`);
  }
  const requested = [...levels];
  const effective = [...new Set(levels
    .map((level) => Math.floor(level / groupSize) * groupSize)
    .filter((level) => level > 0))];
  if (effective.length === 0) {
    throw new Error(`effective ${name} levels must contain at least one cohort`);
  }
  return { requested, effective };
}

function validateTargets(targets, allowRemotePressure) {
  assertPlainObject(targets, 'targets');
  for (const [name, protocols] of Object.entries(PRESSURE_TARGET_PROTOCOLS)) {
    const target = parseTarget(targets[name], name, protocols);
    if (!allowRemotePressure && !isLocalHostname(target.hostname)) {
      throw new Error(`non-local pressure target requires explicit unlock: ${name}`);
    }
  }

  assertPlainObject(targets.cleanup, 'targets.cleanup');
  for (const [name, value] of Object.entries(targets.cleanup)) {
    const protocols = CLEANUP_TARGET_PROTOCOLS[name];
    if (protocols === undefined) {
      throw new Error(`unsupported cleanup target: ${name}`);
    }
    const target = parseTarget(value, `cleanup.${name}`, protocols);
    if (!isLocalHostname(target.hostname)) {
      throw new Error(`cleanup target must be local: ${name}`);
    }
  }
}

function parseTarget(value, name, protocols) {
  if (typeof value !== 'string') {
    throw new Error(`${name} target must be a URL string`);
  }
  let target;
  try {
    target = new URL(value);
  } catch {
    throw new Error(`${name} target must be a valid URL`);
  }
  if (protocols && !protocols.has(target.protocol)) {
    throw new Error(`${name} target uses unsupported protocol ${target.protocol}`);
  }
  if (target.username || target.password) {
    throw new Error(`${name} target must not contain credentials`);
  }
  if (target.search !== '') {
    throw new Error(`${name} target must not contain a query string`);
  }
  return target;
}

function isLocalHostname(hostname) {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]';
}

function assertExactTags(actual, expected, name) {
  if (!Array.isArray(actual)
      || actual.length !== expected.length
      || actual.some((tag, index) => tag !== expected[index])) {
    throw new Error(`${name} must be exactly ${expected.join(',')}`);
  }
}

function rejectSecrets(value, path = 'config') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => rejectSecrets(item, `${path}[${index}]`));
    return;
  }
  if (!isPlainObject(value)) {
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (SECRET_KEY.test(key)) {
      throw new Error(`secret configuration is not allowed: ${path}.${key}`);
    }
    rejectSecrets(child, `${path}.${key}`);
  }
}

function merge(base, overrides) {
  if (!isPlainObject(base) || !isPlainObject(overrides)) {
    return clone(overrides);
  }
  const result = clone(base);
  for (const [key, value] of Object.entries(overrides)) {
    result[key] = isPlainObject(value) && isPlainObject(result[key])
      ? merge(result[key], value)
      : clone(value);
  }
  return result;
}

function clone(value) {
  if (Array.isArray(value)) {
    return value.map(clone);
  }
  if (isPlainObject(value)) {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, clone(child)]));
  }
  return value;
}

function deepFreeze(value) {
  if (Array.isArray(value)) {
    value.forEach(deepFreeze);
  } else if (isPlainObject(value)) {
    Object.values(value).forEach(deepFreeze);
  }
  return Object.freeze(value);
}

function assertPlainObject(value, name) {
  if (!isPlainObject(value)) {
    throw new Error(`${name} must be an object`);
  }
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
