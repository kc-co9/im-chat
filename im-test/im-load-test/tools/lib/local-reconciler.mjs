const EXPECTED_SCHEMAS = Object.freeze({
  account: 'im_chat_account',
  social: 'im_chat_social',
  message: 'im_chat_message',
});
const SHARDS = Object.freeze(Array.from({ length: 8 }, (_, index) => index));

export function createLocalReconciler({ targets, adapter, logger = () => undefined } = {}) {
  const parsedTargets = validateTargets(targets);
  const database = adapter ?? createMysqlAdapter(parsedTargets);

  return Object.freeze({
    async preflight({ cleanupMysqlTarget }) {
      logger({ operation: 'reconcile.preflight' });
      const cleanup = parseCleanupTarget(cleanupMysqlTarget);
      for (const [owner, schema] of Object.entries(EXPECTED_SCHEMAS)) {
        const target = parsedTargets[owner];
        if (loopbackHost(target.hostname) !== loopbackHost(cleanup.hostname)
            || effectivePort(target) !== effectivePort(cleanup)) {
          throw new Error(`${owner} target host/port does not match cleanup.mysql`);
        }
        let rows;
        try {
          rows = await database.query(owner, 'SELECT DATABASE() AS `schemaName`', []);
        } catch (error) {
          throw new Error(`${owner} preflight failed: ${error.message}`);
        }
        if (rows.length !== 1 || rows[0].schemaName !== schema) {
          throw new Error(`${owner} preflight schema mismatch`);
        }
      }
    },

    async signUp({ email, username }) {
      logger({ operation: 'reconcile.signUp' });
      const rows = await database.query('account', [
        'SELECT CAST(`user_id` AS CHAR) AS `userId`, `username`, `status`',
        'FROM `db_user`',
        'WHERE `email` = ? AND `is_deleted` = 0',
        'LIMIT 2',
      ].join(' '), [email]);
      if (rows.length === 0) return notCommitted();
      if (rows.length > 1) throw new Error('multiple duplicate users match signup ownership');
      if (rows[0].username !== username || rows[0].status !== 1) {
        throw new Error('signup email conflicts with username or user status');
      }
      return committed({ userId: canonical(rows[0].userId, 'userId') });
    },

    async addFriend({ userId, friendUserId }) {
      logger({ operation: 'reconcile.addFriend' });
      canonical(userId, 'userId');
      canonical(friendUserId, 'friendUserId');
      const friendRows = await database.query('social', [
        'SELECT CAST(`user_id` AS CHAR) AS `userId`, CAST(`friend_user_id` AS CHAR) AS `friendUserId`,',
        '`friend_status` AS `friendStatus`',
        'FROM `db_friend`',
        'WHERE `is_deleted` = 0',
        'AND ((`user_id` = ? AND `friend_user_id` = ?) OR (`user_id` = ? AND `friend_user_id` = ?))',
      ].join(' '), [userId, friendUserId, friendUserId, userId]);
      if (friendRows.length === 0) return notCommitted('local-db-social');
      const expected = new Set([`${userId}:${friendUserId}`, `${friendUserId}:${userId}`]);
      const friendKeys = friendRows.map((row) => `${canonical(row.userId)}:${canonical(row.friendUserId)}`);
      if (friendRows.length !== 2 || !sameSet(new Set(friendKeys), expected)
          || friendRows.some(({ friendStatus }) => friendStatus !== 1)) {
        throw new Error('friend relationship is partial, duplicate, or contradictory');
      }
      return {
        outcome: 'committed',
        result: { userId, friendUserId },
        evidence: { source: 'local-db-social' },
      };
    },

    async privateChatArtifacts({ userId, friendUserId }) {
      logger({ operation: 'reconcile.privateChatArtifacts' });
      canonical(userId, 'userId');
      canonical(friendUserId, 'friendUserId');
      const chatRows = await database.query('message', unionQuery('db_im_private_chat', [
        'CAST(`chat_id` AS CHAR) AS `chatId`',
        'CAST(`user_id` AS CHAR) AS `userId`',
        'CAST(`peer_user_id` AS CHAR) AS `peerUserId`',
      ], '`status` IN (1, 2) AND `is_deleted` = 0 AND ((`user_id` = ? AND `peer_user_id` = ?) OR (`user_id` = ? AND `peer_user_id` = ?))'),
      repeatParameters([userId, friendUserId, friendUserId, userId]));
      const expected = new Set([`${userId}:${friendUserId}`, `${friendUserId}:${userId}`]);
      const privateChats = chatRows.map((row) => ({
        chatId: canonical(row.chatId),
        userId: canonical(row.userId),
        peerUserId: canonical(row.peerUserId),
      }));
      const chatKeys = privateChats.map((row) => `${row.userId}:${row.peerUserId}`);
      if (new Set(chatKeys).size !== chatKeys.length
          || new Set(privateChats.map(({ chatId }) => chatId)).size !== privateChats.length
          || chatKeys.some((key) => !expected.has(key))) {
        throw new Error('private chat artifacts are duplicate or contradictory');
      }
      if (privateChats.length !== 2) return pendingPrivateChats();
      return {
        outcome: 'converged',
        result: { privateChats },
        evidence: { source: 'local-db-message' },
      };
    },

    async createGroup({ batchId, groupName, ownerId, memberIds }) {
      logger({ operation: 'reconcile.createGroup' });
      if (groupName !== `loadtest-${batchId}-${groupName.slice(`loadtest-${batchId}-`.length)}`
          || !groupName.startsWith(`loadtest-${batchId}-`)) {
        throw new Error('groupName must use the exact batch prefix');
      }
      const expectedUsers = [ownerId, ...memberIds].map((id) => canonical(id));
      const groups = await database.query('social', [
        'SELECT CAST(`group_id` AS CHAR) AS `groupId`, CAST(`owner_id` AS CHAR) AS `ownerId`,',
        '`name` AS `groupName`, `status`',
        'FROM `db_im_group`',
        'WHERE `name` = ? AND `is_deleted` = 0',
        'LIMIT 2',
      ].join(' '), [groupName]);
      if (groups.length === 0) return notCommitted('local-db-social');
      if (groups.length > 1) throw new Error('multiple groups match the unique batch group name');
      if (canonical(groups[0].ownerId, 'ownerId') !== ownerId || groups[0].status !== 1) {
        throw new Error('group name conflicts with owner or group status');
      }
      const groupId = canonical(groups[0].groupId, 'groupId');
      const members = await database.query('social', [
        'SELECT CAST(`user_id` AS CHAR) AS `userId` FROM `db_im_group_member`',
        'WHERE `group_id` = ? AND `is_deleted` = 0',
      ].join(' '), [groupId]);
      const actualUsers = members.map((row) => canonical(row.userId));
      if (actualUsers.length !== expectedUsers.length
          || !sameSet(new Set(actualUsers), new Set(expectedUsers))) {
        throw new Error('group members do not exactly match the intended cohort');
      }
      return {
        outcome: 'committed',
        result: { groupId },
        evidence: { source: 'local-db-social' },
      };
    },

    async groupArtifacts({ groupId, ownerId, memberIds }) {
      logger({ operation: 'reconcile.groupArtifacts' });
      canonical(groupId, 'groupId');
      canonical(ownerId, 'ownerId');
      const expectedUsers = [ownerId, ...memberIds].map((id) => canonical(id));
      const chatRows = await database.query('message', unionQuery('db_im_group_chat', [
        'CAST(`chat_id` AS CHAR) AS `chatId`',
        'CAST(`group_id` AS CHAR) AS `groupId`',
        'CAST(`user_id` AS CHAR) AS `userId`',
      ], '`group_id` = ? AND `status` = 1 AND `is_deleted` = 0'), repeatParameters([groupId]));
      const chats = chatRows.map((row) => ({
        chatId: canonical(row.chatId), groupId: canonical(row.groupId), userId: canonical(row.userId),
      }));
      assertNoUnexpectedOrDuplicateUsers(chats, expectedUsers, 'group chat');
      if (chats.length !== expectedUsers.length) return pendingArtifacts();
      const systemToken = `system:group_created:${groupId}`;
      const messageRows = await database.query('message', unionQuery('db_im_group_inbox_message', [
        'CAST(`message_id` AS CHAR) AS `messageId`',
        'CAST(`group_id` AS CHAR) AS `groupId`',
        'CAST(`chat_id` AS CHAR) AS `chatId`',
        'CAST(`user_id` AS CHAR) AS `userId`',
        'CAST(`sender_id` AS CHAR) AS `senderId`',
        '`token` AS `token`',
      ], '`group_id` = ? AND `token` = ? AND `type` = 7 AND `is_deleted` = 0'),
      repeatParameters([groupId, systemToken]));
      const messages = messageRows.map((row) => ({
        messageId: canonical(row.messageId),
        groupId: canonical(row.groupId),
        chatId: canonical(row.chatId),
        userId: canonical(row.userId),
        senderId: canonical(row.senderId),
        token: row.token,
      }));
      assertNoUnexpectedOrDuplicateUsers(messages, expectedUsers, 'group system message');
      if (messages.length !== expectedUsers.length) return pendingArtifacts();
      const messageIds = new Set(messageRows.map((row) => canonical(row.messageId)));
      if (messageIds.size !== 1 || messages.some((row) => (
        row.senderId !== ownerId
        || row.groupId !== groupId
        || row.token !== systemToken
        || chats.find((chat) => chat.userId === row.userId)?.chatId !== row.chatId
      ))) throw new Error('group system message facts are contradictory');
      const messageId = [...messageIds][0];
      const ownerChat = chats.find(({ userId }) => userId === ownerId);
      const memberChats = memberIds.map((userId) => {
        const chat = chats.find((candidate) => candidate.userId === userId);
        return { userId, chatId: chat.chatId };
      });
      return {
        outcome: 'converged',
        result: { ownerChatId: ownerChat.chatId, memberChats },
        evidence: {
          source: 'local-db-message',
          messageId,
          receiptIds: memberChats.map(({ userId, chatId }) => `GROUP_MESSAGE_SEND:${userId}:${chatId}:${messageId}`),
        },
      };
    },

    close: () => database.close(),
  });
}

function parseCleanupTarget(raw) {
  let target;
  try {
    target = new URL(raw);
  } catch {
    throw new Error('cleanup.mysql must be a valid URL');
  }
  if (target.protocol !== 'mysql:' || target.search !== '' || target.hash !== ''
      || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)) {
    throw new Error('cleanup.mysql must be a query-free local mysql target');
  }
  return target;
}

function validateTargets(targets) {
  if (targets === null || typeof targets !== 'object') throw new Error('database targets are required');
  return Object.fromEntries(Object.entries(EXPECTED_SCHEMAS).map(([owner, schema]) => {
    const raw = targets[owner];
    if (typeof raw !== 'string') throw new Error(`${owner} target is required`);
    let target;
    try {
      target = new URL(raw.startsWith('jdbc:') ? raw.slice(5) : raw);
    } catch {
      throw new Error(`${owner} target must be a valid mysql URL`);
    }
    if (target.protocol !== 'mysql:' || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)) {
      throw new Error(`${owner} target must be local mysql`);
    }
    if (target.search !== '' || target.hash !== '') throw new Error(`${owner} target must be query-free`);
    if (target.username === '' || target.password === '') throw new Error(`${owner} target requires credentials`);
    if (decodeURIComponent(target.pathname.slice(1)) !== schema) throw new Error(`${owner} target schema must be ${schema}`);
    return [owner, target];
  }));
}

function createMysqlAdapter(targets) {
  const pools = new Map();
  const pool = async (owner) => {
    if (!pools.has(owner)) {
      const mysql = await import('mysql2/promise');
      const target = targets[owner];
      pools.set(owner, mysql.createPool({
        host: target.hostname.replace(/^\[|\]$/g, ''),
        port: target.port === '' ? 3306 : Number(target.port),
        user: decodeURIComponent(target.username),
        password: decodeURIComponent(target.password),
        database: decodeURIComponent(target.pathname.slice(1)),
        supportBigNumbers: true,
        bigNumberStrings: true,
        connectionLimit: 2,
      }));
    }
    return pools.get(owner);
  };
  return {
    async query(owner, sql, parameters) {
      const [rows] = await (await pool(owner)).execute(sql, parameters);
      return rows;
    },
    async close() {
      await Promise.all([...pools.values()].map((value) => value.end()));
      pools.clear();
    },
  };
}

function unionQuery(prefix, fields, where) {
  if (!['db_im_private_chat', 'db_im_group_chat', 'db_im_group_inbox_message'].includes(prefix)) {
    throw new Error('table prefix is not allowlisted');
  }
  return SHARDS.map((shard) => `SELECT ${fields.join(', ')} FROM \`${prefix}_${shard}\` WHERE ${where}`).join(' UNION ALL ');
}

function repeatParameters(parameters) {
  return SHARDS.flatMap(() => parameters);
}

function committed(result, details = {}) {
  return { outcome: 'committed', result, evidence: { source: 'local-db', ...details } };
}

function notCommitted(source = 'local-db') {
  return { outcome: 'not_committed', result: null, evidence: { source } };
}

function pendingArtifacts() {
  return { outcome: 'pending', evidence: { source: 'local-db-message' } };
}

function pendingPrivateChats() {
  return { outcome: 'pending', evidence: { source: 'local-db-message' } };
}

function assertNoUnexpectedOrDuplicateUsers(rows, expectedUsers, name) {
  const users = rows.map(({ userId }) => userId);
  if (new Set(users).size !== users.length
      || users.some((userId) => !expectedUsers.includes(userId))) {
    throw new Error(`${name} users are contradictory`);
  }
}

function loopbackHost(hostname) {
  return ['localhost', '127.0.0.1', '[::1]'].includes(hostname) ? 'loopback' : hostname;
}

function effectivePort(target) {
  return target.port === '' ? '3306' : target.port;
}

function canonical(value, name = 'ID') {
  const normalized = String(value);
  if (!/^[1-9][0-9]*$/.test(normalized)) throw new Error(`${name} must be a canonical positive ID`);
  return normalized;
}

function sameSet(left, right) {
  return left.size === right.size && [...left].every((value) => right.has(value));
}
