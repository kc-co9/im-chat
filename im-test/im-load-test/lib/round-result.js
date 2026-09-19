const STABLE = 'stable';
const SERVICE_FAILED = 'service_failed';
const UNCERTAIN = 'uncertain';
const REQUIRED_THRESHOLDS = Object.freeze([
  'lateSlotMs',
  'actualTargetRateMin',
  'actualTargetRateMax',
  'skipRateMax',
  'scheduleP95MsMax',
  'connectionSuccessRateMin',
  'onlineRateMin',
  'unexpectedDisconnectRateMax',
  'sendSuccessRateMin',
  'deliveryAckSuccessRateMin',
  'receiveLatencyP95Ms',
  'receiveLatencyP99Ms',
  'unknownReceiverMax',
  'duplicateRateMax',
  'minimumSends',
  'drainResidualRateMax',
  'hostAvailableMemoryBytesMin',
  'hostMemoryConsecutiveSamples',
  'generatorCpuPercentMin',
  'generatorCpuConsecutiveSamples',
  'generatorSampleSeconds',
]);
const FRACTION_THRESHOLDS = Object.freeze([
  'skipRateMax',
  'connectionSuccessRateMin',
  'onlineRateMin',
  'unexpectedDisconnectRateMax',
  'sendSuccessRateMin',
  'deliveryAckSuccessRateMin',
  'duplicateRateMax',
  'drainResidualRateMax',
]);
const POSITIVE_INTEGER_THRESHOLDS = Object.freeze([
  'minimumSends',
  'hostMemoryConsecutiveSamples',
  'generatorCpuConsecutiveSamples',
  'generatorSampleSeconds',
]);

export function classifyRound(round, thresholds) {
  const uncertain = [];
  const failed = [];
  if (!isPlainObject(round) || !isPlainObject(thresholds) || !isPlainObject(round.metrics)) {
    return result(UNCERTAIN, ['round metrics or thresholds are missing']);
  }
  validateThresholds(thresholds, uncertain);
  if (uncertain.length > 0) {
    return result(UNCERTAIN, uncertain);
  }

  evaluateGenerator(round.generator, round.metrics, thresholds, uncertain, failed);
  evaluateCommonValidity(round.metrics, thresholds, uncertain);

  if (round.kind === 'connection') {
    evaluateConnection(round.metrics, thresholds, uncertain, failed);
  } else if (round.kind === 'message') {
    evaluateMessage(round.metrics, thresholds, uncertain, failed);
  } else {
    uncertain.push(`unsupported round kind: ${round.kind}`);
  }

  if (uncertain.length > 0) {
    return result(UNCERTAIN, uncertain);
  }
  if (failed.length > 0) {
    return result(SERVICE_FAILED, failed);
  }
  return result(STABLE, []);
}

function evaluateGenerator(generator, metrics, thresholds, uncertain, failed) {
  const minimumMemorySamples = thresholds.hostMemoryConsecutiveSamples;
  const minimumCpuSamples = thresholds.generatorCpuConsecutiveSamples;
  if (!isPlainObject(generator)
      || generator.sampleSeconds !== thresholds.generatorSampleSeconds
      || !hasFiniteSamples(generator.cpuPercent, minimumCpuSamples)
      || !hasFiniteSamples(generator.rssBytes, minimumCpuSamples)
      || !hasFiniteSamples(generator.availableMemoryBytes, minimumMemorySamples)
      || !validSendExceptionEvidence(generator.sendExceptions)) {
    uncertain.push('generator evidence is missing or does not use 5-second samples');
    return;
  }
  if (generator.rssBytes.some((value) => value < 0)
      || generator.availableMemoryBytes.some((value) => value < 0)
      || generator.cpuPercent.some((value) => value < 0 || value > 100)) {
    uncertain.push('generator evidence contains out-of-range samples');
  }
  if (hasConsecutive(generator.availableMemoryBytes, minimumMemorySamples,
    (value) => value < thresholds.hostAvailableMemoryBytesMin)) {
    uncertain.push('generator host available memory is saturated');
  }
  if (isFiniteNumber(metrics.actualTargetRate)
      && metrics.actualTargetRate < thresholds.actualTargetRateMin
      && hasConsecutive(generator.cpuPercent, minimumCpuSamples,
        (value) => value >= thresholds.generatorCpuPercentMin)) {
    uncertain.push('generator CPU is saturated while actual rate is below target');
  }
  if (generator.sendExceptions.count > 0) {
    if (generator.sendExceptions.origin === 'service_send') {
      failed.push('service send exceptions were observed');
    } else {
      uncertain.push('local generator send exceptions were observed');
    }
  }
}

function evaluateCommonValidity(metrics, thresholds, uncertain) {
  if (!requireFiniteNonNegative(
    metrics,
    ['actualTargetRate', 'skipRate', 'scheduleP95Ms'],
    uncertain,
  )) {
    return;
  }
  if (metrics.skipRate > 1) {
    uncertain.push('slot skip rate cannot exceed 1');
    return;
  }
  if (!isFiniteNumber(metrics.actualTargetRate)
      || metrics.actualTargetRate < thresholds.actualTargetRateMin
      || metrics.actualTargetRate > thresholds.actualTargetRateMax) {
    uncertain.push('actual rate is outside the target range');
  }
  if (!isFiniteNumber(metrics.skipRate) || metrics.skipRate > thresholds.skipRateMax) {
    uncertain.push('slot skip rate exceeds the validity threshold');
  }
  if (!isFiniteNumber(metrics.scheduleP95Ms)
      || metrics.scheduleP95Ms > thresholds.scheduleP95MsMax) {
    uncertain.push('schedule P95 exceeds the validity threshold');
  }
}

function evaluateConnection(metrics, thresholds, uncertain, failed) {
  const names = ['connectionSuccessRate', 'onlineRate', 'unexpectedDisconnectRate'];
  if (!requireFiniteNonNegative(metrics, names, uncertain)
      || names.some((name) => metrics[name] > 1)) {
    uncertain.push('connection rate evidence must be within 0 and 1');
    return;
  }
  if (metrics.connectionSuccessRate < thresholds.connectionSuccessRateMin) {
    failed.push('connection success rate is below threshold');
  }
  if (metrics.onlineRate < thresholds.onlineRateMin) {
    failed.push('measurement-end online rate is below threshold');
  }
  if (metrics.unexpectedDisconnectRate > thresholds.unexpectedDisconnectRateMax) {
    failed.push('unexpected disconnect rate exceeds threshold');
  }
}

function evaluateMessage(metrics, thresholds, uncertain, failed) {
  const countNames = [
    'sends',
    'sendSuccesses',
    'expectedDeliveries',
    'deliveryAckSuccesses',
    'unknownReceivers',
    'duplicates',
    'ackFailures',
    'drainResidual',
    'postDrainExcess',
    'trackingOverflows',
    'forcedStops',
  ];
  if (!requireNonNegativeIntegers(metrics, countNames, uncertain)
      || !requireFiniteNonNegative(
        metrics,
        ['receiveLatencyP95Ms', 'receiveLatencyP99Ms'],
        uncertain,
      )
      || !Array.isArray(metrics.minuteBacklog)
      || metrics.minuteBacklog.length < 3
      || metrics.minuteBacklog.some((value) => !Number.isInteger(value) || value < 0)) {
    uncertain.push('message correctness evidence is incomplete');
    return;
  }
  if (metrics.sends < thresholds.minimumSends) {
    uncertain.push('minimum sends were not reached');
  }
  if (metrics.expectedDeliveries <= 0) {
    uncertain.push('expected deliveries must be positive');
    return;
  }
  if (metrics.sendSuccesses > metrics.sends
      || metrics.deliveryAckSuccesses > metrics.expectedDeliveries
      || metrics.deliveryAckSuccesses + metrics.drainResidual > metrics.expectedDeliveries
      || metrics.drainResidual > metrics.expectedDeliveries
      || metrics.postDrainExcess > metrics.expectedDeliveries
      || metrics.minuteBacklog.some((value) => value > metrics.expectedDeliveries)) {
    uncertain.push('message counters have impossible denominator relationships');
    return;
  }
  if (metrics.receiveLatencyP95Ms > metrics.receiveLatencyP99Ms) {
    uncertain.push('receive latency P95 cannot exceed P99');
    return;
  }
  if (metrics.sends > 0 && metrics.sendSuccesses / metrics.sends < thresholds.sendSuccessRateMin) {
    failed.push('send business success rate is below threshold');
  }
  if (metrics.deliveryAckSuccesses / metrics.expectedDeliveries
      < thresholds.deliveryAckSuccessRateMin) {
    failed.push('unique delivery and ACK success rate is below threshold');
  }
  if (metrics.receiveLatencyP95Ms > thresholds.receiveLatencyP95Ms) {
    failed.push('receive latency P95 exceeds threshold');
  }
  if (metrics.receiveLatencyP99Ms > thresholds.receiveLatencyP99Ms) {
    failed.push('receive latency P99 exceeds threshold');
  }
  if (metrics.unknownReceivers > thresholds.unknownReceiverMax) {
    failed.push('unknown receiver count exceeds threshold');
  }
  if (metrics.duplicates / metrics.expectedDeliveries > thresholds.duplicateRateMax) {
    failed.push('duplicate delivery rate exceeds threshold');
  }
  if (metrics.ackFailures > 0) {
    failed.push('notification ACK failures were observed');
  }
  if (failed.length === 0 && lastThreeStrictlyGrow(metrics.minuteBacklog)) {
    uncertain.push('minute backlog continuously grows in the last three buckets');
  }
  if (failed.length === 0
      && metrics.drainResidual / metrics.expectedDeliveries > thresholds.drainResidualRateMax) {
    uncertain.push('post-drain residual exceeds the validity threshold');
  }
  if (metrics.postDrainExcess > 0) {
    uncertain.push('events exceeded the bounded drain window');
  }
  if (metrics.trackingOverflows > 0) {
    uncertain.push('message tracking capacity was exceeded');
  }
  if (metrics.forcedStops > 0) {
    failed.push('websocket close grace period expired');
  }
}

function lastThreeStrictlyGrow(samples) {
  const [first, second, third] = samples.slice(-3);
  return first < second && second < third;
}

function validateThresholds(thresholds, uncertain) {
  const missing = REQUIRED_THRESHOLDS.filter((name) => !isFiniteNumber(thresholds[name]));
  if (missing.length > 0) {
    uncertain.push(`missing finite thresholds: ${missing.join(',')}`);
    return;
  }
  if (REQUIRED_THRESHOLDS.some((name) => thresholds[name] < 0)) {
    uncertain.push('thresholds must be nonnegative');
  }
  if (FRACTION_THRESHOLDS.some((name) => thresholds[name] > 1)) {
    uncertain.push('percentage thresholds must be within 0 and 1');
  }
  if (thresholds.actualTargetRateMin <= 0
      || thresholds.actualTargetRateMin > thresholds.actualTargetRateMax) {
    uncertain.push('actual target rate thresholds are invalid');
  }
  if (thresholds.receiveLatencyP95Ms > thresholds.receiveLatencyP99Ms) {
    uncertain.push('receive latency threshold P95 cannot exceed P99');
  }
  if (thresholds.generatorCpuPercentMin > 100) {
    uncertain.push('generator CPU threshold cannot exceed 100');
  }
  if (!Number.isInteger(thresholds.unknownReceiverMax)) {
    uncertain.push('unknown receiver threshold must be an integer');
  }
  if (POSITIVE_INTEGER_THRESHOLDS.some(
    (name) => !Number.isInteger(thresholds[name]) || thresholds[name] <= 0,
  )) {
    uncertain.push('sample and count thresholds must be positive integers');
  }
}

function hasFiniteSamples(samples, minimum) {
  return Number.isInteger(minimum) && minimum > 0
    && Array.isArray(samples) && samples.length >= minimum && samples.every(isFiniteNumber);
}

function validSendExceptionEvidence(evidence) {
  return isPlainObject(evidence)
    && Number.isInteger(evidence.count)
    && evidence.count >= 0
    && ((evidence.count === 0 && evidence.origin === 'none')
      || (evidence.count > 0
        && (evidence.origin === 'service_send' || evidence.origin === 'generator_local')));
}

function hasConsecutive(samples, length, predicate) {
  let consecutive = 0;
  for (const sample of samples) {
    consecutive = predicate(sample) ? consecutive + 1 : 0;
    if (consecutive >= length) {
      return true;
    }
  }
  return false;
}

function requireFinite(source, names, uncertain) {
  const missing = names.filter((name) => !isFiniteNumber(source[name]));
  if (missing.length > 0) {
    uncertain.push(`missing numeric evidence: ${missing.join(',')}`);
    return false;
  }
  return true;
}

function requireFiniteNonNegative(source, names, uncertain) {
  if (!requireFinite(source, names, uncertain)) {
    return false;
  }
  const negative = names.filter((name) => source[name] < 0);
  if (negative.length > 0) {
    uncertain.push(`negative numeric evidence: ${negative.join(',')}`);
    return false;
  }
  return true;
}

function requireNonNegativeIntegers(source, names, uncertain) {
  const invalid = names.filter((name) => !Number.isInteger(source[name]) || source[name] < 0);
  if (invalid.length > 0) {
    uncertain.push(`missing count evidence: ${invalid.join(',')}`);
    return false;
  }
  return true;
}

function isFiniteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function result(status, reasons) {
  return { status, reasons };
}
