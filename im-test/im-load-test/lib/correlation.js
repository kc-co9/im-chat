const DEFAULT_MESSAGE_BYTES = 256;
const BATCH_WIDTH = 32;
const ROUND_WIDTH = 6;
const PHASE_WIDTH = 7;
const SEQUENCE_WIDTH = 19;
const PHASES = new Set(['warmup', 'measure']);
const HEADER_PATTERN = new RegExp(
  `^IMLT1\\|batch=(.{${BATCH_WIDTH}})\\|round=(\\d{${ROUND_WIDTH}})`
  + `\\|phase=(.{${PHASE_WIDTH}})\\|sequence=(\\d{${SEQUENCE_WIDTH}})\\|padding=(\\.*)$`,
);

export function buildCorrelation({
  batch,
  round,
  phase,
  sequence,
  messageBytes = DEFAULT_MESSAGE_BYTES,
}) {
  if (typeof batch !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(batch)
      || batch.length > BATCH_WIDTH) {
    throw new Error(`batch must be 1-${BATCH_WIDTH} printable ASCII identity characters`);
  }
  if (!PHASES.has(phase)) {
    throw new Error('phase must be warmup or measure');
  }
  const roundField = fixedDecimal(round, ROUND_WIDTH, 'round');
  const sequenceField = fixedDecimal(sequence, SEQUENCE_WIDTH, 'sequence');
  if (!Number.isInteger(messageBytes) || messageBytes <= 0) {
    throw new Error('messageBytes must be a positive integer');
  }

  const header = `IMLT1|batch=${batch.padEnd(BATCH_WIDTH, ' ')}`
    + `|round=${roundField}|phase=${phase.padEnd(PHASE_WIDTH, ' ')}`
    + `|sequence=${sequenceField}|padding=`;
  const headerBytes = utf8Length(header);
  if (headerBytes > messageBytes) {
    throw new Error(`messageBytes ${messageBytes} is too small for the correlation header`);
  }
  return header + '.'.repeat(messageBytes - headerBytes);
}

export function parseCorrelation(text, { messageBytes = DEFAULT_MESSAGE_BYTES } = {}) {
  if (typeof text !== 'string' || utf8Length(text) !== messageBytes) {
    throw new Error(`correlation text must be exactly ${messageBytes} UTF-8 bytes`);
  }
  const match = HEADER_PATTERN.exec(text);
  if (!match) {
    throw new Error('malformed correlation text');
  }
  const [, batchField, roundField, phaseField, sequenceField] = match;
  const batch = batchField.trimEnd();
  const phase = phaseField.trimEnd();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(batch) || !PHASES.has(phase)) {
    throw new Error('malformed correlation identity or phase');
  }
  return {
    batch,
    round: canonicalFromFixed(roundField),
    phase,
    sequence: canonicalFromFixed(sequenceField),
  };
}

function fixedDecimal(value, width, name) {
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)$/.test(value) || value.length > width) {
    throw new Error(`${name} must be a canonical decimal string up to ${width} digits`);
  }
  return value.padStart(width, '0');
}

function canonicalFromFixed(value) {
  return value.replace(/^0+(?=\d)/, '');
}

function utf8Length(value) {
  return new TextEncoder().encode(value).length;
}
