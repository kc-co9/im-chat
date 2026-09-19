import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildAckFrame,
  expectedFanout,
  parseFrame,
  parseHttpResult,
} from '../lib/protocol.js';

test('parses a unified HttpResult without converting long IDs to numbers', () => {
  const data = parseHttpResult(JSON.stringify({
    code: 0,
    msg: 'success',
    data: { userId: '9223372036854775807', chatId: '9007199254740993' },
  }));

  assert.deepEqual(data, {
    userId: '9223372036854775807',
    chatId: '9007199254740993',
  });
  assert.throws(
    () => parseHttpResult({ code: 1001, msg: 'failed', data: {} }),
    /HTTP business code 1001/i,
  );
  assert.throws(
    () => parseHttpResult({ code: 0, msg: 'success', data: { userId: 42 } }),
    /userId.*decimal string/i,
  );
});

test('losslessly parses unquoted HTTP long IDs above the safe integer range', () => {
  const data = parseHttpResult(`{
    "code": 0,
    "msg": "success",
    "data": {
      "userId": 9007199254740993,
      "groupId": 9223372036854775807,
      "chatId": 9007199254740995,
      "ownerId": 9007199254740997
    }
  }`);

  assert.deepEqual(data, {
    userId: '9007199254740993',
    groupId: '9223372036854775807',
    chatId: '9007199254740995',
    ownerId: '9007199254740997',
  });
});

test('parses successful frames and rejects wrong or malformed frames', () => {
  const frame = {
    version: '1',
    type: 'response',
    cmd: 'message.private.send',
    seq: 'send-1',
    traceId: 'trace-1',
    code: 0,
    message: 'success',
    body: { chatId: '9007199254740993', messageId: '9223372036854775807' },
  };

  assert.deepEqual(parseFrame(JSON.stringify(frame), {
    type: 'response',
    cmd: 'message.private.send',
    seq: 'send-1',
  }), frame);
  assert.throws(
    () => parseFrame({ ...frame, code: -1 }, { cmd: frame.cmd }),
    /frame business code -1/i,
  );
  assert.throws(
    () => parseFrame(frame, { cmd: 'message.group.send' }),
    /unexpected frame cmd/i,
  );
  assert.throws(
    () => parseFrame({ ...frame, body: { messageId: 42 } }, { cmd: frame.cmd }),
    /messageId.*decimal string/i,
  );
  assert.throws(() => parseFrame('{'), /valid JSON/i);
});

test('requires supported Frame version and honors expected version', () => {
  const response = {
    version: '1',
    type: 'response',
    cmd: 'notification.ack',
    seq: 'ack-1',
    traceId: 'trace-1',
    code: 0,
    message: 'success',
    body: null,
  };

  assert.equal(parseFrame(response, { version: '1' }).version, '1');
  for (const version of ['', '2', null, 1]) {
    assert.throws(() => parseFrame({ ...response, version }), /frame version/i);
  }
  assert.throws(
    () => parseFrame(response, { version: '2' }),
    /unexpected frame version/i,
  );
});

test('parses production-shaped pushes using receipt and message content as authorities', () => {
  const raw = `{
    "version":"1",
    "type":"push",
    "cmd":"message.private.sent",
    "seq":null,
    "traceId":null,
    "code":null,
    "message":null,
    "body":{
      "notification":{
        "messageId":9007199254740993,
        "chatId":9007199254740995,
        "senderId":9007199254740997,
        "receiverId":9007199254740999,
        "messageType":"TEXT",
        "messageContent":"correlation-text",
        "sendTime":"2026-09-14T12:00:00"
      },
      "eventId":"PRIVATE_MESSAGE_SEND:9007199254740999:9007199254740995:9007199254740993",
      "receiptId":"PRIVATE_MESSAGE_SEND:9007199254740999:9007199254740995:9007199254740993"
    }
  }`;

  const push = parseFrame(raw, { type: 'push', cmd: 'message.private.sent' });

  assert.equal(push.seq, null);
  assert.equal(push.code, null);
  assert.deepEqual(push.authoritativeIds, {
    receiverId: '9007199254740999',
    chatId: '9007199254740995',
    messageId: '9007199254740993',
  });
  assert.equal(push.receiptType, 'PRIVATE_MESSAGE_SEND');
  assert.equal(push.messageContent, 'correlation-text');
  assert.notEqual(
    String(push.body.notification.receiverId),
    push.authoritativeIds.receiverId,
    'rounded native JSON numbers must not become authoritative IDs',
  );
});

test('accepts a null response body but keeps response business fields strict', () => {
  const response = {
    version: '1',
    type: 'response',
    cmd: 'notification.ack',
    seq: 'ack-1',
    traceId: 'trace-1',
    code: 0,
    message: 'success',
    body: null,
  };

  assert.deepEqual(parseFrame(response, {
    type: 'response',
    cmd: 'notification.ack',
    seq: 'ack-1',
  }), response);
  assert.throws(() => parseFrame({ ...response, seq: null }), /response.*seq/i);
  assert.throws(() => parseFrame({ ...response, code: null }), /response.*code/i);
});

test('rejects unknown or malformed push notifications', () => {
  const push = {
    version: '1',
    type: 'push',
    cmd: 'message.group.sent',
    seq: null,
    traceId: null,
    code: null,
    message: null,
    body: {
      notification: { messageContent: 'correlation-text' },
      eventId: 'GROUP_MESSAGE_SEND:2:3:4',
      receiptId: 'GROUP_MESSAGE_SEND:2:3:4',
    },
  };

  assert.equal(parseFrame(push).receiptType, 'GROUP_MESSAGE_SEND');
  assert.throws(() => parseFrame({ ...push, cmd: 'message.group.revoked' }), /push cmd/i);
  assert.throws(
    () => parseFrame({ ...push, body: { ...push.body, receiptId: 'PRIVATE_MESSAGE_SEND:2:3:4' } }),
    /receipt type.*push cmd/i,
  );
  assert.throws(
    () => parseFrame({ ...push, body: { ...push.body, notification: { messageContent: 42 } } }),
    /messageContent/i,
  );
});

test('builds ACK frames from canonical string receipt IDs and actual receipt types', () => {
  const privateAck = buildAckFrame({
    receiptId: 'PRIVATE_MESSAGE_SEND:9223372036854775807:9007199254740993:7',
    sequence: 'ack-1',
    traceId: 'trace-ack-1',
  });
  const groupAck = buildAckFrame({
    receiptId: 'GROUP_MESSAGE_SEND:2:3:4',
    sequence: 'ack-2',
    traceId: 'trace-ack-2',
  });

  assert.deepEqual(privateAck, {
    version: '1',
    cmd: 'notification.ack',
    seq: 'ack-1',
    traceId: 'trace-ack-1',
    body: {
      chatId: '9007199254740993',
      messageId: '7',
      receiptType: 'PRIVATE_MESSAGE_SEND',
    },
  });
  assert.equal(groupAck.body.receiptType, 'GROUP_MESSAGE_SEND');
  assert.equal(typeof groupAck.body.chatId, 'string');
  assert.equal(typeof groupAck.body.messageId, 'string');
});

test('rejects malformed receipt IDs without numeric coercion', () => {
  for (const receiptId of [
    'PRIVATE_SENT:1:2:3',
    'PRIVATE_MESSAGE_SEND:01:2:3',
    'PRIVATE_MESSAGE_SEND:1:2:900719925474099312345',
    'PRIVATE_MESSAGE_SEND:1:2',
    'GROUP_MESSAGE_SEND:1:0:3',
  ]) {
    assert.throws(
      () => buildAckFrame({ receiptId, sequence: 'ack-1', traceId: 'trace-1' }),
      /receiptId/i,
    );
  }
});

test('derives expected fanout from message type and group size', () => {
  assert.equal(expectedFanout('PRIVATE_MESSAGE_SEND', 10), 1);
  assert.equal(expectedFanout('GROUP_MESSAGE_SEND', 10), 9);
  assert.equal(expectedFanout('GROUP_MESSAGE_SEND', 3), 2);
  assert.throws(() => expectedFanout('GROUP_MESSAGE_SEND', 1), /groupSize/i);
  assert.throws(() => expectedFanout('PRIVATE_MESSAGE_REVOKE', 10), /message type/i);
});
