import { readFile } from 'node:fs/promises';

import { classifyConnectionRun } from '../lib/connection-state.js';

const args = parseArgs(process.argv.slice(2));
const defaults = JSON.parse(await readFile(new URL('../config/defaults.json', import.meta.url), 'utf8'));
const summary = JSON.parse(await readFile(args.summary, 'utf8'));
const response = JSON.parse(await readFile(args.evidence, 'utf8'));
if (response?.code !== 0 || response.data?.boundary !== 'mock-runtime') {
  throw new Error('mock evidence response is invalid');
}
if (summary.input?.requestedUsers !== args.requested
    || summary.input?.effectiveUsers !== args.effective
    || summary.input?.groupSize !== args.groupSize) {
  throw new Error('connection summary input does not match the fixture');
}

const decision = classifyConnectionRun({ summary, evidence: response.data }, {
  thresholds: defaults.thresholds,
  pressureEvidence: { complete: true, boundary: 'mock-fixture' },
});
if (decision.status !== args.expected) {
  throw new Error(`expected ${args.expected}, got ${decision.status}: ${decision.reasons.join('; ')}`);
}
process.stdout.write(`${JSON.stringify({
  boundary: 'mock-connection-fixture',
  requestedUsers: args.requested,
  effectiveUsers: args.effective,
  decision,
})}\n`);

function parseArgs(argv) {
  const allowed = new Set(['--summary', '--evidence', '--requested', '--effective', '--group-size', '--expected']);
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
  return {
    summary: values['--summary'],
    evidence: values['--evidence'],
    requested: positiveInteger(values['--requested'], '--requested'),
    effective: positiveInteger(values['--effective'], '--effective'),
    groupSize: positiveInteger(values['--group-size'], '--group-size'),
    expected: values['--expected'],
  };
}

function positiveInteger(raw, name) {
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}
