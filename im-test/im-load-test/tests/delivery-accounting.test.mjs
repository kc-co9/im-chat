import assert from 'node:assert/strict';
import test from 'node:test';

import { createDeliveryAccounting } from '../lib/delivery-accounting.js';

test('send failures stay in both send and expected-delivery denominators', () => {
  const accounting = tracker();
  accounting.recordSend(send('p1', 'private', ['102'], false));
  accounting.recordSend(send('g1', 'group', ['102', '103', '104'], true));

  const snapshot = accounting.snapshot();
  assert.equal(snapshot.sends, 2);
  assert.equal(snapshot.sendSuccesses, 1);
  assert.equal(snapshot.expectedDeliveries, 4);
});

test('private expects one receiver and group expects groupSize minus one', () => {
  const accounting = tracker();
  assert.throws(() => accounting.recordSend(send('bad-private', 'private', ['102', '103'], true)), /private.*one/i);
  assert.throws(() => accounting.recordSend(send('bad-group', 'group', ['102', '103'], true)), /group.*3/i);
});

test('duplicate delivery and ACK never compensate for a missing receiver', () => {
  const accounting = tracker();
  accounting.recordSend(send('g1', 'group', ['102', '103', '104'], true));
  accounting.recordDelivery(delivery('g1', '102', 'GROUP_MESSAGE_SEND:102:201:301', 20));
  accounting.recordAck(ack('g1', '102', 'GROUP_MESSAGE_SEND:102:201:301', 0, 30));
  accounting.recordDelivery(delivery('g1', '102', 'GROUP_MESSAGE_SEND:102:201:301', 40));
  accounting.recordAck(ack('g1', '102', 'GROUP_MESSAGE_SEND:102:201:301', 0, 50));

  const snapshot = accounting.snapshot();
  assert.equal(snapshot.expectedDeliveries, 3);
  assert.equal(snapshot.deliveryAckSuccesses, 1);
  assert.equal(snapshot.duplicates, 1);
  assert.equal(snapshot.duplicateAcks, 1);
  assert.equal(snapshot.drainResidual, 2);
});

test('unknown receiver and nonzero ACK are observable failures', () => {
  const accounting = tracker();
  accounting.recordSend(send('p1', 'private', ['102'], true));
  accounting.recordDelivery(delivery('p1', '999', 'PRIVATE_MESSAGE_SEND:999:201:301', 20));
  accounting.recordDelivery(delivery('p1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 30));
  accounting.recordAck(ack('p1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 10001, 40));

  const snapshot = accounting.snapshot();
  assert.equal(snapshot.unknownReceivers, 1);
  assert.equal(snapshot.deliveryAckSuccesses, 0);
  assert.equal(snapshot.ackFailures, 1);
});

test('wrong receipt type, chat, or per-send message ID is rejected as unknown delivery', () => {
  const accounting = tracker();
  accounting.recordSend(send('g1', 'group', ['102', '103', '104'], true));
  accounting.recordDelivery(delivery('g1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 20));
  accounting.recordSend(send('g2', 'group', ['102', '103', '104'], true));
  accounting.recordDelivery(delivery('g2', '103', 'GROUP_MESSAGE_SEND:103:999:301', 30));
  accounting.recordSend(send('g3', 'group', ['102', '103', '104'], true));
  accounting.recordDelivery(delivery('g3', '102', 'GROUP_MESSAGE_SEND:102:201:301', 35));
  accounting.recordDelivery(delivery('g3', '103', 'GROUP_MESSAGE_SEND:103:202:302', 40));

  assert.equal(accounting.snapshot().unknownReceivers, 3);
  assert.equal(accounting.snapshot().deliveryAckSuccesses, 0);
});

test('warmup is isolated while formal sends may complete inside the drain window', () => {
  const accounting = tracker();
  accounting.recordSend(send('warm', 'private', ['102'], true, { phase: 'warmup', sentAtMs: 10 }));
  accounting.recordDelivery(delivery('warm', '102', 'PRIVATE_MESSAGE_SEND:102:201:300', 20, 'warmup'));
  accounting.recordSend(send('p1', 'private', ['102'], true, { sentAtMs: 900 }));
  accounting.recordDelivery(delivery('p1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 1_050));
  accounting.recordAck(ack('p1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 0, 1_080));

  assert.deepEqual(accounting.snapshot(), {
    sends: 1,
    sendSuccesses: 1,
    expectedDeliveries: 1,
    deliveryAckSuccesses: 1,
    unknownReceivers: 0,
    duplicates: 0,
    duplicateAcks: 0,
    ackFailures: 0,
    receiveLatencyP95Ms: 150,
    receiveLatencyP99Ms: 150,
    minuteBacklog: [],
    drainResidual: 0,
    postDrainExcess: 0,
    trackingOverflows: 0,
  });
});

test('tracking capacity is bounded and overflow remains visible', () => {
  const accounting = createDeliveryAccounting({
    groupSize: 4,
    measureStartMs: 0,
    measureEndMs: 1_000,
    drainEndMs: 1_100,
    maxTrackedDeliveries: 2,
  });
  accounting.recordSend(send('g1', 'group', ['102', '103', '104'], true));

  const snapshot = accounting.snapshot();
  assert.equal(snapshot.trackingOverflows, 1);
  assert.equal(snapshot.expectedDeliveries, 3);
  assert.equal(snapshot.drainResidual, 3);
});

test('events after drain are excess and backlog samples retain the last three buckets', () => {
  const accounting = tracker();
  accounting.recordSend(send('p1', 'private', ['102'], true));
  accounting.recordBacklogSample();
  accounting.recordBacklogSample();
  accounting.recordBacklogSample();
  accounting.recordBacklogSample();
  accounting.recordDelivery(delivery('p1', '102', 'PRIVATE_MESSAGE_SEND:102:201:301', 1_101));

  const snapshot = accounting.snapshot();
  assert.deepEqual(snapshot.minuteBacklog, [1, 1, 1]);
  assert.equal(snapshot.deliveryAckSuccesses, 0);
  assert.equal(snapshot.drainResidual, 1);
  assert.equal(snapshot.postDrainExcess, 1);
});

test('out-of-order receiver completion still accounts exact unique fanout', () => {
  const accounting = tracker();
  accounting.recordSend(send('g1', 'group', ['102', '103', '104'], true));
  for (const [receiverId, chatId, at] of [['104', '203', 40], ['102', '201', 20], ['103', '202', 30]]) {
    const receiptId = `GROUP_MESSAGE_SEND:${receiverId}:${chatId}:301`;
    accounting.recordDelivery(delivery('g1', receiverId, receiptId, at));
    accounting.recordAck(ack('g1', receiverId, receiptId, 0, at + 5));
  }

  const snapshot = accounting.snapshot();
  assert.equal(snapshot.deliveryAckSuccesses, 3);
  assert.equal(snapshot.drainResidual, 0);
  assert.equal(snapshot.receiveLatencyP95Ms, 30);
  assert.equal(snapshot.receiveLatencyP99Ms, 30);
});

function tracker() {
  return createDeliveryAccounting({ groupSize: 4, measureStartMs: 0, measureEndMs: 1_000, drainEndMs: 1_100 });
}

function send(token, messageType, receiverIds, success, overrides = {}) {
  const receiverChats = Object.fromEntries(receiverIds.map((receiverId, index) => (
    [receiverId, String(201 + index)]
  )));
  return {
    token,
    messageType,
    senderId: '101',
    receiverIds,
    receiverChats,
    success,
    phase: 'measure',
    sentAtMs: 10,
    ...overrides,
  };
}

function delivery(token, receiverId, receiptId, receivedAtMs, phase = 'measure') {
  return { token, receiverId, receiptId, receivedAtMs, phase };
}

function ack(token, receiverId, receiptId, code, ackedAtMs) {
  return { token, receiverId, receiptId, code, ackedAtMs, phase: 'measure' };
}
