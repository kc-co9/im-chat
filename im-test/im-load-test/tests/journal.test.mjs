import assert from 'node:assert/strict';
import { mkdtemp, open, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { createJournal, recoverJournal, repairTruncatedTail } from '../tools/lib/journal.mjs';

const BATCH_ID = '20260914-t3';

async function withTempDir(run) {
  const directory = await mkdtemp(join(tmpdir(), 'im-load-journal-'));
  try {
    await run(directory);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

function signupIntent(operationId = 'signup-0001') {
  return {
    operationId,
    operation: 'signUp',
    ownership: {
      email: `loadtest-${BATCH_ID}-${operationId}@example.test`,
      username: `loadtest-${BATCH_ID}-${operationId}`,
    },
  };
}

async function completeSignup(journal, operationId, userId) {
  await journal.appendIntent(signupIntent(operationId));
  await journal.appendInvoked({ operationId, attempt: 1 });
  await journal.appendReconciled({
    operationId,
    attempt: 1,
    outcome: 'committed',
    result: { userId },
    evidence: { source: 'user-detail' },
  });
  await journal.appendCompleted({ operationId, result: { userId } });
}

test('append writes and fsyncs a restrictive intent record before returning', async () => {
  await withTempDir(async (directory) => {
    const events = [];
    const journal = createJournal({
      filePath: join(directory, 'journal.jsonl'),
      batchId: BATCH_ID,
      fileSystem: {
        open: async (...args) => {
          const isDirectory = args[0] === directory;
          if (!isDirectory) {
            assert.equal(args[1], 'a');
            assert.equal(args[2], 0o600);
          }
          const handle = await open(...args);
          return {
            async writeFile(value) {
              events.push('write');
              return handle.writeFile(value);
            },
            async sync() {
              events.push(isDirectory ? 'sync-directory' : 'sync-file');
              return handle.sync();
            },
            async close() {
              events.push(isDirectory ? 'close-directory' : 'close-file');
              return handle.close();
            },
          };
        },
        readFile,
      },
    });

    await journal.appendIntent(signupIntent());

    assert.deepEqual(events, ['write', 'sync-file', 'close-file', 'sync-directory', 'close-directory']);
    assert.deepEqual(JSON.parse(await readFile(join(directory, 'journal.jsonl'), 'utf8')), {
      schemaVersion: 1,
      batchId: BATCH_ID,
      operationId: 'signup-0001',
      type: 'intent',
      operation: 'signUp',
      ownership: {
        email: `loadtest-${BATCH_ID}-signup-0001@example.test`,
        username: `loadtest-${BATCH_ID}-signup-0001`,
      },
    });
    assert.equal((await stat(join(directory, 'journal.jsonl'))).mode & 0o777, 0o600);
  });
});

test('append propagates parent directory fsync failures on macOS and Linux', async () => {
  if (!['darwin', 'linux'].includes(process.platform)) {
    return;
  }
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({
      filePath,
      batchId: BATCH_ID,
      fileSystem: {
        open: async (...args) => {
          const handle = await open(...args);
          if (args[0] !== directory) {
            return handle;
          }
          return {
            async sync() {
              const error = new Error('directory fsync unavailable');
              error.code = 'EINVAL';
              throw error;
            },
            async close() {
              return handle.close();
            },
          };
        },
        readFile,
      },
    });

    await assert.rejects(journal.appendIntent(signupIntent()), /directory fsync unavailable/);
  });
});

test('same journal reloads a fully written record after file fsync failure', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    let failSync = true;
    let fileSyncs = 0;
    const journal = createJournal({
      filePath,
      batchId: BATCH_ID,
      fileSystem: {
        readFile,
        open: async (...args) => {
          const handle = await open(...args);
          if (args[0] === directory) {
            return handle;
          }
          return {
            chmod: (...values) => handle.chmod(...values),
            writeFile: (...values) => handle.writeFile(...values),
            async sync() {
              fileSyncs += 1;
              if (failSync) {
                failSync = false;
                throw new Error('injected file sync failure');
              }
              return handle.sync();
            },
            close: () => handle.close(),
          };
        },
      },
    });

    await assert.rejects(journal.appendIntent(signupIntent()), /injected file sync failure/);
    await journal.appendIntent(signupIntent());
    assert.equal(fileSyncs, 2);
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });

    const records = (await readFile(filePath, 'utf8')).trim().split('\n').map(JSON.parse);
    assert.deepEqual(records.map(({ type }) => type), ['intent', 'invoked']);
  });
});

test('same journal reloads a persisted record after directory fsync failure', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    let failDirectorySync = true;
    let directorySyncs = 0;
    const journal = createJournal({
      filePath,
      batchId: BATCH_ID,
      fileSystem: {
        readFile,
        open: async (...args) => {
          const handle = await open(...args);
          if (args[0] !== directory) {
            return handle;
          }
          return {
            async sync() {
              directorySyncs += 1;
              if (failDirectorySync) {
                failDirectorySync = false;
                throw new Error('injected directory sync failure');
              }
              return handle.sync();
            },
            close: () => handle.close(),
          };
        },
      },
    });

    await assert.rejects(journal.appendIntent(signupIntent()), /injected directory sync failure/);
    await journal.appendIntent(signupIntent());
    assert.equal(directorySyncs, 2);
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });

    const records = (await readFile(filePath, 'utf8')).trim().split('\n').map(JSON.parse);
    assert.deepEqual(records.map(({ type }) => type), ['intent', 'invoked']);
  });
});

test('same journal reloads a partial write and requires explicit tail repair', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    let failWrite = true;
    const journal = createJournal({
      filePath,
      batchId: BATCH_ID,
      fileSystem: {
        readFile,
        open: async (...args) => {
          const handle = await open(...args);
          if (args[0] === directory) {
            return handle;
          }
          return {
            chmod: (...values) => handle.chmod(...values),
            async writeFile(value) {
              if (failWrite) {
                failWrite = false;
                await handle.writeFile(value.slice(0, Math.floor(value.length / 2)));
                throw new Error('injected partial write failure');
              }
              return handle.writeFile(value);
            },
            sync: () => handle.sync(),
            close: () => handle.close(),
          };
        },
      },
    });

    await assert.rejects(journal.appendIntent(signupIntent()), /injected partial write failure/);
    await assert.rejects(journal.appendIntent(signupIntent()), /repair|truncated/i);
    await repairTruncatedTail({ filePath, batchId: BATCH_ID });
    await journal.appendIntent(signupIntent());
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });
    assert.equal(recovered.unresolvedIntents[0].lastType, 'invoked');
  });
});

test('recovery exposes an intent fsynced before a crash', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    await journal.appendIntent(signupIntent());

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });

    assert.deepEqual(recovered.unresolvedIntents, [{
      operationId: 'signup-0001',
      operation: 'signUp',
      ownership: {
        email: `loadtest-${BATCH_ID}-signup-0001@example.test`,
        username: `loadtest-${BATCH_ID}-signup-0001`,
      },
      lastType: 'intent',
    }]);
    assert.equal(recovered.truncatedTail, null);
  });
});

test('recovery exposes an invoked operation after its response is lost', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    await completeSignup(journal, 'signup-0001', '101');
    await completeSignup(journal, 'signup-0002', '102');
    await journal.appendIntent({
      operationId: 'group-0001',
      operation: 'createGroup',
      ownership: {
        ownerId: '101',
        memberIds: ['102'],
        groupName: `loadtest-${BATCH_ID}-group-0001`,
      },
    });
    await journal.appendInvoked({ operationId: 'group-0001', attempt: 1 });

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });

    assert.equal(recovered.unresolvedIntents.length, 1);
    assert.equal(recovered.unresolvedIntents[0].operation, 'createGroup');
    assert.equal(recovered.unresolvedIntents[0].lastType, 'invoked');
  });
});

test('recovery returns reconciled work, completed operations, and cohort checkpoints', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    await completeSignup(journal, 'signup-0001', '101');
    await completeSignup(journal, 'signup-0002', '102');
    await journal.appendIntent({
      operationId: 'friend-0001',
      operation: 'addFriend',
      ownership: { userId: '101', friendUserId: '102' },
    });
    await journal.appendInvoked({ operationId: 'friend-0001', attempt: 1 });
    await journal.appendReconciled({
      operationId: 'friend-0001',
      attempt: 1,
      outcome: 'committed',
      result: { userId: '101', friendUserId: '102' },
      evidence: { source: 'friend-list' },
    });
    await journal.appendCheckpoint({
      checkpointId: 'cohort-0001',
      completedOperationIds: ['signup-0002'],
    });

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });

    assert.deepEqual(
      recovered.unresolvedIntents.map(({ operationId, lastType }) => ({ operationId, lastType })),
      [{ operationId: 'friend-0001', lastType: 'reconciled' }],
    );
    assert.deepEqual(recovered.unresolvedIntents[0].reconciliation, {
      attempt: 1,
      outcome: 'committed',
      result: { userId: '101', friendUserId: '102' },
      evidence: { source: 'friend-list' },
    });
    assert.deepEqual(recovered.completed.find(({ operationId }) => operationId === 'signup-0002'), {
      operationId: 'signup-0002',
      operation: 'signUp',
      ownership: {
        email: `loadtest-${BATCH_ID}-signup-0002@example.test`,
        username: `loadtest-${BATCH_ID}-signup-0002`,
      },
      result: { userId: '102' },
      lastType: 'completed',
      reconciliation: {
        attempt: 1,
        outcome: 'committed',
        result: { userId: '102' },
        evidence: { source: 'user-detail' },
      },
    });
    assert.deepEqual(recovered.checkpoints.map((record) => ({
      checkpointId: record.checkpointId,
      completedOperationIds: record.completedOperationIds,
    })), [{ checkpointId: 'cohort-0001', completedOperationIds: ['signup-0002'] }]);
  });
});

test('append returns detached records whose mutation cannot corrupt subsequent transitions or recovery', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    const intent = await journal.appendIntent(signupIntent());
    intent.operation = 'createGroup';
    intent.ownership.email = 'ordinary@example.test';
    intent.ownership.username = 'ordinary';

    const invoked = await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    assert.equal(invoked.operation, 'signUp');
    const reconciled = await journal.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'committed',
      result: { userId: '101' },
      evidence: { source: 'user-detail' },
    });
    reconciled.result.userId = '999';
    reconciled.evidence.source = 'mutated';
    const completed = await journal.appendCompleted({
      operationId: 'signup-0001',
      result: { userId: '101' },
    });
    completed.result.userId = '999';
    await journal.appendIntent(signupIntent('signup-0002'));

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });
    assert.equal(recovered.completed[0].operation, 'signUp');
    assert.deepEqual(recovered.completed[0].ownership, signupIntent().ownership);
    assert.deepEqual(recovered.completed[0].result, { userId: '101' });
    assert.equal(recovered.completed[0].reconciliation.evidence.source, 'user-detail');
  });
});

test('exact duplicate transitions are idempotent and conflicting duplicates are rejected', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    const intent = signupIntent();
    await journal.appendIntent(intent);
    await journal.appendIntent({ ...intent, ownership: { ...intent.ownership } });
    await journal.appendInvoked({ operationId: intent.operationId, attempt: 1 });
    await journal.appendInvoked({ operationId: intent.operationId, attempt: 1 });

    assert.equal((await readFile(filePath, 'utf8')).trim().split('\n').length, 2);
    await assert.rejects(
      journal.appendIntent({ ...intent, operation: 'createGroup' }),
      /operationId.*conflict/i,
    );
  });
});

test('invalid transitions and checkpoints are rejected without appending', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });

    await assert.rejects(
      journal.appendCompleted({ operationId: 'signup-0001', result: { userId: '101' } }),
      /transition/i,
    );
    await journal.appendIntent(signupIntent());
    await assert.rejects(
      journal.appendReconciled({
        operationId: 'signup-0001',
        attempt: 1,
        outcome: 'committed',
        result: { userId: '101' },
        evidence: { source: 'user-detail' },
      }),
      /transition/i,
    );
    await assert.rejects(
      journal.appendCheckpoint({ checkpointId: 'cohort-0001', completedOperationIds: ['signup-0001'] }),
      /checkpoint.*completed/i,
    );
    assert.equal((await readFile(filePath, 'utf8')).trim().split('\n').length, 1);
  });
});

test('a reopened journal validates prior state before continuing transitions', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    await createJournal({ filePath, batchId: BATCH_ID }).appendIntent(signupIntent());

    const reopened = createJournal({ filePath, batchId: BATCH_ID });
    await reopened.appendInvoked({ operationId: 'signup-0001', attempt: 1 });

    assert.equal((await recoverJournal({ filePath, batchId: BATCH_ID })).unresolvedIntents[0].lastType, 'invoked');
  });
});

test('reopen restores a missing final newline before appending the next record', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    await createJournal({ filePath, batchId: BATCH_ID }).appendIntent(signupIntent());
    const completeIntent = await readFile(filePath, 'utf8');
    await writeFile(filePath, completeIntent.slice(0, -1));

    const beforeAppend = await recoverJournal({ filePath, batchId: BATCH_ID });
    assert.equal(beforeAppend.unresolvedIntents[0].lastType, 'intent');
    assert.equal(beforeAppend.truncatedTail, null);

    const reopened = createJournal({ filePath, batchId: BATCH_ID });
    await reopened.appendInvoked({ operationId: 'signup-0001', attempt: 1 });

    const lines = (await readFile(filePath, 'utf8')).trimEnd().split('\n');
    assert.equal(lines.length, 2);
    assert.deepEqual(lines.map((line) => JSON.parse(line).type), ['intent', 'invoked']);
    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });
    assert.equal(recovered.unresolvedIntents[0].lastType, 'invoked');
    assert.equal(recovered.unresolvedIntents[0].attempt, 1);
  });
});

test('repair truncates only a final crash tail, fsyncs it, and allows append after reopen', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    await createJournal({ filePath, batchId: BATCH_ID }).appendIntent(signupIntent());
    const partial = '{"schemaVersion":1,"type":"inv';
    await writeFile(filePath, partial, { flag: 'a' });

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });

    assert.equal(recovered.unresolvedIntents[0].lastType, 'intent');
    assert.deepEqual(recovered.truncatedTail, {
      lineNumber: 2,
      byteLength: Buffer.byteLength(partial),
    });
    await assert.rejects(
      createJournal({ filePath, batchId: BATCH_ID }).appendInvoked({
        operationId: 'signup-0001',
        attempt: 1,
      }),
      /repair|truncated/i,
    );

    const repaired = await repairTruncatedTail({ filePath, batchId: BATCH_ID });
    assert.deepEqual(repaired, { repaired: true, removedBytes: Buffer.byteLength(partial) });
    const reopened = createJournal({ filePath, batchId: BATCH_ID });
    await reopened.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    assert.equal((await recoverJournal({ filePath, batchId: BATCH_ID })).unresolvedIntents[0].lastType, 'invoked');
  });
});

test('repair rejects corrupt newline-complete records without modifying the journal', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    await createJournal({ filePath, batchId: BATCH_ID }).appendIntent(signupIntent());
    await writeFile(filePath, '{not-json}\n', { flag: 'a' });
    const before = await readFile(filePath, 'utf8');

    await assert.rejects(
      repairTruncatedTail({ filePath, batchId: BATCH_ID }),
      /malformed.*line 2/i,
    );
    assert.equal(await readFile(filePath, 'utf8'), before);
  });
});

test('retries only after not_committed reconciliation and preserves durable facts', async () => {
  await withTempDir(async (directory) => {
    const filePath = join(directory, 'journal.jsonl');
    const journal = createJournal({ filePath, batchId: BATCH_ID });
    await journal.appendIntent(signupIntent());
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    await journal.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'not_committed',
      result: null,
      evidence: { source: 'user-detail', found: false },
    });
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 2 });
    await journal.appendReconciled({
      operationId: 'signup-0001',
      attempt: 2,
      outcome: 'committed',
      result: { userId: '101' },
      evidence: { source: 'user-detail', found: true },
    });
    await journal.appendCompleted({ operationId: 'signup-0001', result: { userId: '101' } });

    const recovered = await recoverJournal({ filePath, batchId: BATCH_ID });
    assert.deepEqual(recovered.completed[0].reconciliation, {
      attempt: 2,
      outcome: 'committed',
      result: { userId: '101' },
      evidence: { source: 'user-detail', found: true },
    });
  });
});

test('rejects retry after committed, skipped attempts, invalid outcomes, and completion after not_committed', async () => {
  await withTempDir(async (directory) => {
    const committedPath = join(directory, 'committed.jsonl');
    const committed = createJournal({ filePath: committedPath, batchId: BATCH_ID });
    await committed.appendIntent(signupIntent());
    await committed.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    await committed.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'committed',
      result: { userId: '101' },
      evidence: { source: 'user-detail' },
    });
    await assert.rejects(
      committed.appendInvoked({ operationId: 'signup-0001', attempt: 2 }),
      /committed|transition/i,
    );

    const retryPath = join(directory, 'retry.jsonl');
    const retry = createJournal({ filePath: retryPath, batchId: BATCH_ID });
    await retry.appendIntent(signupIntent());
    await assert.rejects(retry.appendInvoked({ operationId: 'signup-0001', attempt: 2 }), /attempt/i);
    await retry.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    await assert.rejects(retry.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'unknown',
      result: null,
      evidence: {},
    }), /outcome/i);
    await retry.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'not_committed',
      result: null,
      evidence: { source: 'user-detail' },
    });
    await assert.rejects(
      retry.appendCompleted({ operationId: 'signup-0001', result: { userId: '101' } }),
      /committed|transition/i,
    );
  });
});

test('recovery rejects schema, type, field, batch, and transition corruption', async () => {
  await withTempDir(async (directory) => {
    const base = {
      schemaVersion: 1,
      batchId: BATCH_ID,
      operationId: 'signup-0001',
      type: 'intent',
      operation: 'signUp',
      ownership: {
        email: `loadtest-${BATCH_ID}-signup-0001@example.test`,
        username: `loadtest-${BATCH_ID}-signup-0001`,
      },
    };
    const cases = [
      ['schema', { ...base, schemaVersion: 2 }, /schema/i],
      ['type', { ...base, type: 'started' }, /type/i],
      ['field', { ...base, unexpected: true }, /unknown/i],
      ['batch', { ...base, batchId: 'another-batch' }, /batch/i],
    ];
    for (const [name, record, expected] of cases) {
      const filePath = join(directory, `${name}.jsonl`);
      await writeFile(filePath, `${JSON.stringify(record)}\n`);
      await assert.rejects(recoverJournal({ filePath, batchId: BATCH_ID }), expected);
    }

    const conflictPath = join(directory, 'conflict.jsonl');
    await writeFile(conflictPath, `${JSON.stringify(base)}\n${JSON.stringify({
      ...base,
      operation: 'createGroup',
      ownership: {
        ownerId: '101',
        memberIds: ['102'],
        groupName: `loadtest-${BATCH_ID}-group-0001`,
      },
    })}\n`);
    await assert.rejects(recoverJournal({ filePath: conflictPath, batchId: BATCH_ID }), /conflict/i);

    const transitionPath = join(directory, 'transition.jsonl');
    await writeFile(transitionPath, `${JSON.stringify({
      schemaVersion: 1,
      batchId: BATCH_ID,
      operationId: 'signup-0001',
      type: 'completed',
      operation: 'signUp',
      attempt: 1,
      result: { userId: '101' },
    })}\n`);
    await assert.rejects(recoverJournal({ filePath: transitionPath, batchId: BATCH_ID }), /transition/i);
  });
});

test('journal records reject credential material without leaking its value', async () => {
  await withTempDir(async (directory) => {
    const secret = 'journal-secret-value';
    const journal = createJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });

    await assert.rejects(
      journal.appendIntent({
        ...signupIntent(),
        ownership: { email: `loadtest-${BATCH_ID}-user@example.test`, accessToken: secret },
      }),
      (error) => /secret|token|credential/i.test(error.message) && !error.message.includes(secret),
    );

    await journal.appendIntent(signupIntent());
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    await assert.rejects(
      journal.appendReconciled({
        operationId: 'signup-0001',
        attempt: 1,
        outcome: 'committed',
        result: { userId: '101' },
        evidence: { accessToken: secret },
      }),
      (error) => /secret|token|credential/i.test(error.message) && !error.message.includes(secret),
    );
  });
});

test('journal accepts only bounded safe operation IDs and exact operation schemas', async () => {
  await withTempDir(async (directory) => {
    const journal = createJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });

    await assert.rejects(journal.appendIntent({ ...signupIntent(), operation: 'deleteUser' }), /operation/i);
    await assert.rejects(journal.appendIntent(signupIntent('password-reset-0001')), /operationId.*credential|safe/i);
    await assert.rejects(journal.appendIntent(signupIntent('x'.repeat(129))), /operationId.*length|bounded/i);
    await assert.rejects(journal.appendIntent({
      ...signupIntent(),
      ownership: {
        ...signupIntent().ownership,
        unexpected: true,
      },
    }), /unknown/i);
    await assert.rejects(journal.appendIntent({
      ...signupIntent(),
      ownership: {
        email: 'ordinary@example.test',
        username: `loadtest-${BATCH_ID}-signup-0001`,
      },
    }), /prefix|ownership/i);
  });
});

test('relationship and group intents require users established by completed signups', async () => {
  await withTempDir(async (directory) => {
    const journal = createJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });
    await completeSignup(journal, 'signup-0001', '101');

    await assert.rejects(journal.appendIntent({
      operationId: 'friend-0001',
      operation: 'addFriend',
      ownership: { userId: '101', friendUserId: '999' },
    }), /owned|established/i);
    await assert.rejects(journal.appendIntent({
      operationId: 'friend-0002',
      operation: 'addFriend',
      ownership: { userId: '101', friendUserId: '101' },
    }), /distinct/i);

    await completeSignup(journal, 'signup-0002', '102');
    await assert.rejects(journal.appendIntent({
      operationId: 'group-0001',
      operation: 'createGroup',
      ownership: {
        ownerId: '101',
        memberIds: ['102', '999'],
        groupName: `loadtest-${BATCH_ID}-group-0001`,
      },
    }), /owned|established/i);
    await assert.rejects(journal.appendIntent({
      operationId: 'group-0002',
      operation: 'createGroup',
      ownership: {
        ownerId: '101',
        memberIds: ['101', '102'],
        groupName: `loadtest-${BATCH_ID}-group-0002`,
      },
    }), /owner.*excluded|memberIds/i);
  });
});

test('completed results are exact, canonical, and sufficient for operation recovery', async () => {
  await withTempDir(async (directory) => {
    const journal = createJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });
    await completeSignup(journal, 'signup-0001', '101');
    await completeSignup(journal, 'signup-0002', '102');
    await journal.appendIntent({
      operationId: 'group-0001',
      operation: 'createGroup',
      ownership: {
        ownerId: '101',
        memberIds: ['102'],
        groupName: `loadtest-${BATCH_ID}-group-0001`,
      },
    });
    await journal.appendInvoked({ operationId: 'group-0001', attempt: 1 });
    const groupResult = {
      groupId: '201',
      ownerChatId: '301',
      memberChats: [{ userId: '102', chatId: '302' }],
    };
    await journal.appendReconciled({
      operationId: 'group-0001',
      attempt: 1,
      outcome: 'committed',
      result: groupResult,
      evidence: { source: 'group-detail' },
    });
    await assert.rejects(
      journal.appendCompleted({ operationId: 'group-0001', result: { ...groupResult, unexpected: true } }),
      /unknown/i,
    );
    await journal.appendCompleted({ operationId: 'group-0001', result: groupResult });

    const recovered = await recoverJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });
    assert.deepEqual(
      recovered.completed.find(({ operationId }) => operationId === 'group-0001').result,
      groupResult,
    );
  });
});

test('journal rejects signed 64-bit ID overflow', async () => {
  await withTempDir(async (directory) => {
    const journal = createJournal({ filePath: join(directory, 'journal.jsonl'), batchId: BATCH_ID });
    await journal.appendIntent(signupIntent());
    await journal.appendInvoked({ operationId: 'signup-0001', attempt: 1 });
    await assert.rejects(journal.appendReconciled({
      operationId: 'signup-0001',
      attempt: 1,
      outcome: 'committed',
      result: { userId: '9223372036854775808' },
      evidence: { source: 'user-detail' },
    }), /64-bit|range/i);
  });
});
