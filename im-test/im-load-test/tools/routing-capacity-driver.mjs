#!/usr/bin/env node
import { createHash, randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';
import { chmod, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { join, resolve } from 'node:path';

const requiredMetrics = [
  'connectionSuccessRate', 'businessSuccessRate', 'ackSuccessRate',
  'receiveLatencyP95Ms', 'receiveLatencyP99Ms', 'duplicateRate', 'misrouteCount',
  'oldEpochAdmissionCount', 'lookupCount', 'routeLookupP95Ms', 'routeLookupP99Ms',
  'fullLookupRate', 'snapshotAckRate', 'snapshotAckP95Ms', 'staleRouteCount',
  'migrationP95Ms', 'electionRecoveryMs', 'cpuMaxPercent', 'rssLimitPercent',
  'rssMaxBytes', 'diskUsagePercent', 'networkBytes', 'diskWriteBytes', 'raftFsyncP99Ms',
];
const faultPhases = new Set(['gateway-switch', 'broker-drain',
  'leader-route-broker-failure', 'minority-jvm-pause']);
const resourceMetrics = new Set(['cpuMaxPercent', 'rssLimitPercent', 'rssMaxBytes',
  'diskUsagePercent', 'networkBytes', 'diskWriteBytes']);

export function resourceValue(name, samples, nodeIds, window) {
  if (!Array.isArray(samples)) throw new Error(`resource samples missing: ${name}`);
  let maximum = 0;
  let total = 0;
  for (const nodeId of nodeIds) {
    const series = samples.filter((sample) => sample.nodeId === nodeId)
      .sort((first, second) => first.atMs - second.atMs);
    if (series.length < 2 || series[0].atMs > window.startedAt
        || series.at(-1).atMs < window.finishedAt) throw new Error(`resource node coverage missing: ${nodeId}`);
    const measured = series.filter((sample) => sample.atMs >= window.startedAt
      && sample.atMs <= window.finishedAt);
    if (measured.length < 1) throw new Error(`resource measurement window missing: ${nodeId}`);
    for (let index = 1; index < series.length; index += 1) {
      const sample = series[index];
      if (!Number.isFinite(sample.atMs) || !Number.isFinite(sample.value) || sample.value < 0
          || (index > 0 && sample.atMs - series[index - 1].atMs > 30_000)) {
        throw new Error(`invalid resource sampling interval: ${nodeId}`);
      }
      if (sample.atMs >= window.startedAt && sample.atMs <= window.finishedAt) {
        maximum = Math.max(maximum, sample.value);
        total += ['networkBytes', 'diskWriteBytes'].includes(name)
          ? (sample.value >= series[index - 1].value
            ? sample.value - series[index - 1].value : sample.value)
          : 0;
      }
    }
  }
  return ['networkBytes', 'diskWriteBytes'].includes(name) ? total : maximum;
}

export function applicable(metric, phase, workload) {
  if (workload === 'connection' && [
    'businessSuccessRate', 'ackSuccessRate', 'receiveLatencyP95Ms',
    'receiveLatencyP99Ms',
  ].includes(metric)) return false;
  if (['migrationP95Ms', 'snapshotAckP95Ms'].includes(metric)) {
    return ['gateway-switch', 'broker-drain', 'leader-route-broker-failure'].includes(phase);
  }
  if (metric === 'electionRecoveryMs') return phase === 'leader-route-broker-failure';
  return true;
}

function required(value, name) {
  if (value === undefined || value === null || value === '') throw new Error(`${name} is required`);
  return value;
}

async function json(file) {
  return JSON.parse(await readFile(file, 'utf8'));
}

async function save(file, value) {
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  await chmod(file, 0o600);
}

function command(argv, env, timeoutMillis, logFile, signal) {
  if (!Array.isArray(argv) || argv.length === 0 || argv.some((part) => typeof part !== 'string')) {
    throw new Error('hook must be a nonempty argv array');
  }
  return new Promise((resolveRun, rejectRun) => {
    signal?.throwIfAborted();
    const child = spawn(argv[0], argv.slice(1), {
      env: { ...process.env, ...env }, detached: true, stdio: ['ignore', 'pipe', 'pipe'],
    });
    const log = createWriteStream(logFile, { mode: 0o600 });
    child.stdout.pipe(log, { end: false });
    child.stderr.pipe(log, { end: false });
    let timedOut = false;
    const terminate = () => {
      try { process.kill(-child.pid, 'SIGTERM'); } catch { /* already exited */ }
      setTimeout(() => {
        try { process.kill(-child.pid, 'SIGKILL'); } catch { /* already exited */ }
      }, 1000).unref();
    };
    const timer = setTimeout(() => {
      timedOut = true;
      terminate();
    }, timeoutMillis);
    signal?.addEventListener('abort', terminate, { once: true });
    log.on('error', (error) => { terminate(); rejectRun(error); });
    child.on('error', (error) => {
      clearTimeout(timer);
      signal?.removeEventListener('abort', terminate);
      log.end();
      rejectRun(error);
    });
    child.on('close', async (code, closeSignal) => {
      clearTimeout(timer);
      signal?.removeEventListener('abort', terminate);
      log.end();
      try {
        if (code !== 0 || closeSignal || timedOut) throw new Error(`${argv[0]} exited ${code ?? closeSignal}; see ${logFile}`);
        resolveRun();
      } catch (error) { rejectRun(error); }
    });
  });
}

export function verifyEvidence({ metrics, observations, rawSummary, phase, workload, window, summary }) {
  if (!metrics || !observations || !rawSummary?.metrics) throw new Error('missing raw capacity evidence');
  const counters = workload === 'connection'
    ? ['im_connection_planned', 'im_connection_attempted', 'im_connection_opened',
      'im_connection_measurement_online']
    : ['im_message_sends', 'im_message_send_successes',
      'im_message_expected_deliveries', 'im_message_delivery_ack_successes'];
  for (const name of counters) {
    if (!Number.isFinite(rawSummary.metrics[name]?.values?.count)) {
      throw new Error(`raw k6 counter is missing: ${name}`);
    }
  }
  if (rawSummary.metrics[counters[0]].values.count <= 0) throw new Error('no measured pressure');
  if (workload === 'connection') {
    const planned = rawSummary.metrics.im_connection_planned.values.count;
    const opened = rawSummary.metrics.im_connection_opened.values.count;
    const online = rawSummary.metrics.im_connection_measurement_online.values.count;
    const expected = Math.min(opened / planned, online / planned);
    if (Math.abs(metrics.connectionSuccessRate - expected) > 1e-9) {
      throw new Error('connection success rate must include measurement-end online rate');
    }
  } else if (summary) {
    const counts = rawSummary.metrics;
    const sends = counts.im_message_sends.values.count;
    const expectedDeliveries = counts.im_message_expected_deliveries.values.count;
    if (sends <= 0 || expectedDeliveries <= 0) throw new Error('message pressure has no valid denominator');
    const rateChecks = {
      businessSuccessRate: counts.im_message_send_successes.values.count / sends,
      ackSuccessRate: counts.im_message_delivery_ack_successes.values.count / expectedDeliveries,
      receiveLatencyP95Ms: summary.metrics?.receiveLatencyP95Ms,
      receiveLatencyP99Ms: summary.metrics?.receiveLatencyP99Ms,
    };
    for (const [name, expected] of Object.entries(rateChecks)) {
      if (!Number.isFinite(expected) || Math.abs(metrics[name] - expected) > 1e-9) {
        throw new Error(`capacity metric disagrees with k6: ${name}`);
      }
    }
  }
  for (const name of requiredMetrics) {
    if (!Number.isFinite(metrics[name]) || metrics[name] < 0) throw new Error(`missing metric: ${name}`);
    const observation = observations[name];
    if (!applicable(name, phase, workload)) {
      if (metrics[name] !== 0 || observation?.applicable !== false) {
        throw new Error(`invalid non-applicable metric: ${name}`);
      }
      continue;
    }
    if (observation?.applicable === false || !Number.isInteger(observation?.sampleCount)
        || observation.sampleCount <= 0 || !observation.source || !observation.rawFile
        || !Number.isFinite(observation.startedAt) || !Number.isFinite(observation.finishedAt)
        || observation.startedAt > window.startedAt || observation.finishedAt < window.finishedAt) {
      throw new Error(`incomplete observation: ${name}`);
    }
  }
  if (typeof metrics.generatorSaturated !== 'boolean') throw new Error('generator saturation is missing');
  if (typeof metrics.operationOutcome !== 'string') throw new Error('operation outcome is missing');
}

export function classify(metrics, thresholds, workload, phase) {
  const checks = [metrics.duplicateRate <= thresholds.duplicateRateMax,
    metrics.misrouteCount <= thresholds.misrouteCountMax,
    metrics.oldEpochAdmissionCount <= thresholds.oldEpochAdmissionCountMax,
    metrics.routeLookupP95Ms <= thresholds.routeLookupP95MsMax,
    metrics.routeLookupP99Ms <= thresholds.routeLookupP99MsMax,
    metrics.fullLookupRate <= thresholds.fullLookupRateMax,
    metrics.snapshotAckRate >= thresholds.snapshotAckRateMin,
    metrics.staleRouteCount <= thresholds.staleRouteCountMax,
    metrics.cpuMaxPercent <= thresholds.cpuLimitPercentMax,
    metrics.rssLimitPercent <= thresholds.rssLimitPercentMax,
    metrics.diskUsagePercent <= thresholds.diskUsagePercentMax,
    !faultPhases.has(phase) || metrics.operationOutcome === 'SUCCEEDED'];
  if (workload === 'connection') checks.push(
    metrics.connectionSuccessRate >= thresholds.connectionSuccessRateMin);
  else checks.push(metrics.businessSuccessRate >= thresholds.businessSuccessRateMin,
    metrics.ackSuccessRate >= thresholds.ackSuccessRateMin,
    metrics.receiveLatencyP95Ms <= thresholds.receiveLatencyP95MsMax,
    metrics.receiveLatencyP99Ms <= thresholds.receiveLatencyP99MsMax);
  return metrics.generatorSaturated ? 'uncertain' : checks.every(Boolean) ? 'stable' : 'service_failed';
}

export async function main(env = process.env) {
  const cancellation = new AbortController();
  const cancel = () => cancellation.abort(new Error('capacity run cancelled'));
  process.once('SIGTERM', cancel);
  process.once('SIGINT', cancel);
  try { await run(env, cancellation.signal); } finally {
    process.removeListener('SIGTERM', cancel);
    process.removeListener('SIGINT', cancel);
  }
}

async function run(env, signal) {
  const action = required(env.ROUTING_CAPACITY_ACTION, 'action');
  const environmentFile = required(env.ROUTING_CAPACITY_ENV_CONFIG, 'environment config');
  const environment = await json(environmentFile);
  const config = await json(required(env.ROUTING_CAPACITY_CONFIG, 'capacity config'));
  const topology = required(env.ROUTING_CAPACITY_TOPOLOGY_ID, 'topology');
  if (environment.environmentKind !== 'production-candidate'
      || environment.topologyId !== topology
      || !config.candidateTopologies.some((candidate) => candidate.id === topology)) {
    throw new Error('production-candidate environment/topology mismatch');
  }
  const stateDir = resolve(required(env.ROUTING_CAPACITY_STATE_DIR, 'state directory'));
  await mkdir(stateDir, { recursive: true, mode: 0o700 });
  await chmod(stateDir, 0o700);
  const baselineFile = join(stateDir, 'ordinary-data-baseline.json');
  const hook = async (name, variables, output) => {
    const commandLine = required(environment.hooks?.[name], `hook ${name}`);
    await command(commandLine, { ...variables, ROUTING_CAPACITY_HOOK_OUTPUT: output,
      ROUTING_CAPACITY_STATE_DIR: stateDir }, 180_000, `${output}.log`,
    signal);
    const result = await json(output);
    await chmod(output, 0o600);
    if (['restore', 'cleanup'].includes(name) && result.status !== 'passed') {
      throw new Error(`${name} hook did not report passed`);
    }
    return result;
  };
  if (action === 'cleanup') {
    const report = resolve(required(env.ROUTING_CAPACITY_OUTPUT, 'output'));
    const baseline = await json(baselineFile);
    const failures = [];
    for (const entry of await readdir(stateDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const round = join(stateDir, entry.name);
      let journal;
      try { journal = await json(join(round, 'journal.json')); } catch (error) {
        if (error.code === 'ENOENT') continue;
        throw error;
      }
      const variables = { ROUTING_CAPACITY_ACTION: 'cleanup',
        ROUTING_CAPACITY_BATCH_ID: journal.batchId, ROUTING_CAPACITY_RUN_ID: journal.runId };
      for (const step of ['restore', 'cleanup']) {
        try { await hook(step, variables, join(round, `final-${step}.json`)); }
        catch (error) { failures.push(error.message); }
      }
    }
    const after = await hook('inspect', { ROUTING_CAPACITY_ACTION: 'cleanup' }, join(stateDir, 'final-inspect.json'));
    const unchanged = JSON.stringify(after.ordinaryData) === JSON.stringify(baseline.ordinaryData);
    await save(report, { status: unchanged && failures.length === 0 ? 'passed' : 'failed',
      ordinaryDataUnchanged: unchanged, failures });
    if (!unchanged || failures.length) throw new Error('capacity cleanup failed; retained journal permits retry');
    return;
  }
  if (action !== 'run') throw new Error(`unsupported action: ${action}`);
  const phase = required(env.ROUTING_CAPACITY_PHASE, 'phase');
  const repetition = Number(required(env.ROUTING_CAPACITY_REPETITION, 'repetition'));
  const workload = required(env.ROUTING_CAPACITY_WORKLOAD, 'workload');
  if (!config.phases.some((entry) => entry.id === phase) || ![1, 2, 3].includes(repetition)
      || !['connection', 'message'].includes(workload)) throw new Error('invalid matrix entry');
  const level = environment.targetLevels?.[workload];
  if (!config.levels[workload].includes(level)) throw new Error('target level is not configured');
  if (!environment.websocketUrl || !environment.k6) throw new Error('k6 and WebSocket URL are required');
  if (!Number.isInteger(environment.messageConnections) || environment.messageConnections < 10
      || environment.messageConnections % 10 !== 0) throw new Error('message connections must contain complete cohorts');
  try { await json(baselineFile); } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    const initial = await hook('inspect', { ROUTING_CAPACITY_ACTION: 'baseline' }, baselineFile);
    if (initial.ordinaryData === undefined || !initial.topology || !Array.isArray(initial.nodes)
        || initial.nodes.length === 0 || initial.nodes.some((node) => !node.id || !node.resources)) {
      throw new Error('inspect must return ordinary data and deployed topology');
    }
    const expected = config.candidateTopologies.find((candidate) => candidate.id === topology);
    if (initial.topology.voters !== expected.voters || initial.topology.brokers !== expected.brokers
        || initial.topology.gateways !== expected.gateways) throw new Error('deployed topology mismatch');
  }
  const runId = `${phase}-${repetition}-${workload}`;
  const batchId = randomBytes(3).toString('hex');
  const round = join(stateDir, `${runId}-${batchId}`);
  await mkdir(round, { recursive: true, mode: 0o700 });
  await save(join(round, 'journal.json'), { runId, batchId, phase, repetition, workload, status: 'prepared-intent' });
  const variables = { ROUTING_CAPACITY_RUN_ID: runId, ROUTING_CAPACITY_BATCH_ID: batchId,
    ROUTING_CAPACITY_PHASE: phase, ROUTING_CAPACITY_REPETITION: `${repetition}`,
    ROUTING_CAPACITY_WORKLOAD: workload, ROUTING_CAPACITY_LEVEL: `${level}` };
  let primaryFailure;
  try {
    const prepared = await hook('prepare', variables, join(round, 'prepared.json'));
    if (prepared.batchId !== batchId) throw new Error('prepared batch identity mismatch');
    for (const field of ['manifestFile', 'credentialFile']) required(prepared[field], field);
    const startMs = Date.now() + 3000;
    const warmup = config.windows.warmupSeconds;
    const measure = config.windows.measureSeconds;
    const drain = config.windows.drainSeconds;
    const ramp = workload === 'connection' ? level / 20 : 0;
    const measureStartMs = startMs + (warmup + ramp) * 1000;
    const measureEndMs = measureStartMs + measure * 1000;
    const summaryFile = join(round, 'summary.json');
    const rawFile = join(round, 'raw-k6.json');
    const k6Env = { ...variables, BATCH_ID: batchId, ROUND_ID: runId,
      ROUND_NUMBER: `${repetition}`, GROUP_SIZE: `${config.groupSize}`,
      EFFECTIVE_USERS: `${workload === 'connection' ? level : environment.messageConnections}`,
      REQUESTED_USERS: `${level}`, TARGET_RATE: `${level}`, CONNECTION_RATE: '20',
      MESSAGE_BYTES: `${config.messageBytes}`, STABLE_SECONDS: `${warmup}`,
      WARMUP_SECONDS: `${warmup}`, MEASURE_SECONDS: `${measure}`,
      DISCONNECT_SECONDS: `${drain}`, DRAIN_SECONDS: `${drain}`,
      ROUND_START_MS: `${startMs}`, MANIFEST_FILE: prepared.manifestFile,
      CREDENTIAL_FILE: prepared.credentialFile, SUMMARY_PATH: summaryFile,
      WEBSOCKET_URL: environment.websocketUrl };
    const script = resolve(env.ROUTING_CAPACITY_ROOT_DIR || '.', 'im-test/im-load-test/scenarios',
      workload === 'connection' ? 'connection-capacity.js' : 'message-throughput.js');
    const k6 = command([environment.k6, 'run', '--quiet', '--no-usage-report',
      '--summary-export', rawFile, script], k6Env,
    (warmup + ramp + measure + drain + 30) * 1000, join(round, 'k6.log'), signal);
    const fault = faultPhases.has(phase)
      ? delay(Math.max(0, measureStartMs - Date.now()), undefined, { signal }).then(() =>
        hook('fault', { ...variables, ROUTING_CAPACITY_MEASURE_START: `${measureStartMs}`,
          ROUTING_CAPACITY_MEASURE_END: `${measureEndMs}` }, join(round, 'fault.json'))
      )
      : Promise.resolve({ status: 'passed', operationOutcome: 'NOT_APPLICABLE' });
    const [k6Result, faultResult] = await Promise.allSettled([k6, fault]);
    if (k6Result.status === 'rejected') throw k6Result.reason;
    if (faultResult.status === 'rejected') throw faultResult.reason;
    const outcome = faultResult.value;
    if (faultPhases.has(phase) && (outcome.status !== 'passed'
        || outcome.operationOutcome !== 'SUCCEEDED'
        || !Number.isFinite(outcome.startedAt) || !Number.isFinite(outcome.finishedAt)
        || outcome.startedAt < measureStartMs || outcome.finishedAt > measureEndMs
        || !outcome.operationEvidenceFile)) throw new Error('fault did not finish within measurement window');
    const measured = await hook('metrics', { ...variables,
      ROUTING_CAPACITY_SUMMARY: summaryFile, ROUTING_CAPACITY_RAW_K6: rawFile,
      ROUTING_CAPACITY_MEASURE_START: `${measureStartMs}`,
      ROUTING_CAPACITY_MEASURE_END: `${measureEndMs}` }, join(round, 'metrics.json'));
    const summary = await json(summaryFile);
    const rawSummary = await json(rawFile);
    if (summary.input?.batchId !== batchId || summary.input?.roundId !== runId) {
      throw new Error('k6 summary identity mismatch');
    }
    verifyEvidence({ metrics: measured.metrics, observations: measured.observations,
      rawSummary, summary, phase, workload, window: { startedAt: measureStartMs, finishedAt: measureEndMs } });
    const pressureEffective = workload === 'connection'
      ? rawSummary.metrics.im_connection_attempted.values.count / level >= 0.995
      : summary.metrics?.actualTargetRate >= 0.95 && summary.metrics?.actualTargetRate <= 1.05;
    if (!pressureEffective || measured.pressureEffective !== true) throw new Error('ineffective k6 pressure');
    for (const [name, observation] of Object.entries(measured.observations)) {
      if (requiredMetrics.includes(name) && applicable(name, phase, workload)) {
        const observationPath = resolve(round, observation.rawFile);
        if (!observationPath.startsWith(`${round}/`)) throw new Error('observation escapes round directory');
        if ((await readFile(observationPath)).length === 0) {
          throw new Error(`empty raw observation: ${name}`);
        }
        if (resourceMetrics.has(name)) {
          const raw = await json(observationPath);
          const nodes = (await json(baselineFile)).nodes.map((node) => node.id);
          const value = resourceValue(name, raw.samples, nodes,
            { startedAt: measureStartMs, finishedAt: measureEndMs });
          if (Math.abs(value - measured.metrics[name]) > 1e-9) {
            throw new Error(`resource metric disagrees with raw samples: ${name}`);
          }
        }
      }
    }
    if (faultPhases.has(phase) && (await readFile(outcome.operationEvidenceFile)).length === 0) {
      throw new Error('fault operation evidence is empty');
    }
    if (workload === 'message' && (summary.metrics?.sends <= 0
        || summary.metrics?.expectedDeliveries <= 0)) throw new Error('message summary has no pressure');
    const status = classify(measured.metrics, config.thresholds, workload, phase);
    if (status === 'uncertain' && (!Array.isArray(measured.uncertaintyReasons)
        || measured.uncertaintyReasons.length === 0)) throw new Error('saturation reasons are missing');
    await save(resolve(required(env.ROUTING_CAPACITY_OUTPUT, 'output')), {
      runId, phase, repetition, workload, topologyId: topology, level, status,
      pressureEffective: measured.pressureEffective === true, metrics: measured.metrics,
      environmentKind: environment.environmentKind,
      environmentDigest: createHash('sha256').update(await readFile(environmentFile)).digest('hex'),
      driverVersion: 1,
      deployedNodes: (await json(baselineFile)).nodes,
      evidenceDirectory: round,
      uncertaintyReasons: measured.uncertaintyReasons,
      observations: measured.observations,
    });
  } catch (error) { primaryFailure = error; }
  try {
    await hook('restore', variables, join(round, 'restore.json'));
  } catch (error) { primaryFailure ??= error; }
  if (primaryFailure) throw primaryFailure;
}

if (process.argv[1] && import.meta.url === new URL(`file://${resolve(process.argv[1])}`).href) {
  main().catch((error) => { process.stderr.write(`${error.stack || error}\n`); process.exitCode = 1; });
}
