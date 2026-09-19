import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createLocalReconciler } from '../tools/lib/local-reconciler.mjs';

const targets = {
  account: 'mysql://load_user:load_password@127.0.0.1:3306/im_chat_account',
  social: 'jdbc:mysql://load_user:load_password@localhost:3306/im_chat_social',
  message: 'mysql://load_user:load_password@127.0.0.1:3306/im_chat_message',
};

function fakeAdapter(results) {
  const calls = [];
  return {
    calls,
    async query(owner, sql, parameters) {
      calls.push({ owner, sql, parameters });
      return results.shift() ?? [];
    },
    async close() {},
  };
}

test('reconciles signup by exact active email and username with a parameterized SELECT', async () => {
  const adapter = fakeAdapter([[{ userId: '9007199254740993', username: 'loadtest-b-u', status: 1 }]]);
  const reconciler = createLocalReconciler({ targets, adapter });

  const result = await reconciler.signUp({ email: 'loadtest-b-u@example.test', username: 'loadtest-b-u' });

  assert.deepEqual(result, { outcome: 'committed', result: { userId: '9007199254740993' }, evidence: { source: 'local-db' } });
  assert.equal(adapter.calls.length, 1);
  assert.equal(adapter.calls[0].owner, 'account');
  assert.match(adapter.calls[0].sql, /^SELECT /);
  assert.match(adapter.calls[0].sql, /`db_user`/);
  assert.deepEqual(adapter.calls[0].parameters, ['loadtest-b-u@example.test']);
  assert.equal(adapter.calls[0].sql.includes('loadtest-b-u'), false);
});

test('fails closed when signup reconciliation finds duplicate exact users', async () => {
  const reconciler = createLocalReconciler({ targets, adapter: fakeAdapter([[
    { userId: '101' }, { userId: '102' },
  ]]) });

  await assert.rejects(
    reconciler.signUp({ email: 'loadtest-b-u@example.test', username: 'loadtest-b-u' }),
    /multiple|duplicate/i,
  );
});

test('finds signup by active email then fails closed on username or status conflict', async () => {
  for (const row of [
    { userId: '101', username: 'other-user', status: 1 },
    { userId: '101', username: 'loadtest-b-u', status: 2 },
  ]) {
    const adapter = fakeAdapter([[row]]);
    const reconciler = createLocalReconciler({ targets, adapter });
    await assert.rejects(
      reconciler.signUp({ email: 'loadtest-b-u@example.test', username: 'loadtest-b-u' }),
      /username|status|conflict/i,
    );
    assert.deepEqual(adapter.calls[0].parameters, ['loadtest-b-u@example.test']);
    assert.equal(/`username`\s*=\s*\?/.test(adapter.calls[0].sql), false);
    assert.equal(/`status`\s*=\s*1/.test(adapter.calls[0].sql), false);
  }
});

test('preflights all owner connections, exact schemas, and cleanup host before mutations', async () => {
  const adapter = fakeAdapter([
    [{ schemaName: 'im_chat_account' }],
    [{ schemaName: 'im_chat_social' }],
    [{ schemaName: 'im_chat_message' }],
  ]);
  const reconciler = createLocalReconciler({ targets, adapter });

  await reconciler.preflight({ cleanupMysqlTarget: 'mysql://127.0.0.1:3306' });

  assert.deepEqual(adapter.calls.map(({ owner, sql, parameters }) => ({ owner, sql, parameters })), [
    { owner: 'account', sql: 'SELECT DATABASE() AS `schemaName`', parameters: [] },
    { owner: 'social', sql: 'SELECT DATABASE() AS `schemaName`', parameters: [] },
    { owner: 'message', sql: 'SELECT DATABASE() AS `schemaName`', parameters: [] },
  ]);
});

test('preflight fails closed for each unavailable or mismatched owner', async () => {
  for (const failedOwner of ['account', 'social', 'message']) {
    const adapter = {
      calls: [],
      async query(owner) {
        this.calls.push(owner);
        if (owner === failedOwner) throw new Error(`${owner} unavailable`);
        return [{ schemaName: `im_chat_${owner}` }];
      },
      async close() {},
    };
    const reconciler = createLocalReconciler({ targets, adapter });
    await assert.rejects(
      reconciler.preflight({ cleanupMysqlTarget: 'mysql://127.0.0.1:3306' }),
      new RegExp(`${failedOwner}.*preflight|${failedOwner} unavailable`, 'i'),
    );
  }
  const wrongSchema = fakeAdapter([[{ schemaName: 'im_chat_social' }]]);
  await assert.rejects(
    createLocalReconciler({ targets, adapter: wrongSchema })
      .preflight({ cleanupMysqlTarget: 'mysql://127.0.0.1:3306' }),
    /account.*schema/i,
  );
  await assert.rejects(
    createLocalReconciler({ targets, adapter: fakeAdapter([]) })
      .preflight({ cleanupMysqlTarget: 'mysql://127.0.0.1:3307' }),
    /host|port|cleanup/i,
  );
});

test('commits friendship from exactly two active Social rows before Message convergence', async () => {
  const adapter = fakeAdapter([[
    { userId: '101', friendUserId: '102', friendStatus: 1 },
    { userId: '102', friendUserId: '101', friendStatus: 1 },
  ]]);
  const reconciler = createLocalReconciler({ targets, adapter });

  const result = await reconciler.addFriend({ userId: '101', friendUserId: '102' });

  assert.deepEqual(result, {
    outcome: 'committed',
    result: { userId: '101', friendUserId: '102' },
    evidence: { source: 'local-db-social' },
  });
  assert.deepEqual(adapter.calls.map(({ owner }) => owner), ['social']);
  assert.equal(adapter.calls.every(({ sql }) => /^SELECT /.test(sql) && !/\b(?:INSERT|UPDATE|DELETE)\b/i.test(sql)), true);
});

test('reports zero friend rows as not committed and fails closed on partial or duplicate rows', async () => {
  assert.deepEqual(await createLocalReconciler({ targets, adapter: fakeAdapter([[]]) })
    .addFriend({ userId: '101', friendUserId: '102' }), {
    outcome: 'not_committed', result: null, evidence: { source: 'local-db-social' },
  });
  for (const rows of [
    [{ userId: '101', friendUserId: '102', friendStatus: 1 }],
    [
      { userId: '101', friendUserId: '102', friendStatus: 1 },
      { userId: '102', friendUserId: '101', friendStatus: 1 },
      { userId: '101', friendUserId: '102', friendStatus: 1 },
    ],
    [
      { userId: '101', friendUserId: '102', friendStatus: 2 },
      { userId: '102', friendUserId: '101', friendStatus: 1 },
    ],
  ]) {
    const adapter = fakeAdapter([rows]);
    const reconciler = createLocalReconciler({ targets, adapter });
    await assert.rejects(
      reconciler.addFriend({ userId: '101', friendUserId: '102' }),
      /partial|duplicate|contradictory/i,
    );
    assert.equal(/`friend_status`\s*=\s*1/.test(adapter.calls[0].sql), false);
  }
});

test('derives private chat views in a separate Message artifact phase', async () => {
  const adapter = fakeAdapter([[
    { chatId: '201', userId: '101', peerUserId: '102' },
    { chatId: '202', userId: '102', peerUserId: '101' },
  ]]);
  const reconciler = createLocalReconciler({ targets, adapter });

  assert.deepEqual(await reconciler.privateChatArtifacts({ userId: '101', friendUserId: '102' }), {
    outcome: 'converged',
    result: {
      privateChats: [
        { chatId: '201', userId: '101', peerUserId: '102' },
        { chatId: '202', userId: '102', peerUserId: '101' },
      ],
    },
    evidence: { source: 'local-db-message' },
  });
  assert.equal(adapter.calls[0].sql.match(/db_im_private_chat_[0-7]/g).length, 8);
  assert.match(adapter.calls[0].sql, /`status`\s+IN\s+\(1,\s*2\)/);
  assert.match(adapter.calls[0].sql, /`is_deleted`\s*=\s*0/);
  assert.deepEqual(await createLocalReconciler({ targets, adapter: fakeAdapter([[]]) })
    .privateChatArtifacts({ userId: '101', friendUserId: '102' }), {
    outcome: 'pending', evidence: { source: 'local-db-message' },
  });
});

test('commits group creation from one exact Social group and membership before Message convergence', async () => {
  const adapter = fakeAdapter([
    [{ groupId: '301', ownerId: '101', groupName: 'loadtest-b-group', status: 1 }],
    [{ userId: '101' }, { userId: '102' }],
  ]);
  const reconciler = createLocalReconciler({ targets, adapter });

  const result = await reconciler.createGroup({
    batchId: 'b', groupName: 'loadtest-b-group', ownerId: '101', memberIds: ['102'],
  });

  assert.deepEqual(result, {
    outcome: 'committed', result: { groupId: '301' }, evidence: { source: 'local-db-social' },
  });
  assert.deepEqual(adapter.calls.map(({ owner }) => owner), ['social', 'social']);
});

test('derives all group chats and system group-created receipts in a separate Message phase', async () => {
  const adapter = fakeAdapter([
    [{ chatId: '401', groupId: '301', userId: '101' }, { chatId: '402', groupId: '301', userId: '102' }],
    [
      { messageId: '501', groupId: '301', chatId: '401', userId: '101', senderId: '101', token: 'system:group_created:301' },
      { messageId: '501', groupId: '301', chatId: '402', userId: '102', senderId: '101', token: 'system:group_created:301' },
    ],
  ]);
  const reconciler = createLocalReconciler({ targets, adapter });

  const result = await reconciler.groupArtifacts({
    groupId: '301', ownerId: '101', memberIds: ['102'],
  });

  assert.deepEqual(result, {
    outcome: 'converged',
    result: { ownerChatId: '401', memberChats: [{ userId: '102', chatId: '402' }] },
    evidence: {
      source: 'local-db-message',
      messageId: '501',
      receiptIds: ['GROUP_MESSAGE_SEND:102:402:501'],
    },
  });
  assert.deepEqual(adapter.calls.map(({ owner }) => owner), ['message', 'message']);
  assert.equal(adapter.calls[0].sql.match(/db_im_group_chat_[0-7]/g).length, 8);
  assert.equal(adapter.calls[1].sql.match(/db_im_group_inbox_message_[0-7]/g).length, 8);
});

test('fails closed for duplicate groups or wrong members but reports absent groups as not committed', async () => {
  const absent = createLocalReconciler({ targets, adapter: fakeAdapter([[]]) });
  assert.deepEqual(await absent.createGroup({
    batchId: 'b', groupName: 'loadtest-b-group', ownerId: '101', memberIds: ['102'],
  }), { outcome: 'not_committed', result: null, evidence: { source: 'local-db-social' } });

  const duplicate = createLocalReconciler({ targets, adapter: fakeAdapter([[
    { groupId: '301', ownerId: '101', groupName: 'loadtest-b-group', status: 1 },
    { groupId: '302', ownerId: '101', groupName: 'loadtest-b-group', status: 1 },
  ]]) });
  await assert.rejects(duplicate.createGroup({
    batchId: 'b', groupName: 'loadtest-b-group', ownerId: '101', memberIds: ['102'],
  }), /multiple|duplicate/i);

  const wrongMembers = createLocalReconciler({ targets, adapter: fakeAdapter([
    [{ groupId: '301', ownerId: '101', groupName: 'loadtest-b-group', status: 1 }],
    [{ userId: '101' }],
  ]) });
  await assert.rejects(wrongMembers.createGroup({
    batchId: 'b', groupName: 'loadtest-b-group', ownerId: '101', memberIds: ['102'],
  }), /member/i);

  for (const group of [
    { groupId: '301', ownerId: '999', groupName: 'loadtest-b-group', status: 1 },
    { groupId: '301', ownerId: '101', groupName: 'loadtest-b-group', status: 2 },
  ]) {
    const adapter = fakeAdapter([[group]]);
    await assert.rejects(createLocalReconciler({ targets, adapter }).createGroup({
      batchId: 'b', groupName: 'loadtest-b-group', ownerId: '101', memberIds: ['102'],
    }), /owner|status|conflict/i);
    assert.deepEqual(adapter.calls[0].parameters, ['loadtest-b-group']);
    assert.equal(/`owner_id`\s*=\s*\?/.test(adapter.calls[0].sql), false);
    assert.equal(/`status`\s*=\s*1/.test(adapter.calls[0].sql), false);
  }
});

test('reports incomplete Message artifacts as pending without changing group commitment', async () => {
  const reconciler = createLocalReconciler({ targets, adapter: fakeAdapter([
    [{ chatId: '401', groupId: '301', userId: '101' }],
    [],
  ]) });
  assert.deepEqual(await reconciler.groupArtifacts({
    groupId: '301', ownerId: '101', memberIds: ['102'],
  }), { outcome: 'pending', evidence: { source: 'local-db-message' } });
});

test('rejects remote, queried, credential-free, or wrong-schema targets before querying', () => {
  const adapter = fakeAdapter([]);
  for (const account of [
    'mysql://u:p@example.com:3306/im_chat_account',
    'mysql://u:p@127.0.0.1:3306/im_chat_account?ssl=true',
    'mysql://127.0.0.1:3306/im_chat_account',
    'mysql://u:p@127.0.0.1:3306/im_chat_social',
  ]) {
    assert.throws(() => createLocalReconciler({ targets: { ...targets, account }, adapter }), /target|credential|schema|local/i);
  }
  assert.equal(adapter.calls.length, 0);
});

test('logger receives stable operation names without database credentials', async () => {
  const adapter = fakeAdapter([[]]);
  const logs = [];
  const reconciler = createLocalReconciler({ targets, adapter, logger: (entry) => logs.push(entry) });

  await reconciler.signUp({ email: 'loadtest-b-u@example.test', username: 'loadtest-b-u' });

  assert.deepEqual(logs, [{ operation: 'reconcile.signUp' }]);
  assert.equal(JSON.stringify(logs).includes('load_password'), false);
});
