export function createDeliveryAccounting({
  groupSize,
  measureStartMs,
  measureEndMs,
  drainEndMs,
  maxTrackedDeliveries = 50_000,
  maxCompletedReceipts = 20_000,
  trackLatencies = true,
}) {
  assertInteger(groupSize, 'groupSize', 2);
  assertFinite(measureStartMs, 'measureStartMs');
  assertFinite(measureEndMs, 'measureEndMs');
  assertFinite(drainEndMs, 'drainEndMs');
  assertInteger(maxTrackedDeliveries, 'maxTrackedDeliveries', 1);
  assertInteger(maxCompletedReceipts, 'maxCompletedReceipts', 1);
  if (typeof trackLatencies !== 'boolean') throw new Error('trackLatencies must be boolean');
  if (measureStartMs > measureEndMs || measureEndMs > drainEndMs) {
    throw new Error('measurement and drain boundaries are invalid');
  }

  const sends = new Map();
  const completedReceipts = new Map();
  const completedOrder = [];
  let completedHead = 0;
  const latencies = [];
  const backlog = [];
  let sendCount = 0;
  let sendSuccesses = 0;
  let expectedDeliveries = 0;
  let deliveryAckSuccesses = 0;
  let unknownReceivers = 0;
  let duplicates = 0;
  let duplicateAcks = 0;
  let ackFailures = 0;
  let postDrainExcess = 0;
  let trackedDeliveries = 0;
  let trackingOverflows = 0;

  return Object.freeze({
    recordSend(event) {
      validatePhase(event?.phase);
      if (event.phase === 'warmup') return;
      assertToken(event.token);
      assertCanonicalId(event.senderId, 'senderId');
      assertFinite(event.sentAtMs, 'sentAtMs');
      if (event.sentAtMs < measureStartMs || event.sentAtMs > measureEndMs) {
        throw new Error('formal send is outside the measurement window');
      }
      if (typeof event.success !== 'boolean') throw new Error('send success must be boolean');
      if (sends.has(event.token)) throw new Error('duplicate send token');
      const receivers = validateReceivers(event, groupSize);
      const deliveries = new Map(receivers.map((receiverId) => [receiverId, {
        receiptType: event.messageType === 'private' ? 'PRIVATE_MESSAGE_SEND' : 'GROUP_MESSAGE_SEND',
        chatId: event.receiverChats[receiverId],
        receiptId: undefined,
        receivedAtMs: undefined,
        acked: false,
      }]));
      sendCount += 1;
      expectedDeliveries += deliveries.size;
      if (event.success) {
        sendSuccesses += 1;
        if (trackedDeliveries + deliveries.size > maxTrackedDeliveries) {
          trackingOverflows += 1;
          return;
        }
        sends.set(event.token, {
          token: event.token,
          sentAtMs: event.sentAtMs,
          success: true,
          deliveries,
          messageId: undefined,
        });
        trackedDeliveries += deliveries.size;
      }
    },

    recordDelivery(event) {
      validatePhase(event?.phase);
      if (event.phase === 'warmup') return;
      assertFinite(event.receivedAtMs, 'receivedAtMs');
      if (event.receivedAtMs > drainEndMs) {
        postDrainExcess += 1;
        return;
      }
      const completed = completedReceipts.get(event.receiptId);
      if (completed?.receiverId === event.receiverId) {
        duplicates += 1;
        return;
      }
      const expected = expectedDelivery(sends, event);
      if (expected === undefined || !expected.send.success
          || expected.receipt.receiverId !== event.receiverId
          || expected.delivery.receiptType !== expected.receipt.receiptType
          || expected.delivery.chatId !== expected.receipt.chatId
          || (expected.send.messageId !== undefined
            && expected.send.messageId !== expected.receipt.messageId)) {
        unknownReceivers += 1;
        return;
      }
      if (expected.delivery.receiptId !== undefined) {
        duplicates += 1;
        return;
      }
      expected.send.messageId = expected.receipt.messageId;
      expected.delivery.receiptId = event.receiptId;
      expected.delivery.receivedAtMs = event.receivedAtMs;
    },

    recordAck(event) {
      validatePhase(event?.phase);
      if (event.phase === 'warmup') return;
      assertFinite(event.ackedAtMs, 'ackedAtMs');
      if (event.ackedAtMs > drainEndMs) {
        postDrainExcess += 1;
        return;
      }
      const completed = completedReceipts.get(event.receiptId);
      if (completed?.receiverId === event.receiverId) {
        duplicateAcks += 1;
        return;
      }
      const expected = expectedDelivery(sends, event);
      if (expected === undefined || expected.receipt.receiverId !== event.receiverId
          || expected.delivery.receiptId !== event.receiptId) {
        unknownReceivers += 1;
        return;
      }
      if (expected.delivery.acked) {
        duplicateAcks += 1;
        return;
      }
      if (!Number.isInteger(event.code)) throw new Error('ACK code must be an integer');
      if (event.code !== 0) {
        ackFailures += 1;
        return;
      }
      expected.delivery.acked = true;
      deliveryAckSuccesses += 1;
      if (trackLatencies) latencies.push(expected.delivery.receivedAtMs - expected.send.sentAtMs);
      if ([...expected.send.deliveries.values()].every(({ acked }) => acked)) {
        for (const [receiverId, delivery] of expected.send.deliveries) {
          completedReceipts.set(delivery.receiptId, { receiverId });
          completedOrder.push(delivery.receiptId);
          if (completedOrder.length - completedHead > maxCompletedReceipts) {
            completedReceipts.delete(completedOrder[completedHead++]);
            if (completedHead >= 1_024) {
              completedOrder.splice(0, completedHead);
              completedHead = 0;
            }
          }
        }
        sends.delete(expected.send.token);
        trackedDeliveries -= expected.send.deliveries.size;
      }
    },

    recordBacklogSample() {
      backlog.push(expectedDeliveries - deliveryAckSuccesses);
    },

    snapshot() {
      return Object.freeze({
        sends: sendCount,
        sendSuccesses,
        expectedDeliveries,
        deliveryAckSuccesses,
        unknownReceivers,
        duplicates,
        duplicateAcks,
        ackFailures,
        receiveLatencyP95Ms: percentile(latencies, 0.95),
        receiveLatencyP99Ms: percentile(latencies, 0.99),
        minuteBacklog: backlog.slice(-3),
        drainResidual: expectedDeliveries - deliveryAckSuccesses,
        postDrainExcess,
        trackingOverflows,
      });
    },
  });
}

function validateReceivers(event, groupSize) {
  if (!['private', 'group'].includes(event.messageType)) throw new Error('messageType must be private or group');
  if (!Array.isArray(event.receiverIds)) throw new Error('receiverIds must be an array');
  const receivers = [...event.receiverIds];
  receivers.forEach((receiverId) => assertCanonicalId(receiverId, 'receiverId'));
  if (new Set(receivers).size !== receivers.length || receivers.includes(event.senderId)) {
    throw new Error('receiverIds must be unique and exclude sender');
  }
  if (event.messageType === 'private' && receivers.length !== 1) {
    throw new Error('private message must have exactly one receiver');
  }
  if (event.messageType === 'group' && receivers.length !== groupSize - 1) {
    throw new Error(`group message must have exactly ${groupSize - 1} receivers`);
  }
  if (event.receiverChats === null || typeof event.receiverChats !== 'object'
      || Array.isArray(event.receiverChats)
      || Object.keys(event.receiverChats).length !== receivers.length) {
    throw new Error('receiverChats must exactly map expected receivers');
  }
  for (const receiverId of receivers) {
    assertCanonicalId(event.receiverChats[receiverId], 'receiver chatId');
  }
  return receivers;
}

function expectedDelivery(sends, event) {
  assertToken(event?.token);
  assertCanonicalId(event.receiverId, 'receiverId');
  const receipt = parseReceipt(event.receiptId);
  const send = sends.get(event.token);
  const delivery = send?.deliveries.get(event.receiverId);
  return send === undefined || delivery === undefined ? undefined : { send, delivery, receipt };
}

function parseReceipt(value) {
  assertReceiptId(value);
  const [receiptType, receiverId, chatId, messageId, extra] = value.split(':');
  if (!['PRIVATE_MESSAGE_SEND', 'GROUP_MESSAGE_SEND'].includes(receiptType) || extra !== undefined) {
    throw new Error('receiptId type is invalid');
  }
  assertCanonicalId(receiverId, 'receipt receiverId');
  assertCanonicalId(chatId, 'receipt chatId');
  assertCanonicalId(messageId, 'receipt messageId');
  return { receiptType, receiverId, chatId, messageId };
}

function percentile(values, quantile) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.ceil(sorted.length * quantile) - 1];
}

function validatePhase(phase) {
  if (!['warmup', 'measure'].includes(phase)) throw new Error('phase must be warmup or measure');
}

function assertToken(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 256) {
    throw new Error('token must be a bounded non-empty string');
  }
}

function assertReceiptId(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 256) {
    throw new Error('receiptId must be a bounded non-empty string');
  }
}

function assertCanonicalId(value, name) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new Error(`${name} must be a canonical positive ID`);
  }
}

function assertFinite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${name} must be finite`);
}

function assertInteger(value, name, minimum) {
  if (!Number.isInteger(value) || value < minimum) throw new Error(`${name} must be at least ${minimum}`);
}
