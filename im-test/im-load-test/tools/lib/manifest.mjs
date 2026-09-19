import { randomUUID } from 'node:crypto';
import * as nodeFileSystem from 'node:fs/promises';
import { basename, dirname, join } from 'node:path';

const SCHEMA_VERSION = 1;
const SCHEMAS = Object.freeze({
  account: 'im_chat_account',
  social: 'im_chat_social',
  message: 'im_chat_message',
});
const RECEIPT_TYPES = new Set(['PRIVATE_MESSAGE_SEND', 'GROUP_MESSAGE_SEND']);
const MAX_LONG = 9223372036854775807n;

export function validateManifest(manifest) {
  assertExactFields(manifest, [
    'version',
    'batchId',
    'prefix',
    'schemas',
    'users',
    'cohorts',
    'groups',
    'relations',
    'privateChats',
    'groupChats',
    'messages',
    'receiptIds',
  ], 'manifest');
  if (manifest.version !== SCHEMA_VERSION) {
    throw new Error('unsupported manifest schema version');
  }
  assertNonEmptyString(manifest.batchId, 'batchId');
  const expectedPrefix = `loadtest-${manifest.batchId}`;
  if (manifest.prefix !== expectedPrefix) {
    throw new Error('manifest prefix does not match batch ownership');
  }

  assertExactFields(manifest.schemas, Object.keys(SCHEMAS), 'schemas');
  for (const [owner, schema] of Object.entries(SCHEMAS)) {
    if (manifest.schemas[owner] !== schema) {
      throw new Error(`schema mismatch for ${owner}`);
    }
  }

  assertArray(manifest.users, 'users');
  assertArray(manifest.cohorts, 'cohorts');
  assertArray(manifest.groups, 'groups');
  assertArray(manifest.relations, 'relations');
  assertArray(manifest.receiptIds, 'receiptIds');

  const users = validateUsers(manifest.users, expectedPrefix);
  const cohorts = validateCohorts(manifest.cohorts, users);
  const groups = validateGroups(manifest.groups, users, cohorts, expectedPrefix);
  const relations = validateRelations(manifest.relations, users);
  const chats = validateChats(manifest.privateChats, manifest.groupChats, users, groups, relations);
  const messages = validateMessages(manifest.messages, users, groups, expectedPrefix);
  validateReceipts(manifest.receiptIds, users, chats, messages);
  validateCohortOwnership(users, cohorts, groups);
  return manifest;
}

export async function publishManifest(
  filePath,
  manifest,
  { fileSystem = {}, platform = process.platform } = {},
) {
  validateManifest(manifest);
  await atomicPublish(filePath, manifest, { ...nodeFileSystem, ...fileSystem }, platform);
}

export async function readManifest(filePath, { fileSystem = {} } = {}) {
  const fs = { ...nodeFileSystem, ...fileSystem };
  let manifest;
  try {
    manifest = JSON.parse(await fs.readFile(filePath, 'utf8'));
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error('malformed manifest JSON');
    }
    throw error;
  }
  return validateManifest(manifest);
}

export async function publishCredentialManifest(
  filePath,
  manifest,
  { fileSystem = {}, platform = process.platform } = {},
) {
  validateCredentialManifest(manifest);
  await atomicPublish(filePath, manifest, { ...nodeFileSystem, ...fileSystem }, platform);
}

async function atomicPublish(filePath, value, fs, platform) {
  assertNonEmptyString(filePath, 'filePath');
  const directory = dirname(filePath);
  const temporaryPath = join(directory, `.${basename(filePath)}.${process.pid}.${randomUUID()}.tmp`);
  let handle;
  try {
    handle = await fs.open(temporaryPath, 'wx', 0o600);
    await handle.writeFile(`${JSON.stringify(value, null, 2)}\n`);
    await handle.sync();
    await handle.close();
    handle = undefined;
    await fs.rename(temporaryPath, filePath);
    await syncDirectory(fs, directory, platform);
  } catch (error) {
    if (handle !== undefined) {
      await handle.close().catch(() => undefined);
    }
    await fs.rm(temporaryPath, { force: true }).catch(() => undefined);
    throw error;
  }
}

async function syncDirectory(fs, directory, platform) {
  let handle;
  try {
    handle = await fs.open(directory, 'r');
    await handle.sync();
  } catch (error) {
    const unsupportedOnWindows = platform === 'win32'
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

function validateUsers(values, prefix) {
  const users = new Map();
  const emails = new Set();
  for (const [index, user] of values.entries()) {
    assertExactFields(user, ['userId', 'email', 'cohortId'], `users[${index}]`);
    assertCanonicalId(user.userId, `users[${index}].userId`);
    assertNonEmptyString(user.email, `users[${index}].email`);
    assertNonEmptyString(user.cohortId, `users[${index}].cohortId`);
    if (!user.email.startsWith(`${prefix}-`)) {
      throw new Error(`user email prefix violates batch ownership at users[${index}]`);
    }
    if (users.has(user.userId)) {
      throw new Error(`duplicate userId at users[${index}]`);
    }
    if (emails.has(user.email)) {
      throw new Error(`duplicate user email at users[${index}]`);
    }
    users.set(user.userId, user);
    emails.add(user.email);
  }
  return users;
}

function validateCohorts(values, users) {
  const cohorts = new Map();
  const groupIds = new Set();
  for (const [index, cohort] of values.entries()) {
    assertExactFields(cohort, ['cohortId', 'userIds', 'groupId'], `cohorts[${index}]`);
    assertNonEmptyString(cohort.cohortId, `cohorts[${index}].cohortId`);
    assertCanonicalId(cohort.groupId, `cohorts[${index}].groupId`);
    assertOwnedIdArray(cohort.userIds, users, `cohorts[${index}].userIds`);
    if (cohorts.has(cohort.cohortId)) {
      throw new Error(`duplicate cohortId at cohorts[${index}]`);
    }
    if (groupIds.has(cohort.groupId)) {
      throw new Error(`groupId is claimed by multiple cohorts at cohorts[${index}]`);
    }
    cohorts.set(cohort.cohortId, cohort);
    groupIds.add(cohort.groupId);
  }
  return cohorts;
}

function validateGroups(values, users, cohorts, prefix) {
  const groups = new Map();
  for (const [index, group] of values.entries()) {
    assertExactFields(group, ['groupId', 'name', 'ownerUserId', 'memberUserIds'], `groups[${index}]`);
    assertCanonicalId(group.groupId, `groups[${index}].groupId`);
    assertNonEmptyString(group.name, `groups[${index}].name`);
    assertCanonicalId(group.ownerUserId, `groups[${index}].ownerUserId`);
    if (!group.name.startsWith(`${prefix}-`)) {
      throw new Error(`group name prefix violates batch ownership at groups[${index}]`);
    }
    if (!users.has(group.ownerUserId)) {
      throw new Error(`group owner is not an owned user at groups[${index}]`);
    }
    assertOwnedIdArray(group.memberUserIds, users, `groups[${index}].memberUserIds`);
    if (!group.memberUserIds.includes(group.ownerUserId)) {
      throw new Error(`group members must include owner at groups[${index}]`);
    }
    if (groups.has(group.groupId)) {
      throw new Error(`duplicate groupId at groups[${index}]`);
    }
    const cohort = [...cohorts.values()].find((candidate) => candidate.groupId === group.groupId);
    if (cohort === undefined || !sameSet(cohort.userIds, group.memberUserIds)) {
      throw new Error(`group members must match its cohort at groups[${index}]`);
    }
    groups.set(group.groupId, group);
  }
  return groups;
}

function validateRelations(values, users) {
  const relations = new Set();
  for (const [index, relation] of values.entries()) {
    assertExactFields(relation, ['userId', 'friendUserId'], `relations[${index}]`);
    assertCanonicalId(relation.userId, `relations[${index}].userId`);
    assertCanonicalId(relation.friendUserId, `relations[${index}].friendUserId`);
    if (!users.has(relation.userId) || !users.has(relation.friendUserId)) {
      throw new Error(`relation must reference owned users at relations[${index}]`);
    }
    const key = `${relation.userId}:${relation.friendUserId}`;
    if (relations.has(key)) {
      throw new Error(`duplicate relation at relations[${index}]`);
    }
    relations.add(key);
  }
  return relations;
}

function validateChats(privateChats, groupChats, users, groups, relations) {
  assertArray(privateChats, 'privateChats');
  assertArray(groupChats, 'groupChats');
  const chats = new Map();
  const views = new Set();
  for (const chat of privateChats) {
    assertExactFields(chat, ['chatId', 'userId', 'peerUserId'], 'private chat');
    for (const field of ['chatId', 'userId', 'peerUserId']) {
      assertCanonicalId(chat[field], `private chat ${field}`);
    }
    if (!users.has(chat.userId) || !users.has(chat.peerUserId)) {
      throw new Error('private chat must reference owned users');
    }
    if (chat.userId === chat.peerUserId || !relations.has(`${chat.userId}:${chat.peerUserId}`)) {
      throw new Error('private chat requires an owned friend relation');
    }
    addChat(chats, views, chat, 'private', `${chat.userId}:${chat.peerUserId}`);
  }
  for (const chat of groupChats) {
    assertExactFields(chat, ['chatId', 'groupId', 'userId'], 'group chat');
    for (const field of ['chatId', 'groupId', 'userId']) {
      assertCanonicalId(chat[field], `group chat ${field}`);
    }
    if (!users.has(chat.userId) || !groups.has(chat.groupId)) {
      throw new Error('group chat must reference an owned group and user');
    }
    if (!groups.get(chat.groupId).memberUserIds.includes(chat.userId)) {
      throw new Error('group chat user must be an owned group member');
    }
    addChat(chats, views, chat, 'group', `${chat.groupId}:${chat.userId}`);
  }
  for (const relation of relations) {
    if (!views.has(`private:${relation}`)) {
      throw new Error('every owned relation requires its private chat user view');
    }
  }
  for (const group of groups.values()) {
    for (const userId of group.memberUserIds) {
      if (!views.has(`group:${group.groupId}:${userId}`)) {
        throw new Error('every owned group member requires a group chat view');
      }
    }
  }
  return chats;
}

function addChat(chats, views, chat, type, viewId) {
  if (chats.has(chat.chatId)) {
    throw new Error('duplicate chatId');
  }
  if (views.has(`${type}:${viewId}`)) {
    throw new Error(`duplicate ${type} chat view`);
  }
  chats.set(chat.chatId, { ...chat, type });
  views.add(`${type}:${viewId}`);
}

function validateMessages(values, users, groups, prefix) {
  assertArray(values, 'messages');
  const messages = new Map();
  const tokens = new Set();
  for (const message of values) {
    if (!isPlainObject(message) || !['private', 'group'].includes(message.type)) {
      throw new Error('unknown message type');
    }
    assertExactFields(message, [
      'messageId', 'type', 'senderUserId', 'correlationToken',
      ...(message.type === 'group' ? ['groupId'] : []),
    ], 'message');
    assertCanonicalId(message.messageId, 'messageId');
    assertCanonicalId(message.senderUserId, 'message senderUserId');
    if (!users.has(message.senderUserId)) {
      throw new Error('message sender must be owned');
    }
    if (message.type === 'group') {
      assertCanonicalId(message.groupId, 'message groupId');
      if (!groups.get(message.groupId)?.memberUserIds.includes(message.senderUserId)) {
        throw new Error('message sender must belong to its owned group');
      }
    }
    const ownedSystemToken = message.type === 'group'
      && message.correlationToken === `system:group_created:${message.groupId}`
      && groups.get(message.groupId).ownerUserId === message.senderUserId;
    if (typeof message.correlationToken !== 'string'
        || (!message.correlationToken.startsWith(`${prefix}:`) && !ownedSystemToken)) {
      throw new Error('message correlation token must belong to batch prefix');
    }
    if (messages.has(message.messageId)) {
      throw new Error('duplicate messageId');
    }
    if (tokens.has(message.correlationToken)) {
      throw new Error('duplicate message correlation token');
    }
    messages.set(message.messageId, message);
    tokens.add(message.correlationToken);
  }
  return messages;
}

function validateReceipts(values, users, chats, messages) {
  const receipts = new Set();
  for (const [index, receiptId] of values.entries()) {
    if (typeof receiptId !== 'string') {
      throw new Error(`receiptId must be a string at receiptIds[${index}]`);
    }
    const segments = receiptId.split(':');
    if (segments.length !== 4 || !RECEIPT_TYPES.has(segments[0])) {
      throw new Error(`receiptId type is invalid at receiptIds[${index}]`);
    }
    for (let part = 1; part < segments.length; part += 1) {
      assertCanonicalId(segments[part], `receiptIds[${index}] part ${part}`);
    }
    if (!users.has(segments[1])) {
      throw new Error(`receipt receiver must be an owned user at receiptIds[${index}]`);
    }
    const type = segments[0] === 'PRIVATE_MESSAGE_SEND' ? 'private' : 'group';
    const chat = chats.get(segments[2]);
    const message = messages.get(segments[3]);
    if (chat === undefined || chat.type !== type) {
      throw new Error(`receipt requires an owned ${type} chat`);
    }
    if (chat.userId !== segments[1]) {
      throw new Error('receipt receiver must own the chat');
    }
    if (message === undefined || message.type !== type) {
      throw new Error('receipt message type must match an owned message fact');
    }
    if ((type === 'private' && chat.peerUserId !== message.senderUserId)
        || (type === 'group' && chat.groupId !== message.groupId)) {
      throw new Error('receipt chat does not match owned message facts');
    }
    if (receipts.has(receiptId)) {
      throw new Error(`duplicate receiptId at receiptIds[${index}]`);
    }
    receipts.add(receiptId);
  }
}

function validateCohortOwnership(users, cohorts, groups) {
  const seenUsers = new Set();
  for (const cohort of cohorts.values()) {
    if (!groups.has(cohort.groupId)) {
      throw new Error(`cohort group is not owned: ${cohort.cohortId}`);
    }
    for (const userId of cohort.userIds) {
      if (users.get(userId).cohortId !== cohort.cohortId) {
        throw new Error(`user cohort ownership mismatch: ${userId}`);
      }
      if (seenUsers.has(userId)) {
        throw new Error(`user belongs to multiple cohorts: ${userId}`);
      }
      seenUsers.add(userId);
    }
  }
  if (seenUsers.size !== users.size) {
    throw new Error('every owned user must belong to a cohort');
  }
}

function validateCredentialManifest(manifest) {
  assertExactFields(manifest, ['version', 'batchId', 'credentials'], 'credential manifest');
  if (manifest.version !== SCHEMA_VERSION) {
    throw new Error('unsupported credential manifest schema version');
  }
  assertNonEmptyString(manifest.batchId, 'batchId');
  assertArray(manifest.credentials, 'credentials');
  const prefix = `loadtest-${manifest.batchId}-`;
  const users = new Set();
  for (const [index, credential] of manifest.credentials.entries()) {
    assertExactFields(
      credential,
      ['userId', 'email', 'password', 'accessToken', 'refreshToken'],
      `credentials[${index}]`,
    );
    assertCanonicalId(credential.userId, `credentials[${index}].userId`);
    if (users.has(credential.userId)) {
      throw new Error(`duplicate credential userId at credentials[${index}]`);
    }
    users.add(credential.userId);
    if (typeof credential.email !== 'string' || !credential.email.startsWith(prefix)) {
      throw new Error(`credential email prefix mismatch at credentials[${index}]`);
    }
    for (const field of ['password', 'accessToken', 'refreshToken']) {
      assertNonEmptyString(credential[field], `credentials[${index}].${field}`);
    }
  }
}

function assertOwnedIdArray(values, users, name) {
  assertArray(values, name);
  if (values.length === 0) {
    throw new Error(`${name} must not be empty`);
  }
  const unique = new Set();
  for (const value of values) {
    assertCanonicalId(value, name);
    if (!users.has(value)) {
      throw new Error(`${name} references an unknown user`);
    }
    if (unique.has(value)) {
      throw new Error(`${name} contains a duplicate user`);
    }
    unique.add(value);
  }
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

function assertCanonicalId(value, name) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new Error(`${name} must be a canonical positive decimal string`);
  }
  if (BigInt(value) > MAX_LONG) {
    throw new Error(`${name} exceeds signed 64-bit range`);
  }
}

function assertArray(value, name) {
  if (!Array.isArray(value)) {
    throw new Error(`${name} must be an array`);
  }
}

function assertNonEmptyString(value, name) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${name} must be a non-empty string`);
  }
}

function sameSet(left, right) {
  return left.length === right.length && left.every((value) => right.includes(value));
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
