import { classifyRound } from './round-result.js';

export function evaluateLoadGenerator({ samples, actualTargetRate, sendExceptions, thresholds } = {}) {
  const uncertain = [];
  const failed = [];
  if (!validThresholds(thresholds)) {
    return result('uncertain', ['generator thresholds are invalid'], false, 0, 0);
  }
  const minimumSamples = Math.max(
    thresholds.hostMemoryConsecutiveSamples,
    thresholds.generatorCpuConsecutiveSamples,
  );
  if (!Array.isArray(samples) || samples.length < minimumSamples
      || samples.some((sample) => !validSample(sample))
      || !usesInterval(samples, thresholds.sampleSeconds * 1_000)) {
    return result(
      'uncertain',
      ['generator resource samples are missing or invalid'],
      false,
      thresholds.sampleSeconds,
      Array.isArray(samples) ? samples.length : 0,
    );
  }
  if (typeof actualTargetRate !== 'number' || !Number.isFinite(actualTargetRate)
      || actualTargetRate < thresholds.actualTargetRateMin
      || actualTargetRate > thresholds.actualTargetRateMax) {
    uncertain.push('actual target rate is outside the valid range');
  }
  if (hasConsecutive(
    samples,
    thresholds.hostMemoryConsecutiveSamples,
    ({ availableMemoryBytes }) => availableMemoryBytes < thresholds.hostAvailableMemoryBytesMin,
  )) {
    uncertain.push('host memory is saturated');
  }
  if (actualTargetRate < thresholds.actualTargetRateMin && hasConsecutive(
    samples,
    thresholds.generatorCpuConsecutiveSamples,
    ({ cpuPercent }) => cpuPercent >= thresholds.generatorCpuPercentMin,
  )) {
    uncertain.push('generator CPU is saturated while target rate is not reached');
  }
  if (!validSendExceptions(sendExceptions)) {
    uncertain.push('send exception evidence is invalid');
  } else if (sendExceptions.count > 0) {
    if (sendExceptions.origin === 'service_send') {
      failed.push('service send exceptions were observed');
    } else {
      uncertain.push('local generator send exceptions were observed');
    }
  }

  const status = uncertain.length > 0 ? 'uncertain' : failed.length > 0 ? 'service_failed' : 'stable';
  return result(
    status,
    status === 'uncertain' ? uncertain : failed,
    true,
    thresholds.sampleSeconds,
    samples.length,
  );
}

export function classifyMessageRun({
  summary,
  samples,
  sendExceptionOrigin,
  thresholds,
} = {}) {
  const metrics = summary?.metrics;
  if (summary?.boundary !== 'k6-message' || metrics === null || typeof metrics !== 'object') {
    return {
      generatorHealth: { status: 'uncertain', reasons: ['message summary is invalid'] },
      decision: { status: 'uncertain', reasons: ['message summary is invalid'] },
    };
  }
  const sendExceptions = {
    count: metrics.sendExceptions,
    origin: metrics.sendExceptions === 0 ? 'none' : sendExceptionOrigin,
  };
  const generatorThresholds = {
    sampleSeconds: thresholds.generatorSampleSeconds,
    actualTargetRateMin: thresholds.actualTargetRateMin,
    actualTargetRateMax: thresholds.actualTargetRateMax,
    hostAvailableMemoryBytesMin: thresholds.hostAvailableMemoryBytesMin,
    hostMemoryConsecutiveSamples: thresholds.hostMemoryConsecutiveSamples,
    generatorCpuPercentMin: thresholds.generatorCpuPercentMin,
    generatorCpuConsecutiveSamples: thresholds.generatorCpuConsecutiveSamples,
  };
  const generatorHealth = evaluateLoadGenerator({
    samples,
    actualTargetRate: metrics.actualTargetRate,
    sendExceptions,
    thresholds: generatorThresholds,
  });
  const generator = {
    sampleSeconds: thresholds.generatorSampleSeconds,
    cpuPercent: Array.isArray(samples) ? samples.map(({ cpuPercent }) => cpuPercent) : [],
    rssBytes: Array.isArray(samples) ? samples.map(({ rssBytes }) => rssBytes) : [],
    availableMemoryBytes: Array.isArray(samples)
      ? samples.map(({ availableMemoryBytes }) => availableMemoryBytes)
      : [],
    sendExceptions,
  };
  const roundDecision = classifyRound({ kind: 'message', metrics, generator }, thresholds);
  let decision = roundDecision;
  if (generatorHealth.status === 'uncertain' && roundDecision.status !== 'service_failed') {
    decision = {
      status: 'uncertain',
      reasons: [...new Set([
        ...generatorHealth.reasons,
        ...(roundDecision.status === 'uncertain' ? roundDecision.reasons : []),
      ])],
    };
  } else if (generatorHealth.status === 'service_failed' && roundDecision.status === 'stable') {
    decision = { status: 'service_failed', reasons: [...generatorHealth.reasons] };
  }
  return { generatorHealth, decision };
}

function result(status, reasons, complete, sampleSeconds, sampleCount) {
  return {
    status,
    reasons,
    evidence: { complete, sampleSeconds, sampleCount },
  };
}

function validThresholds(value) {
  return value !== null && typeof value === 'object'
    && Number.isInteger(value.sampleSeconds) && value.sampleSeconds > 0
    && finite(value.actualTargetRateMin) && value.actualTargetRateMin > 0
    && finite(value.actualTargetRateMax) && value.actualTargetRateMax >= value.actualTargetRateMin
    && finite(value.hostAvailableMemoryBytesMin) && value.hostAvailableMemoryBytesMin >= 0
    && Number.isInteger(value.hostMemoryConsecutiveSamples) && value.hostMemoryConsecutiveSamples > 0
    && finite(value.generatorCpuPercentMin) && value.generatorCpuPercentMin >= 0
    && value.generatorCpuPercentMin <= 100
    && Number.isInteger(value.generatorCpuConsecutiveSamples) && value.generatorCpuConsecutiveSamples > 0;
}

function validSample(sample) {
  return sample !== null && typeof sample === 'object'
    && finite(sample.atMs) && sample.atMs >= 0
    && finite(sample.cpuPercent) && sample.cpuPercent >= 0 && sample.cpuPercent <= 100
    && finite(sample.rssBytes) && sample.rssBytes >= 0
    && finite(sample.availableMemoryBytes) && sample.availableMemoryBytes >= 0;
}

function usesInterval(samples, intervalMs) {
  const elapsedSamples = [];
  for (let index = 1; index < samples.length; index += 1) {
    const elapsed = samples[index].atMs - samples[index - 1].atMs;
    if (elapsed <= intervalMs * 0.5 || elapsed > intervalMs * 1.5) return false;
    elapsedSamples.push(elapsed);
  }
  elapsedSamples.sort((left, right) => left - right);
  const middle = Math.floor(elapsedSamples.length / 2);
  const median = elapsedSamples.length % 2 === 0
    ? (elapsedSamples[middle - 1] + elapsedSamples[middle]) / 2
    : elapsedSamples[middle];
  return median > intervalMs * 0.75 && median <= intervalMs * 1.25;
}

function hasConsecutive(values, count, predicate) {
  let consecutive = 0;
  for (const value of values) {
    consecutive = predicate(value) ? consecutive + 1 : 0;
    if (consecutive >= count) return true;
  }
  return false;
}

function validSendExceptions(value) {
  return value !== null && typeof value === 'object'
    && Number.isInteger(value.count) && value.count >= 0
    && ['none', 'service_send', 'generator'].includes(value.origin)
    && (value.count > 0 || value.origin === 'none');
}

function finite(value) {
  return typeof value === 'number' && Number.isFinite(value);
}
