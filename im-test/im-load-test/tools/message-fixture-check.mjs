import { readFile } from 'node:fs/promises';

import { classifyMessageRun } from '../lib/load-generator-health.js';

const args = parseArgs(process.argv.slice(2));
const summary = JSON.parse(await readFile(args.summary, 'utf8'));
const response = JSON.parse(await readFile(args.evidence, 'utf8'));
const defaults = JSON.parse(await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'));
if (response?.code !== 0 || response.data?.boundary !== 'mock-runtime') {
  throw new Error('mock message evidence is invalid');
}
verifyExactFixture(args.mode, summary.metrics, response.data);
const availableMemoryBytes = args.generator === 'saturated' ? 1_000_000_000 : 2_000_000_000;
const samples = [0, 5_000, 10_000].map((atMs) => ({
  atMs,
  cpuPercent: 30,
  rssBytes: 100_000_000,
  availableMemoryBytes,
}));
const result = classifyMessageRun({
  summary,
  samples,
  sendExceptionOrigin: 'generator',
  thresholds: { ...defaults.thresholds, minimumSends: 20 },
});
if (result.decision.status !== args.expected) {
  throw new Error(`expected ${args.expected}, got ${result.decision.status}: ${result.decision.reasons.join('; ')}`);
}
process.stdout.write(`${JSON.stringify({
  boundary: 'mock-message-fixture',
  mode: args.mode,
  generator: args.generator,
  decision: result.decision,
})}\n`);

function verifyExactFixture(mode, metrics, evidence) {
  const expected = {
    normal: [20, 68, 0, 0, 1, 0, 68, 68, 0],
    duplicate: [20, 68, 1, 0, 1, 0, 69, 68, 0],
    missing: [20, 67, 0, 0, 1, 1, 67, 67, 0],
    unknown: [20, 68, 0, 1, 1, 0, 69, 68, 0],
    'ack-failure': [20, 67, 0, 0, 1, 1, 68, 67, 1],
    'send-timeout': [19, 67, 0, 0, 1, 1, 67, 67, 0],
    'send-busy': [19, 59, 0, 0, 1, 9, 59, 59, 0],
    'send-repeat': [19, 59, 0, 0, 1, 9, 59, 59, 0],
    'response-after-push': [20, 68, 0, 0, 1, 0, 68, 68, 0],
  }[mode];
  if (expected === undefined) throw new Error(`unsupported message fixture mode ${mode}`);
  const actual = [
    metrics.sendSuccesses,
    metrics.deliveryAckSuccesses,
    metrics.duplicates,
    metrics.unknownReceivers,
    metrics.actualTargetRate,
    metrics.drainResidual,
    evidence.messageDeliveries.count,
    evidence.messageAcks.count,
    evidence.messageAckFailures.count,
  ];
  if (metrics.sends !== 20 || metrics.expectedDeliveries !== 68
      || metrics.minuteBacklog.length !== 3 || metrics.postDrainExcess !== 0
      || metrics.sendExceptions !== 0 || evidence.messageSends.count !== 20
      || metrics.trackingOverflows !== 0
      || (mode === 'response-after-push' && metrics.receiveLatencyP95Ms >= 500)
      || (mode === 'response-after-push' && metrics.sendResponseLatencyP99Ms < 400)
      || JSON.stringify(sendFailureDiagnostics(metrics)) !== JSON.stringify(expectedFailureDiagnostics(mode))
      || JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`message fixture counts differ: ${JSON.stringify({ mode, metrics, evidence })}`);
  }
}

function sendFailureDiagnostics(metrics) {
  return [
    metrics.sendFailuresPrivate,
    metrics.sendFailuresGroup,
    metrics.sendBusyFailures,
    metrics.sendRepeatFailures,
    metrics.sendOtherBusinessFailures,
    metrics.sendResponseTimeouts,
  ];
}

function expectedFailureDiagnostics(mode) {
  if (mode === 'send-timeout') return [1, 0, 0, 0, 0, 1];
  if (mode === 'send-busy') return [0, 1, 1, 0, 0, 0];
  if (mode === 'send-repeat') return [0, 1, 0, 1, 0, 0];
  return [0, 0, 0, 0, 0, 0];
}

function parseArgs(argv) {
  const allowed = new Set(['--summary', '--evidence', '--mode', '--generator', '--expected']);
  const values = {};
  for (let index = 0; index < argv.length; index += 2) {
    const name = argv[index];
    const value = argv[index + 1];
    if (!allowed.has(name)) throw new Error(`unknown option ${name}`);
    if (value === undefined || value.startsWith('--')) throw new Error(`missing value for ${name}`);
    if (Object.hasOwn(values, name)) throw new Error(`duplicate option ${name}`);
    values[name] = value;
  }
  for (const name of allowed) {
    if (values[name] === undefined) throw new Error(`${name} is required`);
  }
  if (!['healthy', 'saturated'].includes(values['--generator'])) throw new Error('generator mode is invalid');
  return {
    summary: values['--summary'],
    evidence: values['--evidence'],
    mode: values['--mode'],
    generator: values['--generator'],
    expected: values['--expected'],
  };
}
