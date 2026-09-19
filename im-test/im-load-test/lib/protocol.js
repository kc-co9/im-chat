import {
  isLosslessNumber,
  parse as parseLosslessJson,
  toSafeNumberOrThrow,
} from 'lossless-json';

const MAX_LONG = '9223372036854775807';
const LONG_ID_FIELDS = new Set([
  'userId',
  'receiverId',
  'chatId',
  'messageId',
  'groupId',
  'ownerId',
]);
const SEND_RECEIPT_TYPES = new Set(['PRIVATE_MESSAGE_SEND', 'GROUP_MESSAGE_SEND']);
const FRAME_TYPES = new Set(['response', 'push', 'error']);
const PUSH_RECEIPT_TYPES = Object.freeze({
  'message.private.sent': 'PRIVATE_MESSAGE_SEND',
  'message.group.sent': 'GROUP_MESSAGE_SEND',
});

export function parseHttpResult(payload) {
  const result = parseJsonObject(payload, 'HttpResult', true);
  if (!Number.isInteger(result.code)) {
    throw new Error('HttpResult code must be an integer');
  }
  if (result.code !== 0) {
    throw new Error(`HTTP business code ${result.code}: ${stringMessage(result.msg)}`);
  }
  validateLongIds(result.data);
  return result.data;
}

export function parseFrame(payload, expected = {}) {
  const frame = parseJsonObject(payload, 'frame');
  if (frame.version !== '1') {
    throw new Error(`unsupported frame version: ${frame.version}`);
  }
  if (!FRAME_TYPES.has(frame.type) || typeof frame.cmd !== 'string') {
    throw new Error('malformed frame');
  }
  for (const field of ['version', 'type', 'cmd', 'seq']) {
    if (expected[field] !== undefined && frame[field] !== expected[field]) {
      throw new Error(`unexpected frame ${field}: ${frame[field]}`);
    }
  }
  if (frame.type === 'push') {
    return parsePushFrame(frame);
  }
  return parseResponseFrame(frame);
}

export function buildAckFrame({ receiptId, sequence, traceId }) {
  if (typeof sequence !== 'string' || sequence.length === 0
      || typeof traceId !== 'string' || traceId.length === 0) {
    throw new Error('ACK sequence and traceId must be non-empty strings');
  }
  const { receiptType, chatId, messageId } = parseReceiptId(receiptId);
  return {
    version: '1',
    cmd: 'notification.ack',
    seq: sequence,
    traceId,
    body: { chatId, messageId, receiptType },
  };
}

export function expectedFanout(messageType, groupSize) {
  if (!Number.isInteger(groupSize) || groupSize < 2) {
    throw new Error('groupSize must be an integer of at least 2');
  }
  if (messageType === 'PRIVATE_MESSAGE_SEND') {
    return 1;
  }
  if (messageType === 'GROUP_MESSAGE_SEND') {
    return groupSize - 1;
  }
  throw new Error(`unsupported message type: ${messageType}`);
}

function parsePushFrame(frame) {
  if (frame.seq !== null || frame.code !== null || !isPlainObject(frame.body)) {
    throw new Error('push frame requires null seq/code and an object body');
  }
  const expectedReceiptType = PUSH_RECEIPT_TYPES[frame.cmd];
  if (expectedReceiptType === undefined) {
    throw new Error(`unsupported push cmd: ${frame.cmd}`);
  }
  const notification = frame.body.notification;
  if (!isPlainObject(notification) || typeof notification.messageContent !== 'string') {
    throw new Error('push notification.messageContent must be a string');
  }
  const receipt = parseReceiptId(frame.body.receiptId);
  if (receipt.receiptType !== expectedReceiptType) {
    throw new Error('receipt type does not match push cmd');
  }
  if (typeof frame.body.eventId !== 'string' || frame.body.eventId !== frame.body.receiptId) {
    throw new Error('push eventId must equal receiptId');
  }
  return {
    ...frame,
    receiptType: receipt.receiptType,
    authoritativeIds: {
      receiverId: receipt.receiverId,
      chatId: receipt.chatId,
      messageId: receipt.messageId,
    },
    messageContent: notification.messageContent,
  };
}

function parseResponseFrame(frame) {
  if (typeof frame.seq !== 'string' || !Number.isInteger(frame.code)) {
    throw new Error('response frame requires a string seq and integer code');
  }
  if (frame.body !== null && !isPlainObject(frame.body)) {
    throw new Error('response frame body must be an object or null');
  }
  if (frame.code !== 0) {
    throw new Error(`frame business code ${frame.code}: ${stringMessage(frame.message)}`);
  }
  validateLongIds(frame.body);
  return frame;
}

function parseReceiptId(receiptId) {
  if (typeof receiptId !== 'string') {
    throw new Error('receiptId must be a string');
  }
  const segments = receiptId.split(':');
  if (segments.length !== 4 || !SEND_RECEIPT_TYPES.has(segments[0])) {
    throw new Error('receiptId must use a supported send receipt type and four segments');
  }
  const [receiptType, receiverId, chatId, messageId] = segments;
  canonicalLong(receiverId, 'receiptId receiverId');
  canonicalLong(chatId, 'receiptId chatId');
  canonicalLong(messageId, 'receiptId messageId');
  return { receiptType, receiverId, chatId, messageId };
}

function validateLongIds(value) {
  if (Array.isArray(value)) {
    value.forEach(validateLongIds);
    return;
  }
  if (!isPlainObject(value)) {
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (LONG_ID_FIELDS.has(key)) {
      canonicalLong(child, key);
    } else if (key === 'receiptId') {
      parseReceiptId(child);
    } else {
      validateLongIds(child);
    }
  }
}

function canonicalLong(value, name) {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)
      || value.length > MAX_LONG.length
      || (value.length === MAX_LONG.length && value > MAX_LONG)) {
    throw new Error(`${name} must be a canonical positive decimal string within Java Long range`);
  }
  return value;
}

function parseJsonObject(payload, name, preserveLongIds = false) {
  let value = payload;
  if (typeof payload === 'string') {
    try {
      value = preserveLongIds
        ? parseLosslessJson(payload, preserveHttpNumber)
        : JSON.parse(payload);
    } catch {
      throw new Error(`${name} must be valid JSON`);
    }
  }
  if (!isPlainObject(value)) {
    throw new Error(`${name} must be a JSON object`);
  }
  return value;
}

function preserveHttpNumber(key, value) {
  if (!isLosslessNumber(value)) {
    return value;
  }
  const source = value.toString();
  if (LONG_ID_FIELDS.has(key)) {
    return canonicalLong(source, key);
  }
  return toSafeNumberOrThrow(source);
}

function stringMessage(value) {
  return typeof value === 'string' ? value : 'unknown error';
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
