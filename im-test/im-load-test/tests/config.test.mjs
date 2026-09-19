import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

import {
  CUSTOM_TAGS,
  SYSTEM_TAGS,
  resolveConfig,
} from '../lib/config.js';

const defaultsUrl = new URL('../config/defaults.json', import.meta.url);

async function defaults() {
  return JSON.parse(await readFile(defaultsUrl, 'utf8'));
}

test('defaults fix the T2 load profile and thresholds', async () => {
  const value = await defaults();

  assert.equal(value.k6Version, '2.2.0');
  assert.deepEqual(value.levels.connection, [20, 50, 100, 200, 400, 800, 1600, 3200]);
  assert.deepEqual(value.levels.message, [50, 100, 200, 400, 800, 1600, 3200]);
  assert.equal(value.groupSize, 10);
  assert.equal(value.messageBytes, 256);
  assert.deepEqual(value.connection, {
    ratePerSecond: 20,
    stableSeconds: 60,
    measureSeconds: 300,
    disconnectSeconds: 60,
  });
  assert.deepEqual(value.message, {
    warmupSeconds: 60,
    measureSeconds: 300,
    drainSeconds: 60,
  });
  assert.deepEqual(value.messageMix, { private: 70, group: 30 });
  assert.deepEqual(value.maxRounds, { connection: 7, message: 7 });
  assert.deepEqual(value.timeouts, {
    roundSeconds: 900,
    stageSeconds: 7200,
    allSeconds: 18000,
  });
  assert.equal(value.maxMessageRate, 3200);
  assert.deepEqual(value.thresholds, {
    lateSlotMs: 200,
    actualTargetRateMin: 0.98,
    actualTargetRateMax: 1.02,
    skipRateMax: 0.01,
    scheduleP95MsMax: 100,
    connectionSuccessRateMin: 0.995,
    onlineRateMin: 0.995,
    unexpectedDisconnectRateMax: 0.005,
    sendSuccessRateMin: 0.999,
    deliveryAckSuccessRateMin: 0.999,
    receiveLatencyP95Ms: 500,
    receiveLatencyP99Ms: 2000,
    unknownReceiverMax: 0,
    duplicateRateMax: 0.001,
    minimumSends: 1000,
    drainResidualRateMax: 0.001,
    hostAvailableMemoryBytesMin: 1073741824,
    hostMemoryConsecutiveSamples: 3,
    generatorCpuPercentMin: 90,
    generatorCpuConsecutiveSamples: 3,
    generatorSampleSeconds: 5,
  });
});

test('resolves localhost defaults and preserves requested and effective levels', async () => {
  const config = resolveConfig(await defaults(), {
    levels: {
      connection: [21, 29, 40, 9],
      message: [59, 50, 101],
    },
  });

  assert.deepEqual(config.levels.connection, {
    requested: [21, 29, 40, 9],
    effective: [20, 40],
  });
  assert.deepEqual(config.levels.message, {
    requested: [59, 50, 101],
    effective: [50, 100],
  });
  assert.equal(config.targets.http, 'http://127.0.0.1:18010');
  assert.equal(config.targets.websocket, 'ws://127.0.0.1:18011/ws');
  assert.equal(config.targets.prometheus, 'http://127.0.0.1:9090');
  assert.equal(config.redirects, 'error');
});

test('allows non-local pressure targets only with an explicit unlock', async () => {
  const value = await defaults();
  const remoteTargets = {
    http: 'https://load.example.test',
    websocket: 'wss://load.example.test/ws',
    prometheus: 'https://metrics.example.test',
  };

  assert.throws(
    () => resolveConfig(value, { targets: remoteTargets }),
    /non-local pressure target/i,
  );
  assert.deepEqual(
    resolveConfig(value, { allowRemotePressure: true, targets: remoteTargets }).targets,
    { ...value.targets, ...remoteTargets },
  );
});

test('cleanup targets stay local even when remote pressure is unlocked', async () => {
  const value = await defaults();

  assert.throws(
    () => resolveConfig(value, {
      allowRemotePressure: true,
      targets: {
        http: 'https://load.example.test',
        cleanup: { mysql: 'mysql://db.example.test:3306' },
      },
    }),
    /cleanup target must be local/i,
  );
});

test('cleanup targets require owner-specific local URL schemes', async () => {
  const value = await defaults();
  const config = resolveConfig(value, {
    targets: {
      cleanup: {
        mysql: 'mysql://localhost:3306',
        redis: 'rediss://127.0.0.1:6380',
      },
    },
  });

  assert.equal(config.targets.cleanup.mysql, 'mysql://localhost:3306');
  assert.equal(config.targets.cleanup.redis, 'rediss://127.0.0.1:6380');
  for (const [owner, target] of [
    ['mysql', 'ftp://127.0.0.1:3306'],
    ['mysql', 'http://127.0.0.1:3306'],
    ['redis', 'file://127.0.0.1/tmp/redis.sock'],
    ['redis', 'http://127.0.0.1:6379'],
  ]) {
    assert.throws(
      () => resolveConfig(value, { targets: { cleanup: { [owner]: target } } }),
      /unsupported protocol/i,
    );
  }
});

test('rejects every query string on configured targets', async () => {
  const value = await defaults();
  const targetsWithQuery = [
    { http: 'http://127.0.0.1:18010?author=value' },
    { websocket: 'ws://127.0.0.1:18011/ws?tokenizer=value' },
    { prometheus: 'http://127.0.0.1:9090?passwordPolicy=value' },
    { cleanup: { mysql: 'mysql://127.0.0.1:3306?jwtAlgorithm=value' } },
    { cleanup: { redis: 'redis://127.0.0.1:6379?sessionCookie=value' } },
    { http: 'http://127.0.0.1:18010?dbCredential=value' },
    { websocket: 'ws://127.0.0.1:18011/ws?clientKey=value' },
    { prometheus: 'http://127.0.0.1:9090?passphrase=value' },
  ];

  for (const targets of targetsWithQuery) {
    assert.throws(
      () => resolveConfig(value, { targets }),
      /query string/i,
    );
  }

  const config = resolveConfig(value, {
    targets: {
      http: 'http://localhost:18010',
      websocket: 'ws://localhost:18011/ws',
      prometheus: 'http://localhost:9090',
      cleanup: {
        mysql: 'mysql://localhost:3306',
        redis: 'rediss://localhost:6380',
      },
    },
  });
  assert.deepEqual(config.targets, {
    http: 'http://localhost:18010',
    websocket: 'ws://localhost:18011/ws',
    prometheus: 'http://localhost:9090',
    cleanup: {
      mysql: 'mysql://localhost:3306',
      redis: 'rediss://localhost:6380',
    },
  });
});

test('rejects redirects, secrets, invalid message size, and empty effective levels', async () => {
  const value = await defaults();

  assert.throws(() => resolveConfig(value, { redirects: 'follow' }), /redirects/i);
  assert.throws(() => resolveConfig(value, { accessToken: 'secret' }), /secret/i);
  assert.throws(() => resolveConfig(value, { messageBytes: 255 }), /messageBytes/i);
  assert.throws(
    () => resolveConfig(value, { levels: { connection: [1, 9] } }),
    /effective connection levels/i,
  );
  assert.throws(
    () => resolveConfig(value, { levels: { message: [3210] } }),
    /maxMessageRate/i,
  );
});

test('exports only bounded system and custom metric tags', async () => {
  const value = await defaults();
  const config = resolveConfig(value);

  assert.deepEqual(SYSTEM_TAGS, ['scenario', 'name', 'status', 'method']);
  assert.deepEqual(CUSTOM_TAGS, ['batch', 'round', 'phase', 'message_type']);
  assert.deepEqual(config.systemTags, SYSTEM_TAGS);
  assert.deepEqual(config.customTags, CUSTOM_TAGS);
  assert.throws(
    () => resolveConfig(value, { customTags: [...CUSTOM_TAGS, 'userId'] }),
    /customTags/i,
  );
});

test('deep-freezes the complete resolved configuration', async () => {
  const config = resolveConfig(await defaults());

  assert.ok(Object.isFrozen(config));
  assert.ok(Object.isFrozen(config.targets));
  assert.ok(Object.isFrozen(config.targets.cleanup));
  assert.ok(Object.isFrozen(config.systemTags));
  assert.ok(Object.isFrozen(config.levels.connection.effective));
  assert.throws(() => {
    config.targets.cleanup.mysql = 'mysql://db.example.test:3306';
  }, TypeError);
  assert.throws(() => {
    config.redirects = 'follow';
  }, TypeError);
  assert.throws(() => {
    config.systemTags.push('url');
  }, TypeError);
});
