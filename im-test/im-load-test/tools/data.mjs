import { randomBytes } from 'node:crypto';
import { mkdir, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';

import { resolveConfig } from '../lib/config.js';
import { loginRound, logoutRound, prepareBatch } from './lib/preparation.mjs';
import { createLocalReconciler } from './lib/local-reconciler.mjs';

export async function main(argv = process.argv.slice(2), dependencies = {}) {
  const [command, ...args] = argv;
  if (!['prepare', 'login-round', 'logout-round'].includes(command)) {
    throw new Error('expected prepare, login-round, or logout-round command');
  }
  const options = parseOptions(command, args);
  const environment = dependencies.env ?? process.env;
  const defaults = dependencies.defaults ?? JSON.parse(
    await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'),
  );
  const groupSize = integerOption(options['group-size'], defaults.groupSize);
  const environmentTargets = environment.IM_LOAD_TEST_HTTP_URL === undefined
    ? undefined
    : { http: environment.IM_LOAD_TEST_HTTP_URL };
  const config = dependencies.config ?? resolveConfig(defaults, {
    groupSize,
    ...((dependencies.targets ?? environmentTargets) === undefined
      ? {}
      : { targets: dependencies.targets ?? environmentTargets }),
  });
  if (command === 'prepare') {
    const batchId = options.resume ?? options.batch ?? dependencies.batchId
      ?? (dependencies.randomBatchId ?? randomBatchId)();
    assertBatchId(batchId);
    const outputDirectory = options['output-dir'] ?? join('.harness', 'load-test', batchId);
    const paths = dataPaths(outputDirectory);
    await (dependencies.makeDirectory ?? mkdir)(outputDirectory, { recursive: true });
    const userCount = integerOption(options.users, 3200);
    const maximumUsers = Math.max(...config.levels.connection.effective);
    if (userCount > maximumUsers) throw new Error(`users must not exceed maximum connection level ${maximumUsers}`);
    if (userCount % groupSize !== 0) throw new Error('users must be a multiple of groupSize for full cohorts');
    let ownedReconciler;
    const reconciler = dependencies.localReconciler ?? await loadLocalReconciler({
      environment,
      fileSystem: dependencies.fileSystem,
      factory: dependencies.createLocalReconciler ?? createLocalReconciler,
    });
    if (dependencies.localReconciler === undefined) ownedReconciler = reconciler;
    try {
      return await (dependencies.prepareBatch ?? prepareBatch)({
        batchId,
        resume: options.resume !== undefined,
        cohortCount: userCount / groupSize,
        groupSize,
        config,
        paths,
        localReconciler: reconciler,
      });
    } finally {
      await ownedReconciler?.close();
    }
  }
  const batchId = requiredOption(options, 'batch');
  assertBatchId(batchId);
  const roundId = requiredOption(options, 'round');
  assertRunId(roundId, 'roundId');
  const outputDirectory = options['output-dir'] ?? join('.harness', 'load-test', batchId);
  const paths = dataPaths(outputDirectory);
  const roundCredentialPath = join(outputDirectory, `credentials-${roundId}.json`);
  if (command === 'logout-round') {
    return (dependencies.logoutRound ?? logoutRound)({
      batchId,
      roundId,
      concurrency: integerOption(options.concurrency, 10),
      credentialPath: roundCredentialPath,
      summaryPath: join(outputDirectory, `logout-${roundId}.json`),
      config,
    });
  }
  return (dependencies.loginRound ?? loginRound)({
    batchId,
    roundId,
    concurrency: integerOption(options.concurrency, 10),
    manifestPath: paths.manifest,
    credentialPath: paths.credentials,
    roundCredentialPath,
    summaryPath: join(outputDirectory, `login-${roundId}.json`),
    config,
  });
}

function randomBatchId() {
  const space = 36n ** 6n;
  const value = BigInt(`0x${randomBytes(4).toString('hex')}`) % space;
  return value.toString(36).padStart(6, '0');
}

function assertBatchId(value) {
  if (typeof value !== 'string' || !/^[a-z0-9]{1,6}$/.test(value)) {
    throw new Error('batchId must contain 1 to 6 lowercase base36 characters');
  }
}

function assertRunId(value, name) {
  if (typeof value !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(value)) {
    throw new Error(`${name} must be a bounded identifier`);
  }
}

async function loadLocalReconciler({ environment, fileSystem = {}, factory }) {
  const filePath = environment.IM_LOAD_TEST_DATABASE_TARGETS_FILE;
  if (typeof filePath !== 'string' || filePath.length === 0) {
    throw new Error('IM_LOAD_TEST_DATABASE_TARGETS_FILE is required for prepare');
  }
  const read = fileSystem.readFile ?? readFile;
  const fileStat = fileSystem.stat ?? (await import('node:fs/promises')).stat;
  const metadata = await fileStat(filePath);
  if ((metadata.mode & 0o777) !== 0o600) throw new Error('database targets file must have mode 0600');
  let targets;
  try {
    targets = JSON.parse(await read(filePath, 'utf8'));
  } catch (error) {
    if (error instanceof SyntaxError) throw new Error('database targets file must contain valid JSON');
    throw error;
  }
  return factory({ targets });
}

function parseOptions(command, args) {
  const allowed = command === 'prepare'
    ? new Set(['batch', 'resume', 'users', 'group-size', 'output-dir'])
    : new Set(['batch', 'round', 'concurrency', 'output-dir']);
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const flag = args[index];
    if (typeof flag !== 'string' || !flag.startsWith('--')) throw new Error(`unknown option ${flag}`);
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`unknown option --${name} for ${command}`);
    if (Object.hasOwn(options, name)) throw new Error(`duplicate option --${name}`);
    const value = args[index + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`missing value for --${name}`);
    options[name] = value;
    index += 1;
  }
  if (command === 'prepare' && options.batch !== undefined && options.resume !== undefined) {
    throw new Error('--batch and --resume conflict');
  }
  return options;
}

function dataPaths(directory) {
  return {
    journal: join(directory, 'journal.jsonl'),
    manifest: join(directory, 'manifest.json'),
    credentials: join(directory, 'credentials.json'),
  };
}

function integerOption(value, fallback) {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) throw new Error(`invalid integer option ${value}`);
  return parsed;
}

function requiredOption(options, name) {
  if (typeof options[name] !== 'string' || options[name].length === 0) throw new Error(`--${name} is required`);
  return options[name];
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
