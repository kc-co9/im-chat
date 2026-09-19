import * as nodeFileSystem from 'node:fs/promises';
import { dirname } from 'node:path';

const SCHEMA_VERSION = 1;
const SECRET_KEY = /(authorization|cookie|credential|password|secret|token)/i;
const TYPES = new Set(['intent', 'invoked', 'reconciled', 'completed', 'checkpoint']);
const OUTCOMES = new Set(['committed', 'not_committed']);
const OPERATIONS = new Set(['signUp', 'addFriend', 'createGroup']);
const MAX_LONG = 9223372036854775807n;
const MAX_OPERATION_ID_LENGTH = 128;
const RECORD_FIELDS = Object.freeze({
  intent: ['schemaVersion', 'batchId', 'operationId', 'type', 'operation', 'ownership'],
  invoked: ['schemaVersion', 'batchId', 'operationId', 'type', 'operation', 'attempt'],
  reconciled: [
    'schemaVersion',
    'batchId',
    'operationId',
    'type',
    'operation',
    'attempt',
    'outcome',
    'result',
    'evidence',
  ],
  completed: ['schemaVersion', 'batchId', 'operationId', 'type', 'operation', 'attempt', 'result'],
  checkpoint: [
    'schemaVersion',
    'batchId',
    'operationId',
    'type',
    'operation',
    'checkpointId',
    'completedOperationIds',
  ],
});

export function createJournal({ filePath, batchId, fileSystem = {} }) {
  assertNonEmptyString(filePath, 'filePath');
  assertNonEmptyString(batchId, 'batchId');
  const fs = { ...nodeFileSystem, ...fileSystem };
  let state;
  let durabilityUncertain = false;
  let queue = Promise.resolve();

  const append = (createRecord) => {
    const pending = queue.then(async () => {
      if (state === undefined) {
        state = await loadState(fs, filePath, batchId);
      }
      if (state.truncatedTail !== null) {
        throw new Error('journal truncated tail requires explicit repair before append');
      }
      if (state.missingFinalNewline) {
        await appendDurably(fs, filePath, '\n');
        state.missingFinalNewline = false;
      }
      const record = createRecord(state);
      const candidate = cloneState(state);
      if (!applyRecord(candidate, record, batchId)) {
        if (durabilityUncertain) {
          await syncExisting(fs, filePath);
          durabilityUncertain = false;
        }
        return cloneJson(record);
      }
      await appendLine(fs, filePath, record);
      state = candidate;
      durabilityUncertain = false;
      return cloneJson(record);
    }).catch((error) => {
      state = undefined;
      durabilityUncertain = true;
      throw error;
    });
    queue = pending.catch(() => undefined);
    return pending;
  };

  return {
    async appendIntent(value) {
      assertExactFields(value, ['operationId', 'operation', 'ownership'], 'intent input');
      assertSafeJson(value.ownership, 'intent ownership');
      return append(() => ({
        schemaVersion: SCHEMA_VERSION,
        batchId,
        operationId: value.operationId,
        type: 'intent',
        operation: value.operation,
        ownership: cloneJson(value.ownership),
      }));
    },
    async appendInvoked(value) {
      assertExactFields(value, ['operationId', 'attempt'], 'invoked input');
      assertAttempt(value.attempt);
      return append((current) => transitionRecord(
        current,
        batchId,
        value.operationId,
        'invoked',
        value.attempt,
      ));
    },
    async appendReconciled(value) {
      assertExactFields(
        value,
        ['operationId', 'attempt', 'outcome', 'result', 'evidence'],
        'reconciled input',
      );
      assertAttempt(value.attempt);
      assertOutcome(value.outcome);
      assertSafeJson(value.result, 'reconciliation result');
      assertSafeJson(value.evidence, 'reconciliation evidence');
      return append((current) => ({
        ...transitionRecord(
          current,
          batchId,
          value.operationId,
          'reconciled',
          value.attempt,
        ),
        outcome: value.outcome,
        result: cloneJson(value.result),
        evidence: cloneJson(value.evidence),
      }));
    },
    async appendCompleted(value) {
      assertExactFields(value, ['operationId', 'result'], 'completed input');
      assertSafeJson(value.result, 'completed result');
      return append((current) => {
        const operation = requireOperation(current, value.operationId);
        return {
          ...transitionRecord(
            current,
            batchId,
            value.operationId,
            'completed',
            operation.attempt,
          ),
          result: cloneJson(value.result),
        };
      });
    },
    async appendCheckpoint(value) {
      assertExactFields(value, ['checkpointId', 'completedOperationIds'], 'checkpoint input');
      assertNonEmptyString(value.checkpointId, 'checkpointId');
      assertStringArray(value.completedOperationIds, 'completedOperationIds');
      return append(() => ({
        schemaVersion: SCHEMA_VERSION,
        batchId,
        operationId: `checkpoint:${value.checkpointId}`,
        type: 'checkpoint',
        operation: 'checkpoint',
        checkpointId: value.checkpointId,
        completedOperationIds: [...value.completedOperationIds],
      }));
    },
  };
}

export async function recoverJournal({ filePath, batchId, fileSystem = {} }) {
  assertNonEmptyString(filePath, 'filePath');
  assertNonEmptyString(batchId, 'batchId');
  const fs = { ...nodeFileSystem, ...fileSystem };
  return summary(await loadState(fs, filePath, batchId));
}

export async function repairTruncatedTail({ filePath, batchId, fileSystem = {} }) {
  assertNonEmptyString(filePath, 'filePath');
  assertNonEmptyString(batchId, 'batchId');
  const fs = { ...nodeFileSystem, ...fileSystem };
  const state = await loadState(fs, filePath, batchId);
  if (state.truncatedTail === null) {
    return { repaired: false, removedBytes: 0 };
  }

  const content = await fs.readFile(filePath, 'utf8');
  const targetSize = Buffer.byteLength(content) - state.truncatedTail.byteLength;
  const handle = await fs.open(filePath, 'r+');
  try {
    await handle.truncate(targetSize);
    await handle.sync();
  } finally {
    await handle.close();
  }
  await syncDirectory(fs, dirname(filePath));
  return { repaired: true, removedBytes: state.truncatedTail.byteLength };
}

async function loadState(fs, filePath, batchId) {
  let content;
  try {
    content = await fs.readFile(filePath, 'utf8');
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return emptyState();
    }
    throw error;
  }

  const state = emptyState();
  const lines = content.split('\n');
  if (content.endsWith('\n')) {
    lines.pop();
  } else {
    const tail = lines.pop();
    if (tail.length > 0) {
      try {
        JSON.parse(tail);
        lines.push(tail);
        state.missingFinalNewline = true;
      } catch {
        state.truncatedTail = {
          lineNumber: lines.length + 1,
          byteLength: Buffer.byteLength(tail),
        };
      }
    }
  }

  for (let index = 0; index < lines.length; index += 1) {
    let record;
    try {
      record = JSON.parse(lines[index]);
    } catch {
      throw new Error(`malformed journal line ${index + 1}`);
    }
    try {
      applyRecord(state, record, batchId);
    } catch (error) {
      throw new Error(`journal line ${index + 1}: ${error.message}`);
    }
  }
  return state;
}

function emptyState() {
  return {
    operations: new Map(),
    ownedUsers: new Map(),
    checkpoints: new Map(),
    records: [],
    truncatedTail: null,
    missingFinalNewline: false,
  };
}

function cloneState(state) {
  return {
    operations: new Map([...state.operations].map(([operationId, operation]) => [operationId, {
      intent: operation.intent,
      lastType: operation.lastType,
      attempt: operation.attempt,
      reconciliation: operation.reconciliation,
      transitions: new Map(operation.transitions),
    }])),
    ownedUsers: new Map(state.ownedUsers),
    checkpoints: new Map(state.checkpoints),
    records: [...state.records],
    truncatedTail: state.truncatedTail,
    missingFinalNewline: state.missingFinalNewline,
  };
}

function applyRecord(state, record, batchId) {
  validateRecordEnvelope(record, batchId);
  if (record.type === 'checkpoint') {
    validateRecordPayload(record, batchId);
    return applyCheckpoint(state, record);
  }

  const current = state.operations.get(record.operationId);
  if (record.type === 'intent') {
    if (current !== undefined) {
      if (sameJson(current.intent, record)) {
        return false;
      }
      throw new Error(`operationId conflict: ${record.operationId}`);
    }
    validateRecordPayload(record, batchId);
    validateOwnershipReferences(state, record);
    state.operations.set(record.operationId, {
      intent: record,
      lastType: 'intent',
      attempt: 0,
      reconciliation: null,
      transitions: new Map(),
    });
    state.records.push(record);
    return true;
  }

  if (current === undefined) {
    throw new Error(`invalid transition for operationId ${record.operationId}: intent required`);
  }
  if (current.intent.operation !== record.operation) {
    throw new Error(`operationId conflict: ${record.operationId}`);
  }
  validateRecordPayload(record, batchId);
  const transitionId = `${record.type}:${record.attempt}`;
  const duplicate = current.transitions.get(transitionId);
  if (duplicate !== undefined) {
    if (sameJson(duplicate, record)) {
      return false;
    }
    throw new Error(`operationId conflict for ${transitionId}: ${record.operationId}`);
  }

  validateTransition(current, record);
  validateResultMatchesIntent(current, record);
  if (record.type === 'completed' && record.operation === 'signUp') {
    establishUser(state, record);
  }
  current.transitions.set(transitionId, record);
  current.lastType = record.type;
  current.attempt = record.attempt;
  if (record.type === 'reconciled') {
    current.reconciliation = record;
  }
  state.records.push(record);
  return true;
}

function validateTransition(current, record) {
  if (record.type === 'invoked') {
    const firstAttempt = current.lastType === 'intent' && record.attempt === 1;
    const retry = current.lastType === 'reconciled'
      && current.reconciliation.outcome === 'not_committed'
      && record.attempt === current.attempt + 1;
    if (!firstAttempt && !retry) {
      if (current.reconciliation?.outcome === 'committed') {
        throw new Error('invalid transition: committed reconciliation cannot retry');
      }
      throw new Error(`invalid invoked attempt ${record.attempt}`);
    }
    return;
  }
  if (record.type === 'reconciled') {
    if (current.lastType !== 'invoked' || record.attempt !== current.attempt) {
      throw new Error(`invalid transition to reconciled attempt ${record.attempt}`);
    }
    return;
  }
  if (record.type === 'completed') {
    if (current.lastType !== 'reconciled'
        || current.reconciliation.outcome !== 'committed'
        || record.attempt !== current.attempt) {
      throw new Error('invalid transition: completion requires committed reconciliation');
    }
  }
}

function applyCheckpoint(state, record) {
  const existing = state.checkpoints.get(record.checkpointId);
  if (existing !== undefined) {
    if (sameJson(existing, record)) {
      return false;
    }
    throw new Error(`checkpoint conflict: ${record.checkpointId}`);
  }
  for (const operationId of record.completedOperationIds) {
    if (state.operations.get(operationId)?.lastType !== 'completed') {
      throw new Error(`checkpoint requires completed operationId ${operationId}`);
    }
  }
  state.checkpoints.set(record.checkpointId, record);
  state.records.push(record);
  return true;
}

function validateRecordEnvelope(record, batchId) {
  if (!isPlainObject(record)) {
    throw new Error('journal record must be an object');
  }
  if (record.schemaVersion !== SCHEMA_VERSION) {
    throw new Error('unsupported journal schemaVersion');
  }
  if (!TYPES.has(record.type)) {
    throw new Error('unknown journal record type');
  }
  assertExactFields(record, RECORD_FIELDS[record.type], `${record.type} record`);
  if (record.batchId !== batchId) {
    throw new Error('journal batch mismatch');
  }
  assertOperationId(record.operationId);
  if (record.type === 'checkpoint') {
    if (record.operation !== 'checkpoint') {
      throw new Error('checkpoint operation is invalid');
    }
  } else if (!OPERATIONS.has(record.operation)) {
    throw new Error('unknown journal operation');
  }
}

function validateRecordPayload(record, batchId) {
  if (record.type === 'intent') {
    validateOwnership(record.operation, record.ownership, batchId);
  } else if (record.type === 'checkpoint') {
    assertNonEmptyString(record.checkpointId, 'checkpointId');
    assertStringArray(record.completedOperationIds, 'completedOperationIds');
  } else {
    assertAttempt(record.attempt);
    if (record.type === 'reconciled') {
      assertOutcome(record.outcome);
      if (record.outcome === 'not_committed') {
        if (record.result !== null) {
          throw new Error('not_committed reconciliation result must be null');
        }
      } else {
        validateOperationResult(record.operation, record.result);
      }
      assertSafeJson(record.evidence, 'reconciliation evidence');
    } else if (record.type === 'completed') {
      validateOperationResult(record.operation, record.result);
    }
  }
}

function validateOwnership(operation, ownership, batchId) {
  const prefix = `loadtest-${batchId}-`;
  if (operation === 'signUp') {
    assertExactFields(ownership, ['email', 'username'], 'signUp ownership');
    assertPrefixed(ownership.email, prefix, 'signUp email');
    assertPrefixed(ownership.username, prefix, 'signUp username');
    return;
  }
  if (operation === 'addFriend') {
    assertExactFields(ownership, ['userId', 'friendUserId'], 'addFriend ownership');
    assertCanonicalLong(ownership.userId, 'addFriend userId');
    assertCanonicalLong(ownership.friendUserId, 'addFriend friendUserId');
    if (ownership.userId === ownership.friendUserId) {
      throw new Error('addFriend users must be distinct');
    }
    return;
  }
  assertExactFields(ownership, ['ownerId', 'memberIds', 'groupName'], 'createGroup ownership');
  assertCanonicalLong(ownership.ownerId, 'createGroup ownerId');
  assertCanonicalLongArray(ownership.memberIds, 'createGroup memberIds');
  if (ownership.memberIds.includes(ownership.ownerId)) {
    throw new Error('createGroup owner must be excluded from memberIds');
  }
  assertPrefixed(ownership.groupName, prefix, 'createGroup groupName');
}

function validateOwnershipReferences(state, record) {
  if (record.operation === 'signUp') {
    return;
  }
  const ownership = record.ownership;
  const userIds = record.operation === 'addFriend'
    ? [ownership.userId, ownership.friendUserId]
    : [ownership.ownerId, ...ownership.memberIds];
  if (userIds.some((userId) => !state.ownedUsers.has(userId))) {
    throw new Error(`${record.operation} requires users established by completed signUp operations`);
  }
}

function validateOperationResult(operation, result) {
  if (operation === 'signUp') {
    assertExactFields(result, ['userId'], 'signUp result');
    assertCanonicalLong(result.userId, 'signUp result userId');
    return;
  }
  if (operation === 'addFriend') {
    assertExactFields(result, ['userId', 'friendUserId'], 'addFriend result');
    assertCanonicalLong(result.userId, 'addFriend result userId');
    assertCanonicalLong(result.friendUserId, 'addFriend result friendUserId');
    return;
  }
  if (isPlainObject(result) && Object.keys(result).length === 1 && Object.hasOwn(result, 'groupId')) {
    assertCanonicalLong(result.groupId, 'createGroup result groupId');
    return;
  }
  assertExactFields(result, ['groupId', 'ownerChatId', 'memberChats'], 'createGroup result');
  assertCanonicalLong(result.groupId, 'createGroup result groupId');
  assertCanonicalLong(result.ownerChatId, 'createGroup result ownerChatId');
  if (!Array.isArray(result.memberChats) || result.memberChats.length === 0) {
    throw new Error('createGroup result memberChats must be a non-empty array');
  }
  const userIds = new Set();
  const chatIds = new Set([result.ownerChatId]);
  for (const [index, chat] of result.memberChats.entries()) {
    assertExactFields(chat, ['userId', 'chatId'], `createGroup result memberChats[${index}]`);
    assertCanonicalLong(chat.userId, `createGroup result memberChats[${index}].userId`);
    assertCanonicalLong(chat.chatId, `createGroup result memberChats[${index}].chatId`);
    if (userIds.has(chat.userId) || chatIds.has(chat.chatId)) {
      throw new Error('createGroup result contains duplicate member or chat IDs');
    }
    userIds.add(chat.userId);
    chatIds.add(chat.chatId);
  }
}

function validateResultMatchesIntent(current, record) {
  const intent = current.intent;
  if (record.type === 'reconciled' && record.outcome === 'not_committed') {
    return;
  }
  if (record.type !== 'reconciled' && record.type !== 'completed') {
    return;
  }
  if (intent.operation === 'addFriend') {
    if (record.result.userId !== intent.ownership.userId
        || record.result.friendUserId !== intent.ownership.friendUserId) {
      throw new Error('addFriend result does not match intent ownership');
    }
  } else if (intent.operation === 'createGroup') {
    if (!Object.hasOwn(record.result, 'memberChats')) {
      return;
    }
    const resultUsers = record.result.memberChats.map(({ userId }) => userId);
    if (!sameStringSet(resultUsers, intent.ownership.memberIds)) {
      throw new Error('createGroup result memberChats do not match intent ownership');
    }
  }
  if (record.type === 'completed' && !sameJson(record.result, current.reconciliation.result)) {
    throw new Error('completed result conflict');
  }
}

function establishUser(state, record) {
  const existing = state.ownedUsers.get(record.result.userId);
  if (existing !== undefined && existing !== record.operationId) {
    throw new Error('signUp result userId is already established by another operation');
  }
  state.ownedUsers.set(record.result.userId, record.operationId);
}

function transitionRecord(state, batchId, operationId, type, attempt) {
  const operation = requireOperation(state, operationId);
  return {
    schemaVersion: SCHEMA_VERSION,
    batchId,
    operationId,
    type,
    operation: operation.intent.operation,
    attempt,
  };
}

function requireOperation(state, operationId) {
  assertNonEmptyString(operationId, 'operationId');
  const operation = state.operations.get(operationId);
  if (operation === undefined) {
    throw new Error(`invalid transition for operationId ${operationId}: intent required`);
  }
  return operation;
}

async function appendLine(fs, filePath, record) {
  await appendDurably(fs, filePath, `${JSON.stringify(record)}\n`);
}

async function syncExisting(fs, filePath) {
  const handle = await fs.open(filePath, 'a', 0o600);
  try {
    if (typeof handle.chmod === 'function') {
      await handle.chmod(0o600);
    }
    await handle.sync();
  } finally {
    await handle.close();
  }
  await syncDirectory(fs, dirname(filePath));
}

async function appendDurably(fs, filePath, value) {
  const handle = await fs.open(filePath, 'a', 0o600);
  try {
    if (typeof handle.chmod === 'function') {
      await handle.chmod(0o600);
    }
    await handle.writeFile(value);
    await handle.sync();
  } finally {
    await handle.close();
  }
  await syncDirectory(fs, dirname(filePath));
}

async function syncDirectory(fs, directory) {
  let handle;
  try {
    handle = await fs.open(directory, 'r');
    await handle.sync();
  } catch (error) {
    const unsupportedOnWindows = process.platform === 'win32'
      && ['EINVAL', 'ENOTSUP', 'EISDIR', 'EPERM'].includes(error?.code);
    if (!unsupportedOnWindows) {
      throw error;
    }
  } finally {
    if (handle !== undefined) {
      await handle.close();
    }
  }
}

function summary(state) {
  const completed = [];
  const unresolvedIntents = [];
  for (const current of state.operations.values()) {
    const common = {
      operationId: current.intent.operationId,
      operation: current.intent.operation,
      ownership: cloneJson(current.intent.ownership),
    };
    const reconciliation = current.reconciliation === null ? undefined : {
      attempt: current.reconciliation.attempt,
      outcome: current.reconciliation.outcome,
      result: cloneJson(current.reconciliation.result),
      evidence: cloneJson(current.reconciliation.evidence),
    };
    if (current.lastType === 'completed') {
      completed.push({
        ...common,
        result: cloneJson(current.transitions.get(`completed:${current.attempt}`).result),
        lastType: 'completed',
        ...(reconciliation === undefined ? {} : { reconciliation }),
      });
    } else {
      unresolvedIntents.push({
        ...common,
        lastType: current.lastType,
        ...(current.attempt === 0 ? {} : { attempt: current.attempt }),
        ...(reconciliation === undefined ? {} : { reconciliation }),
      });
    }
  }
  return {
    completed,
    unresolvedIntents,
    checkpoints: [...state.checkpoints.values()].map(cloneJson),
    truncatedTail: state.truncatedTail === null ? null : { ...state.truncatedTail },
  };
}

function assertExactFields(value, fields, name) {
  if (!isPlainObject(value)) {
    throw new Error(`${name} must be an object`);
  }
  const allowed = new Set(fields);
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) {
      throw new Error(`unknown field in ${name}: ${key}`);
    }
  }
  for (const field of fields) {
    if (!Object.hasOwn(value, field)) {
      throw new Error(`${name} requires ${field}`);
    }
  }
}

function assertAttempt(value) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error('attempt must be a positive integer');
  }
}

function assertOutcome(value) {
  if (!OUTCOMES.has(value)) {
    throw new Error('reconciliation outcome must be committed or not_committed');
  }
}

function assertOperationId(value) {
  if (typeof value !== 'string'
      || value.length === 0
      || value.length > MAX_OPERATION_ID_LENGTH
      || !/^[A-Za-z0-9][A-Za-z0-9._:-]*$/.test(value)) {
    throw new Error('operationId must use a bounded safe charset and length');
  }
  if (SECRET_KEY.test(value)) {
    throw new Error('operationId must not contain credential-like content');
  }
}

function assertCanonicalLong(value, name) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new Error(`${name} must be a canonical positive decimal string`);
  }
  if (BigInt(value) > MAX_LONG) {
    throw new Error(`${name} exceeds signed 64-bit range`);
  }
}

function assertCanonicalLongArray(value, name) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`${name} must be a non-empty array`);
  }
  value.forEach((item) => assertCanonicalLong(item, name));
  if (new Set(value).size !== value.length) {
    throw new Error(`${name} must not contain duplicates`);
  }
}

function assertPrefixed(value, prefix, name) {
  if (typeof value !== 'string' || !value.startsWith(prefix) || value.length === prefix.length) {
    throw new Error(`${name} must use the batch ownership prefix`);
  }
}

function assertStringArray(value, name) {
  if (!Array.isArray(value) || value.length === 0 || value.some((item) => typeof item !== 'string' || item === '')) {
    throw new Error(`${name} must be a non-empty string array`);
  }
  if (new Set(value).size !== value.length) {
    throw new Error(`${name} must not contain duplicates`);
  }
}

function assertSafeJson(value, name, path = name) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertSafeJson(item, name, `${path}[${index}]`));
    return;
  }
  if (isPlainObject(value)) {
    for (const [key, child] of Object.entries(value)) {
      if (SECRET_KEY.test(key)) {
        throw new Error(`secret or credential field is not allowed in ${name}: ${key}`);
      }
      assertSafeJson(child, name, `${path}.${key}`);
    }
    return;
  }
  if (value === null || typeof value === 'string' || typeof value === 'boolean') {
    return;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return;
  }
  throw new Error(`${path} must contain JSON values`);
}

function assertNonEmptyString(value, name) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${name} must be a non-empty string`);
  }
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

function sameJson(left, right) {
  return JSON.stringify(sortJson(left)) === JSON.stringify(sortJson(right));
}

function sameStringSet(left, right) {
  return left.length === right.length && left.every((value) => right.includes(value));
}

function sortJson(value) {
  if (Array.isArray(value)) {
    return value.map(sortJson);
  }
  if (isPlainObject(value)) {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortJson(value[key])]));
  }
  return value;
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
