import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, rm } from 'node:fs/promises';
import { basename, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

import { classifyConnectionRound } from '../lib/connection-state.js';
import { classifyMessageRun, evaluateLoadGenerator } from '../lib/load-generator-health.js';

export async function main(argv = process.argv.slice(2)) {
  const options = parseOptions(argv);
  const kind = required(options, 'kind');
  if (!['connection', 'message'].includes(kind)) throw new Error('kind must be connection or message');
  const summary = await readJson(required(options, 'summary'));
  const resourceSamples = await parseResourceSamples(required(options, 'resources'));
  const defaults = await readJson(new URL('../config/defaults.json', import.meta.url));
  const routeEvidence = kind === 'connection'
    ? await readJson(required(options, 'route-evidence'))
    : undefined;
  if (kind === 'message' && options['route-evidence'] !== undefined) {
    throw new Error('route-evidence is only valid for connection rounds');
  }
  const report = buildRoundReport({
    kind,
    summary,
    resourceSamples,
    routeEvidence,
    thresholds: defaults.thresholds,
  });
  await publishJson(required(options, 'output'), report);
  process.stdout.write(`${JSON.stringify({ kind, decision: report.decision })}\n`);
}

export function buildRoundReport({
  kind,
  summary,
  resourceSamples,
  routeEvidence,
  thresholds,
} = {}) {
  const completeSamples = Array.isArray(resourceSamples)
      && resourceSamples.every((sample) => sample?.status === 'complete')
    ? resourceSamples.map(({ atMs, cpuPercent, rssBytes, availableMemoryBytes }) => ({
      atMs,
      cpuPercent,
      rssBytes,
      availableMemoryBytes,
    }))
    : [];
  let generatorHealth;
  let decision;
  if (kind === 'connection') {
    generatorHealth = evaluateLoadGenerator({
      samples: completeSamples,
      actualTargetRate: 1,
      sendExceptions: { count: 0, origin: 'none' },
      thresholds: generatorThresholds(thresholds),
    });
    decision = classifyConnectionRound({ summary, routeEvidence }, {
      thresholds,
      pressureEvidence: generatorHealth.evidence,
    });
    if (generatorHealth.status === 'uncertain') {
      decision = {
        status: 'uncertain',
        reasons: [...new Set([...generatorHealth.reasons, ...decision.reasons])],
      };
    }
  } else if (kind === 'message') {
    ({ generatorHealth, decision } = classifyMessageRun({
      summary,
      samples: completeSamples,
      sendExceptionOrigin: 'generator',
      thresholds,
    }));
  } else {
    throw new Error('kind must be connection or message');
  }
  return {
    version: 1,
    boundary: 'load-test-round',
    kind,
    input: {
      batchId: summary?.input?.batchId ?? null,
      roundId: summary?.input?.roundId ?? null,
      effectiveUsers: summary?.input?.effectiveUsers ?? null,
    },
    generatorHealth,
    decision,
  };
}

export async function parseResourceSamples(filePath) {
  const text = await readFile(filePath, 'utf8');
  return text.split('\n').filter(Boolean).map((line) => {
    const sample = JSON.parse(line);
    return {
      atMs: sample.timestamp * 1_000,
      status: sample.status,
      cpuPercent: typeof sample.k6CpuPercent === 'number'
        ? Math.min(100, sample.k6CpuPercent)
        : sample.k6CpuPercent,
      rssBytes: sample.k6RssBytes,
      availableMemoryBytes: sample.availableMemoryBytes,
    };
  });
}

function generatorThresholds(thresholds) {
  return {
    sampleSeconds: thresholds?.generatorSampleSeconds,
    actualTargetRateMin: thresholds?.actualTargetRateMin,
    actualTargetRateMax: thresholds?.actualTargetRateMax,
    hostAvailableMemoryBytesMin: thresholds?.hostAvailableMemoryBytesMin,
    hostMemoryConsecutiveSamples: thresholds?.hostMemoryConsecutiveSamples,
    generatorCpuPercentMin: thresholds?.generatorCpuPercentMin,
    generatorCpuConsecutiveSamples: thresholds?.generatorCpuConsecutiveSamples,
  };
}

function parseOptions(argv) {
  const allowed = new Set(['kind', 'summary', 'resources', 'route-evidence', 'output']);
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (typeof flag !== 'string' || !flag.startsWith('--') || !allowed.has(flag.slice(2))) {
      throw new Error(`unknown option ${flag}`);
    }
    if (value === undefined || value.startsWith('--')) throw new Error(`missing value for ${flag}`);
    const name = flag.slice(2);
    if (Object.hasOwn(options, name)) throw new Error(`duplicate option ${flag}`);
    options[name] = value;
  }
  return options;
}

function required(options, name) {
  const value = options[name];
  if (typeof value !== 'string' || value.length === 0) throw new Error(`--${name} is required`);
  return value;
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function publishJson(filePath, value) {
  const directory = dirname(filePath);
  await mkdir(directory, { recursive: true });
  const temporary = `${directory}/.${basename(filePath)}.${process.pid}.${randomUUID()}.tmp`;
  let handle;
  try {
    handle = await open(temporary, 'wx', 0o600);
    await handle.writeFile(`${JSON.stringify(value, null, 2)}\n`);
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(temporary, filePath);
    const directoryHandle = await open(directory, 'r');
    try {
      await directoryHandle.sync();
    } finally {
      await directoryHandle.close();
    }
  } catch (error) {
    await handle?.close().catch(() => undefined);
    await rm(temporary, { force: true }).catch(() => undefined);
    throw error;
  }
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
