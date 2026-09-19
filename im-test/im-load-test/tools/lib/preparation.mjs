import { mkdir, readFile } from 'node:fs/promises';
import { dirname } from 'node:path';

import { GatewayBusinessError, createGatewayHttpClient } from './http-client.mjs';
import { createJournal, recoverJournal, repairTruncatedTail } from './journal.mjs';
import {
  publishCredentialManifest,
  publishManifest,
  readManifest,
} from './manifest.mjs';
import { openSetupWebSockets } from './setup-websocket.mjs';

const SCHEMAS = Object.freeze({
  account: 'im_chat_account',
  social: 'im_chat_social',
  message: 'im_chat_message',
});

export async function prepareBatch({
  batchId,
  resume = false,
  cohortCount = 1,
  groupSize = 10,
  config,
  paths,
  httpClient,
  localReconciler,
  websocketFactory = openSetupWebSockets,
  scheduler = { wait: (attempt, milliseconds = 500) => new Promise((resolve) => setTimeout(resolve, milliseconds)) },
  pollAttempts = 20,
  mutationAttempts = 10,
  mutationRetryMs = 2_000,
  hooks = {},
  setupOptions = { openAttempts: 6, openRetryMs: 2_000 },
} = {}) {
  validatePrepareInput({
    batchId, cohortCount, groupSize, config, paths, pollAttempts, mutationAttempts, mutationRetryMs,
  });
  await Promise.all(Object.values(paths).map((filePath) => mkdir(dirname(filePath), { recursive: true })));
  if (resume) await repairTruncatedTail({ filePath: paths.journal, batchId });
  const client = httpClient ?? createGatewayHttpClient({ config });
  const journal = createJournal({ filePath: paths.journal, batchId });
  const local = localReconcilerProvider({ config, injected: localReconciler });
  const initial = await recoverJournal({ filePath: paths.journal, batchId });
  if (!resume && (initial.completed.length > 0 || initial.unresolvedIntents.length > 0)) {
    throw new Error('existing batch journal requires resume');
  }

  try {
  await (await local.get()).preflight({ cleanupMysqlTarget: config.targets.cleanup.mysql });
  // Cleaner 先获得一个原子 seed；任何后续 mutation 中断都可由 seed+journal+DB 恢复完整归属。
  await publishManifest(paths.manifest, buildManifest(batchId, [], [], [], []));
  const plans = createPreparationPlan(batchId, cohortCount, groupSize);
  const credentials = new Map();
  const completedOperationIds = new Map(plans.map(({ cohortId }) => [cohortId, []]));
  const users = [];

  for (const plan of plans) {
    for (const member of plan.members) {
      const operationId = `signup:${plan.cohortId}:${member.memberIndex}`;
      const driven = await driveMutation({
        journal,
        journalPath: paths.journal,
        batchId,
        operationId,
        operation: 'signUp',
        ownership: { email: member.email, username: member.username },
        mutationAttempts,
        mutationRetryMs,
        scheduler,
        mutate: () => client.signUp({
          operationId,
          email: member.email,
          username: member.username,
          password: member.password,
        }),
        reconcile: () => reconcileSignup(client, local, member, operationId),
        hooks,
      });
      completedOperationIds.get(plan.cohortId).push(operationId);
      const login = driven.context.login ?? await loginAndVerify(client, member, operationId);
      credentials.set(driven.result.userId, credentialFrom(member, login));
      member.userId = driven.result.userId;
      member.accessToken = login.accessToken;
      users.push({ userId: member.userId, email: member.email, cohortId: plan.cohortId });
    }
  }

  const relations = [];
  const privateChats = [];
  const cohortResults = [];
  for (const plan of plans) {
    for (const [edgeIndex, [left, right]] of ringEdges(plan.members).entries()) {
      const operationId = `friend:${plan.cohortId}:${edgeIndex}`;
      await driveMutation({
        journal,
        journalPath: paths.journal,
        batchId,
        operationId,
        operation: 'addFriend',
        ownership: { userId: left.userId, friendUserId: right.userId },
        mutationAttempts,
        mutationRetryMs,
        scheduler,
        mutate: () => client.addFriend({
          operationId,
          accessToken: left.accessToken,
          friendUserId: right.userId,
        }),
        reconcile: () => reconcileFriend(client, local, left, right, operationId),
        afterCommitted: (reconciliation) => repairCommittedFriend({
          client,
          left,
          right,
          operationId,
          reconciliation,
          scheduler,
          attempts: mutationAttempts,
          retryMs: mutationRetryMs,
        }),
        hooks,
      });
      completedOperationIds.get(plan.cohortId).push(operationId);
      const chats = await waitForPrivateChatArtifacts({
        client, local, left, right, operationId, scheduler, attempts: pollAttempts,
      });
      relations.push(
        { userId: left.userId, friendUserId: right.userId },
        { userId: right.userId, friendUserId: left.userId },
      );
      privateChats.push(
        { chatId: chats.leftChatId, userId: left.userId, peerUserId: right.userId },
        { chatId: chats.rightChatId, userId: right.userId, peerUserId: left.userId },
      );
    }

    const owner = plan.members[0];
    const members = plan.members.slice(1);
    const operationId = `group:${plan.cohortId}`;
    let setup;
    try {
      setup = await websocketFactory({
        config,
        credentials: plan.members.map(({ userId, accessToken }) => ({ userId, accessToken })),
        batchId,
        receiptJournalPath: paths.setupReceipts ?? `${paths.journal}.setup-receipts.jsonl`,
        ...setupOptions,
      });
      const barrier = setup.waitForGroupCreated({
        ownerUserId: owner.userId,
        memberUserIds: members.map(({ userId }) => userId),
      }).then(
        (value) => ({ value }),
        (error) => ({ error }),
      );
      const driven = await driveMutation({
        journal,
        journalPath: paths.journal,
        batchId,
        operationId,
        operation: 'createGroup',
        ownership: {
          ownerId: owner.userId,
          memberIds: members.map(({ userId }) => userId),
          groupName: plan.groupName,
        },
        mutationAttempts,
        mutationRetryMs,
        scheduler,
        mutate: () => client.createGroup({
          operationId,
          accessToken: owner.accessToken,
          memberIds: members.map(({ userId }) => userId),
          groupName: plan.groupName,
        }),
        reconcile: () => reconcileGroupMutation(client, local, plan, operationId, scheduler, pollAttempts),
        hooks,
      });
      const artifacts = await waitForGroupArtifacts({
        local,
        result: driven.result,
        owner,
        members,
        scheduler,
        attempts: pollAttempts,
      });
      validateGroupArtifactResult(driven.result, artifacts.result);
      await setup.seedKnownReceipts?.(artifacts.evidence.receiptIds);
      const barrierOutcome = await barrier;
      if (barrierOutcome.error !== undefined) throw barrierOutcome.error;
      const notification = barrierOutcome.value;
      validateNotificationArtifacts(notification, artifacts);
      completedOperationIds.get(plan.cohortId).push(operationId);
      cohortResults.push({
        plan,
        result: { groupId: driven.result.groupId, ...artifacts.result },
        evidence: notification,
      });
    } finally {
      await setup?.close();
      for (const member of plan.members) delete member.accessToken;
    }
    await journal.appendCheckpoint({
      checkpointId: plan.cohortId,
      completedOperationIds: completedOperationIds.get(plan.cohortId),
    });
  }

  const manifest = buildManifest(batchId, users, relations, privateChats, cohortResults);
  const credentialManifest = {
    version: 1,
    batchId,
    credentials: users.map(({ userId }) => credentials.get(userId)),
  };
  await publishManifest(paths.manifest, manifest);
  await publishCredentialManifest(paths.credentials, credentialManifest);
  credentials.clear();
  return { manifest, httpClient: client };
  } finally {
    await local.closeOwned();
  }
}

export async function loginRound({
  batchId,
  roundId,
  concurrency = 10,
  selectedUserIds,
  manifestPath,
  credentialPath,
  roundCredentialPath,
  summaryPath,
  config,
  httpClient,
  clock = () => Date.now(),
} = {}) {
  if (!Number.isInteger(concurrency) || concurrency < 1) throw new Error('concurrency must be positive');
  const manifest = await readManifest(manifestPath);
  if (manifest.batchId !== batchId) throw new Error('manifest batch mismatch');
  const source = JSON.parse(await readFile(credentialPath, 'utf8'));
  if (source.batchId !== batchId || !Array.isArray(source.credentials)) {
    throw new Error('credential manifest batch mismatch');
  }
  const selected = new Set(selectedUserIds ?? manifest.users.map(({ userId }) => userId));
  const publicUsers = new Set(manifest.users.map(({ userId }) => userId));
  if ([...selected].some((userId) => !publicUsers.has(userId))) throw new Error('selected user is not in manifest');
  const sourceById = new Map(source.credentials.map((credential) => [credential.userId, credential]));
  const work = [...selected].map((userId) => sourceById.get(userId));
  if (work.some((credential) => credential === undefined)) throw new Error('selected user credential is missing');
  const client = httpClient ?? createGatewayHttpClient({ config });
  const results = new Array(work.length);
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, work.length) }, async () => {
    while (cursor < work.length) {
      const index = cursor++;
      const credential = work[index];
      const startedAt = clock();
      const login = await client.signIn({
        operationId: `login-round:${roundId}:${index}`,
        email: credential.email,
        password: credential.password,
      });
      const latencyMs = clock() - startedAt;
      if (login.userId !== credential.userId) throw new Error('login-round userId mismatch');
      results[index] = {
        latencyMs,
        credential: credentialFrom(credential, login),
      };
    }
  }));
  const roundManifest = {
    version: 1,
    batchId,
    credentials: results.map(({ credential }) => credential),
  };
  const summary = {
    version: 1,
    batchId,
    roundId,
    selectedUserIds: [...selected],
    successCount: results.length,
    failureCount: 0,
    latencyMs: results.map(({ latencyMs }) => latencyMs),
  };
  await publishCredentialManifest(roundCredentialPath, roundManifest);
  await publishNonSecretSummary(summaryPath, summary);
  return { credentials: roundManifest, summary };
}

export async function logoutRound({
  batchId,
  roundId,
  concurrency = 10,
  credentialPath,
  summaryPath,
  config,
  httpClient,
} = {}) {
  if (!Number.isInteger(concurrency) || concurrency < 1) throw new Error('concurrency must be positive');
  const source = JSON.parse(await readFile(credentialPath, 'utf8'));
  if (source.batchId !== batchId || !Array.isArray(source.credentials)) {
    throw new Error('round credential manifest batch mismatch');
  }
  const client = httpClient ?? createGatewayHttpClient({ config });
  let cursor = 0;
  const signedOutUserIds = new Array(source.credentials.length);
  await Promise.all(Array.from({ length: Math.min(concurrency, source.credentials.length) }, async () => {
    while (cursor < source.credentials.length) {
      const index = cursor++;
      const credential = source.credentials[index];
      try {
        await client.signOut({
          operationId: `logout-round:${roundId}:${index}`,
          accessToken: credential.accessToken,
        });
      } catch (error) {
        if (!(error instanceof GatewayBusinessError && error.code === 10001)) throw error;
      }
      signedOutUserIds[index] = credential.userId;
    }
  }));
  const summary = {
    version: 1,
    batchId,
    roundId,
    signedOutUserIds,
    successCount: signedOutUserIds.length,
    failureCount: 0,
  };
  await publishNonSecretSummary(summaryPath, summary);
  return summary;
}

async function driveMutation({
  journal,
  journalPath,
  batchId,
  operationId,
  operation,
  ownership,
  mutationAttempts,
  mutationRetryMs,
  scheduler,
  mutate,
  reconcile,
  afterCommitted,
  hooks,
}) {
  let state = await recoverJournal({ filePath: journalPath, batchId });
  let current = findOperation(state, operationId);
  if (current === undefined) {
    await journal.appendIntent({ operationId, operation, ownership });
  } else if (current.lastType === 'completed') {
    return { result: current.result, evidence: current.reconciliation?.evidence ?? {}, context: {} };
  }

  for (;;) {
    state = await recoverJournal({ filePath: journalPath, batchId });
    current = findOperation(state, operationId);
    if (current.lastType === 'completed') {
      return { result: current.result, evidence: current.reconciliation?.evidence ?? {}, context: {} };
    }
    if (current.lastType === 'reconciled' && current.reconciliation.outcome === 'committed') {
      await afterCommitted?.(current.reconciliation);
      await journal.appendCompleted({ operationId, result: current.reconciliation.result });
      await hooks.afterCompleted?.({ operationId, operation });
      return {
        result: current.reconciliation.result,
        evidence: current.reconciliation.evidence,
        context: {},
      };
    }

    let attempt = current.attempt ?? 0;
    let mutationError;
    if (current.lastType === 'intent' || current.lastType === 'reconciled') {
      attempt += 1;
      if (attempt > mutationAttempts) throw new Error(`${operationId} exceeded mutation attempts`);
      await journal.appendInvoked({ operationId, attempt });
      try {
        await mutate();
      } catch (error) {
        mutationError = error;
      }
      await hooks.afterMutation?.({ operationId, operation, attempt, mutationError });
    }
    const reconciliation = await reconcile({ attempt, mutationError });
    await journal.appendReconciled({
      operationId,
      attempt,
      outcome: reconciliation.outcome,
      result: reconciliation.result,
      evidence: reconciliation.evidence,
    });
    if (reconciliation.outcome === 'committed') {
      await afterCommitted?.(reconciliation);
      await journal.appendCompleted({ operationId, result: reconciliation.result });
      await hooks.afterCompleted?.({ operationId, operation });
      return {
        result: reconciliation.result,
        evidence: reconciliation.evidence,
        context: reconciliation.context ?? {},
      };
    }
    if (attempt < mutationAttempts) await scheduler.wait(attempt, mutationRetryMs);
  }
}

async function repairCommittedFriend({
  client,
  left,
  right,
  operationId,
  reconciliation,
  scheduler,
  attempts,
  retryMs,
}) {
  if (reconciliation?.evidence?.source !== 'local-db-social') return;
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      await client.addFriend({
        operationId: `${operationId}:repair:${attempt}`,
        accessToken: left.accessToken,
        friendUserId: right.userId,
      });
      return;
    } catch (error) {
      lastError = error;
      if (attempt < attempts) await scheduler.wait(attempt, retryMs);
    }
  }
  throw lastError;
}

async function reconcileSignup(client, local, member, operationId) {
  try {
    const login = await loginAndVerify(client, member, operationId);
    return {
      outcome: 'committed',
      result: { userId: login.userId },
      evidence: { source: 'signIn+userDetail' },
      context: { login },
    };
  } catch (error) {
    return (await local.get()).signUp({ email: member.email, username: member.username, causeCode: error.code });
  }
}

async function loginAndVerify(client, member, operationId) {
  const login = await client.signIn({
    operationId: `${operationId}:reconcile-login`,
    email: member.email,
    password: member.password,
  });
  const detail = await client.userDetail({
    operationId: `${operationId}:reconcile-detail`,
    accessToken: login.accessToken,
  });
  if (detail.userId !== login.userId || detail.email !== member.email || detail.username !== member.username) {
    throw new Error('signUp reconciliation identity mismatch');
  }
  return login;
}

async function reconcileFriend(client, local, left, right, operationId) {
  try {
    const [leftList, rightList] = await Promise.all([
      client.friendList({ operationId: `${operationId}:left`, accessToken: left.accessToken }),
      client.friendList({ operationId: `${operationId}:right`, accessToken: right.accessToken }),
    ]);
    const leftFound = leftList.friends?.some(({ userId }) => userId === right.userId) === true;
    const rightFound = rightList.friends?.some(({ userId }) => userId === left.userId) === true;
    if (leftFound && rightFound) {
      return {
        outcome: 'committed',
        result: { userId: left.userId, friendUserId: right.userId },
        evidence: { source: 'friendList' },
      };
    }
  } catch (error) {
    if (error.code !== 10001 && error.name !== 'GatewayBusinessError') {
      // Availability failures are intentionally resolved by the local read-only authority below.
    }
  }
  return (await local.get()).addFriend({ userId: left.userId, friendUserId: right.userId });
}

async function waitForPrivateChatArtifacts({ client, local, left, right, operationId, scheduler, attempts }) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const [leftList, rightList] = await Promise.all([
        client.getChatList({ operationId: `${operationId}:chat-left:${attempt}`, accessToken: left.accessToken }),
        client.getChatList({ operationId: `${operationId}:chat-right:${attempt}`, accessToken: right.accessToken }),
      ]);
      const leftChat = leftList.chatList?.find((chat) => chat.chatType === 'PRIVATE' && chat.chatName === right.username);
      const rightChat = rightList.chatList?.find((chat) => chat.chatType === 'PRIVATE' && chat.chatName === left.username);
      if (leftChat !== undefined && rightChat !== undefined) {
        return { leftChatId: leftChat.chatId, rightChatId: rightChat.chatId };
      }
    } catch {
      // The local Message owner remains authoritative when the Gateway context is unavailable.
    }
    const artifacts = await (await local.get()).privateChatArtifacts({
      userId: left.userId,
      friendUserId: right.userId,
    });
    if (artifacts.outcome === 'converged') {
      const leftChat = artifacts.result.privateChats.find(({ userId }) => userId === left.userId);
      const rightChat = artifacts.result.privateChats.find(({ userId }) => userId === right.userId);
      return { leftChatId: leftChat.chatId, rightChatId: rightChat.chatId };
    }
    if (artifacts.outcome !== 'pending') throw new Error('invalid private chat artifact outcome');
    if (attempt < attempts) await scheduler.wait(attempt);
  }
  throw new Error('private chat events did not converge');
}

async function reconcileGroupMutation(client, local, plan, operationId, scheduler, attempts) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    let lists;
    try {
      lists = await Promise.all(plan.members.map((member) => client.getGroupList({
        operationId: `${operationId}:groups:${member.memberIndex}:${attempt}`,
        accessToken: member.accessToken,
      })));
    } catch {
      break;
    }
    const matches = lists.map((list) => list.groupList?.filter(({ groupName }) => groupName === plan.groupName) ?? []);
    if (matches.every((items) => items.length === 0)) {
      if (attempt < attempts) await scheduler.wait(attempt);
      continue;
    }
    if (matches.some((items) => items.length !== 1)) {
      throw new Error('group API reconciliation requires exactly one same-name group per member');
    }
    const items = matches.map(([item]) => item);
    if (items.some(({ ownerId }) => ownerId !== plan.members[0].userId)) {
      throw new Error('group API reconciliation found wrong owner');
    }
    if (items.some(({ memberCount }) => memberCount !== plan.members.length)) {
      throw new Error('group API reconciliation found wrong memberCount');
    }
    const groupIds = new Set(items.map(({ groupId }) => groupId));
    if (groupIds.size !== 1) throw new Error('group reconciliation found duplicate group names');
    const ownerChatId = items[0].chatId;
    const memberChats = plan.members.slice(1).map((member, index) => {
      return { userId: member.userId, chatId: items[index + 1].chatId };
    });
    return {
      outcome: 'committed',
      result: { groupId: items[0].groupId, ownerChatId, memberChats },
      evidence: {
        source: 'groupList',
      },
    };
  }
  return (await local.get()).createGroup({
    batchId: plan.batchId,
    groupName: plan.groupName,
    ownerId: plan.members[0].userId,
    memberIds: plan.members.slice(1).map(({ userId }) => userId),
  });
}

async function waitForGroupArtifacts({ local, result, owner, members, scheduler, attempts }) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const artifacts = await (await local.get()).groupArtifacts({
      groupId: result.groupId,
      ownerId: owner.userId,
      memberIds: members.map(({ userId }) => userId),
    });
    if (artifacts.outcome === 'converged') return artifacts;
    if (artifacts.outcome !== 'pending') throw new Error('invalid group artifact reconciliation outcome');
    if (attempt < attempts) await scheduler.wait(attempt);
  }
  throw new Error('group Message artifacts did not converge');
}

function validateGroupArtifactResult(groupResult, artifactResult) {
  if (groupResult.ownerChatId !== undefined && groupResult.ownerChatId !== artifactResult.ownerChatId) {
    throw new Error('group owner chat differs between API and Message reconciliation');
  }
  if (groupResult.memberChats !== undefined) {
    const expected = new Map(groupResult.memberChats.map(({ userId, chatId }) => [userId, chatId]));
    if (artifactResult.memberChats.some(({ userId, chatId }) => expected.get(userId) !== chatId)
        || expected.size !== artifactResult.memberChats.length) {
      throw new Error('group member chats differ between API and Message reconciliation');
    }
  }
}

function validateNotificationArtifacts(notification, artifacts) {
  if (notification.messageId !== artifacts.evidence.messageId) {
    throw new Error('group notification messageId differs from Message reconciliation');
  }
  const expected = new Set(artifacts.evidence.receiptIds);
  if (notification.receiptIds.length !== expected.size
      || notification.receiptIds.some((receiptId) => !expected.has(receiptId))) {
    throw new Error('group notification receipts differ from Message reconciliation');
  }
}

function buildManifest(batchId, users, relations, privateChats, cohortResults) {
  const groups = [];
  const cohorts = [];
  const groupChats = [];
  const messages = [];
  const receiptIds = [];
  for (const { plan, result, evidence } of cohortResults) {
    const memberUserIds = plan.members.map(({ userId }) => userId);
    groups.push({
      groupId: result.groupId,
      name: plan.groupName,
      ownerUserId: plan.members[0].userId,
      memberUserIds,
    });
    cohorts.push({ cohortId: plan.cohortId, userIds: memberUserIds, groupId: result.groupId });
    groupChats.push(
      { chatId: result.ownerChatId, groupId: result.groupId, userId: plan.members[0].userId },
      ...result.memberChats.map(({ userId, chatId }) => ({ chatId, groupId: result.groupId, userId })),
    );
    messages.push({
      messageId: evidence.messageId,
      type: 'group',
      senderUserId: plan.members[0].userId,
      correlationToken: `system:group_created:${result.groupId}`,
      groupId: result.groupId,
    });
    receiptIds.push(...evidence.receiptIds);
  }
  return {
    version: 1,
    batchId,
    prefix: `loadtest-${batchId}`,
    schemas: { ...SCHEMAS },
    users,
    cohorts,
    groups,
    relations,
    privateChats,
    groupChats,
    messages,
    receiptIds,
  };
}

export function createPreparationPlan(batchId, cohortCount, groupSize) {
  return Array.from({ length: cohortCount }, (_, cohortIndex) => {
    const cohortOrdinal = String(cohortIndex + 1).padStart(4, '0');
    const cohortId = `cohort-${cohortOrdinal}`;
    return {
      batchId,
      cohortId,
      groupName: `loadtest-${batchId}-g${cohortIndex.toString(36).padStart(3, '0')}`,
      members: Array.from({ length: groupSize }, (unused, memberIndex) => {
        const globalUserOrdinal = cohortIndex * groupSize + memberIndex;
        const ordinal = globalUserOrdinal.toString(36).padStart(3, '0');
        const identity = `loadtest-${batchId}-${ordinal}`;
        return {
          memberIndex,
          email: `${identity}@lt.dev`,
          username: identity,
          password: `LT!${batchId}!${cohortOrdinal}!${ordinal}`,
        };
      }),
    };
  });
}

function localReconcilerProvider({ config, injected }) {
  return {
    async get() {
      if (injected === undefined) {
        throw new Error('local reconciliation is required when Gateway API state cannot prove the mutation');
      }
      return injected;
    },
    async closeOwned() {},
  };
}

function ringEdges(members) {
  const edges = [];
  const seen = new Set();
  for (let index = 0; index < members.length; index += 1) {
    const left = members[index];
    const right = members[(index + 1) % members.length];
    const key = [left.userId, right.userId].sort().join(':');
    if (!seen.has(key)) {
      seen.add(key);
      edges.push([left, right]);
    }
  }
  return edges;
}

function credentialFrom(source, login) {
  return {
    userId: login.userId,
    email: source.email,
    password: source.password,
    accessToken: login.accessToken,
    refreshToken: login.refreshToken,
  };
}

function findOperation(state, operationId) {
  return [...state.completed, ...state.unresolvedIntents].find((operation) => operation.operationId === operationId);
}

async function publishNonSecretSummary(filePath, value) {
  const temporaryPath = `${filePath}.${process.pid}.tmp`;
  await mkdir(dirname(filePath), { recursive: true });
  const { open, rename, rm } = await import('node:fs/promises');
  let handle;
  try {
    handle = await open(temporaryPath, 'wx', 0o600);
    await handle.writeFile(`${JSON.stringify(value, null, 2)}\n`);
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(temporaryPath, filePath);
  } catch (error) {
    await handle?.close().catch(() => undefined);
    await rm(temporaryPath, { force: true }).catch(() => undefined);
    throw error;
  }
}

function validatePrepareInput({
  batchId, cohortCount, groupSize, config, paths, pollAttempts, mutationAttempts, mutationRetryMs,
}) {
  if (typeof batchId !== 'string' || !/^[a-z0-9]{1,6}$/.test(batchId)) {
    throw new Error('batchId must contain 1 to 6 lowercase base36 characters');
  }
  if (!Number.isInteger(mutationRetryMs) || mutationRetryMs < 0) {
    throw new Error('mutationRetryMs is invalid');
  }
  for (const [name, value] of Object.entries({ cohortCount, groupSize, pollAttempts, mutationAttempts })) {
    if (!Number.isInteger(value) || value < (name === 'groupSize' ? 2 : 1)) {
      throw new Error(`${name} is invalid`);
    }
  }
  if (config?.groupSize !== groupSize) throw new Error('groupSize must match resolved config');
  const maximumUsers = Math.max(...config.levels.connection.effective);
  if (cohortCount * groupSize > maximumUsers) {
    throw new Error(`prepared users must not exceed maximum connection level ${maximumUsers}`);
  }
  for (const name of ['journal', 'manifest', 'credentials']) {
    if (typeof paths?.[name] !== 'string' || paths[name].length === 0) throw new Error(`paths.${name} is required`);
  }
}
