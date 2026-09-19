import assert from 'node:assert/strict';
import { mkdtemp, open, readFile, readdir, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';

import {
  publishCredentialManifest,
  publishManifest,
  readManifest,
  validateManifest,
} from '../tools/lib/manifest.mjs';

const BATCH_ID = '20260914-t3';
const PREFIX = `loadtest-${BATCH_ID}`;

function validManifest() {
  return {
    version: 1,
    batchId: BATCH_ID,
    prefix: PREFIX,
    schemas: {
      account: 'im_chat_account',
      social: 'im_chat_social',
      message: 'im_chat_message',
    },
    users: [
      { userId: '101', email: `${PREFIX}-user-0001@example.test`, cohortId: 'cohort-0001' },
      { userId: '102', email: `${PREFIX}-user-0002@example.test`, cohortId: 'cohort-0001' },
    ],
    cohorts: [{ cohortId: 'cohort-0001', userIds: ['101', '102'], groupId: '201' }],
    groups: [{
      groupId: '201',
      name: `${PREFIX}-group-0001`,
      ownerUserId: '101',
      memberUserIds: ['101', '102'],
    }],
    relations: [
      { userId: '101', friendUserId: '102' },
      { userId: '102', friendUserId: '101' },
    ],
    privateChats: [
      { chatId: '301', userId: '101', peerUserId: '102' },
      { chatId: '302', userId: '102', peerUserId: '101' },
    ],
    groupChats: [
      { chatId: '311', groupId: '201', userId: '101' },
      { chatId: '312', groupId: '201', userId: '102' },
    ],
    messages: [
      {
        messageId: '401',
        type: 'private',
        senderUserId: '101',
        correlationToken: `${PREFIX}:private:0001`,
      },
      {
        messageId: '402',
        type: 'group',
        senderUserId: '101',
        groupId: '201',
        correlationToken: `${PREFIX}:group:0001`,
      },
    ],
    receiptIds: [
      'PRIVATE_MESSAGE_SEND:102:302:401',
      'GROUP_MESSAGE_SEND:102:312:402',
    ],
  };
}

async function withTempDir(run) {
  const directory = await mkdtemp(join(tmpdir(), 'im-load-manifest-'));
  try {
    await run(directory);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

test('validates canonical IDs and owned users, chats, messages, groups, cohorts, and relations', () => {
  assert.deepEqual(validateManifest(validManifest()), validManifest());
});

test('group creation system token belongs only to the exact owned group and owner', () => {
  const manifest = validManifest();
  manifest.messages[1].correlationToken = 'system:group_created:201';
  assert.equal(validateManifest(manifest).messages[1].correlationToken, 'system:group_created:201');
  const wrongGroup = structuredClone(manifest);
  wrongGroup.messages[1].correlationToken = 'system:group_created:999';
  assert.throws(() => validateManifest(wrongGroup), /token|prefix/i);
  const wrongOwner = structuredClone(manifest);
  wrongOwner.messages[1].senderUserId = '102';
  assert.throws(() => validateManifest(wrongOwner), /token|prefix/i);
});

test('rejects forged prefixes, schema changes, noncanonical IDs, and unknown fields', () => {
  assert.throws(() => validateManifest({ ...validManifest(), prefix: 'loadtest-someone-else' }), /prefix/i);
  assert.throws(() => validateManifest({
    ...validManifest(),
    schemas: { ...validManifest().schemas, account: 'other_schema' },
  }), /schema/i);
  const noncanonical = validManifest();
  noncanonical.users[0].userId = '0101';
  assert.throws(() => validateManifest(noncanonical), /canonical/i);
  assert.throws(() => validateManifest({ ...validManifest(), unexpected: true }), /unknown/i);
  const nestedUnknown = validManifest();
  nestedUnknown.privateChats[0].unexpected = true;
  assert.throws(() => validateManifest(nestedUnknown), /unknown/i);
});

test('rejects ownership mismatches and references outside the batch', () => {
  const badEmail = validManifest();
  badEmail.users[0].email = 'ordinary-user@example.test';
  assert.throws(() => validateManifest(badEmail), /email.*prefix|ownership/i);

  const badRelation = validManifest();
  badRelation.relations[0].friendUserId = '999';
  assert.throws(() => validateManifest(badRelation), /relation.*owned|unknown user/i);

  const badGroup = validManifest();
  badGroup.groups[0].memberUserIds = ['101'];
  assert.throws(() => validateManifest(badGroup), /cohort|member/i);

  const badPrivateChat = validManifest();
  badPrivateChat.privateChats[0].peerUserId = '999';
  assert.throws(() => validateManifest(badPrivateChat), /private chat.*owned|unknown user/i);

  const badGroupChat = validManifest();
  badGroupChat.groupChats[0].userId = '999';
  assert.throws(() => validateManifest(badGroupChat), /group chat.*owned|unknown user/i);
});

test('rejects private chats without owned relations and group chats outside owned membership', () => {
  const unrelatedPrivate = validManifest();
  unrelatedPrivate.relations = [{ userId: '101', friendUserId: '102' }];
  assert.throws(() => validateManifest(unrelatedPrivate), /private chat.*relation/i);

  const nonmemberGroup = validManifest();
  nonmemberGroup.groups[0].memberUserIds = ['101'];
  nonmemberGroup.cohorts[0].userIds = ['101'];
  assert.throws(() => validateManifest(nonmemberGroup), /group chat.*member|cohort/i);
});

test('requires both private user views and every owned group member view', () => {
  const missingPrivateView = validManifest();
  missingPrivateView.privateChats.pop();
  assert.throws(() => validateManifest(missingPrivateView), /private chat.*view|relation.*chat/i);

  const missingGroupView = validManifest();
  missingGroupView.groupChats.pop();
  assert.throws(() => validateManifest(missingGroupView), /group chat.*member|member.*chat/i);
});

test('rejects duplicate chat and message IDs', () => {
  const duplicateChat = validManifest();
  duplicateChat.groupChats[0].chatId = '301';
  assert.throws(() => validateManifest(duplicateChat), /duplicate.*chat/i);

  const duplicateMessage = validManifest();
  duplicateMessage.messages[1].messageId = '401';
  assert.throws(() => validateManifest(duplicateMessage), /duplicate.*message/i);
});

test('rejects a groupId claimed by multiple cohorts', () => {
  const manifest = validManifest();
  manifest.cohorts.push({
    cohortId: 'cohort-0002',
    userIds: ['101', '102'],
    groupId: '201',
  });

  assert.throws(() => validateManifest(manifest), /groupId.*cohort|multiple cohorts/i);
});

test('rejects IDs above the signed 64-bit maximum', () => {
  const manifest = validManifest();
  manifest.users[0].userId = '9223372036854775808';

  assert.throws(() => validateManifest(manifest), /64-bit|range/i);
});

test('ties receipt type, receiver, chat, and message to owned manifest facts', () => {
  const outsideReceiver = validManifest();
  outsideReceiver.receiptIds[0] = 'PRIVATE_MESSAGE_SEND:999:302:401';
  assert.throws(() => validateManifest(outsideReceiver), /receipt.*receiver|owned/i);

  const wrongReceiverChat = validManifest();
  wrongReceiverChat.receiptIds[0] = 'PRIVATE_MESSAGE_SEND:101:302:401';
  assert.throws(() => validateManifest(wrongReceiverChat), /receipt.*chat|receiver/i);

  const wrongChatType = validManifest();
  wrongChatType.receiptIds[0] = 'PRIVATE_MESSAGE_SEND:102:312:401';
  assert.throws(() => validateManifest(wrongChatType), /receipt.*private|chat type/i);

  const wrongMessageType = validManifest();
  wrongMessageType.receiptIds[0] = 'PRIVATE_MESSAGE_SEND:102:302:402';
  assert.throws(() => validateManifest(wrongMessageType), /receipt.*message type|private.*message/i);

  const unknownMessage = validManifest();
  unknownMessage.receiptIds[0] = 'PRIVATE_MESSAGE_SEND:102:302:999';
  assert.throws(() => validateManifest(unknownMessage), /receipt.*message|unknown message/i);
});

test('publishes through a synced temporary file, rename, and directory sync', async () => {
  await withTempDir(async (directory) => {
    const events = [];
    const fileSystem = {
      open: async (...args) => {
        const handle = await open(...args);
        const isDirectory = args[0] === directory;
        return {
          async writeFile(value) {
            events.push('write-temp');
            return handle.writeFile(value);
          },
          async sync() {
            events.push(isDirectory ? 'sync-directory' : 'sync-temp');
            return handle.sync();
          },
          async close() {
            return handle.close();
          },
        };
      },
      async rename(source, destination) {
        events.push('rename');
        const { rename } = await import('node:fs/promises');
        return rename(source, destination);
      },
      readFile,
      readdir,
    };
    const filePath = join(directory, 'manifest.json');

    await publishManifest(filePath, validManifest(), { fileSystem });

    assert.deepEqual(events, ['write-temp', 'sync-temp', 'rename', 'sync-directory']);
    assert.deepEqual(await readManifest(filePath), validManifest());
    assert.deepEqual(await readdir(directory), ['manifest.json']);
  });
});

test('macOS publication rejects directory open or fsync failure after rename and remains readable', async () => {
  await withTempDir(async (directory) => {
    for (const failure of ['open', 'sync']) {
      const filePath = join(directory, `${failure}.json`);
      const fileSystem = {
        open: async (...args) => {
          if (args[0] === directory && failure === 'open') {
            const error = new Error('injected directory open failure');
            error.code = 'EINVAL';
            throw error;
          }
          const handle = await open(...args);
          if (args[0] !== directory || failure !== 'sync') {
            return handle;
          }
          return {
            async sync() {
              const error = new Error('injected directory sync failure');
              error.code = 'EINVAL';
              throw error;
            },
            close: () => handle.close(),
          };
        },
        async rename(source, destination) {
          const { rename } = await import('node:fs/promises');
          return rename(source, destination);
        },
        readFile,
        rm,
      };

      await assert.rejects(
        publishManifest(filePath, validManifest(), { fileSystem, platform: 'darwin' }),
        new RegExp(`directory ${failure} failure`),
      );
      assert.deepEqual(await readManifest(filePath), validManifest());
    }
  });
});

test('publishes credential manifests with mode 0600 without exposing secrets', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'credentials.json');
    const credentials = {
      version: 1,
      batchId: BATCH_ID,
      credentials: [{
        userId: '101',
        email: `${PREFIX}-user-0001@example.test`,
        password: 'not-for-logs',
        accessToken: 'access-secret',
        refreshToken: 'refresh-secret',
      }],
    };

    await publishCredentialManifest(filePath, credentials);

    assert.equal((await stat(filePath)).mode & 0o777, 0o600);
    assert.deepEqual(JSON.parse(await readFile(filePath, 'utf8')), credentials);
    assert.equal(dirname(filePath), directory);
  });
});
