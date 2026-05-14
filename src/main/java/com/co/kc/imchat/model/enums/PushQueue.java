package com.co.kc.imchat.model.enums;

public class PushQueue {

    public static final String QUEUE_RESULT = "/queue/result";

    public static final String QUEUE_PRIVATE_MESSAGE_SENT = "/queue/message/private/sent";

    public static final String QUEUE_PRIVATE_MESSAGE_REVOKED = "/queue/message/private/revoked";

    public static final String QUEUE_GROUP_MESSAGE_SENT = "/queue/message/group/sent";

    public static final String QUEUE_GROUP_MESSAGE_REVOKED = "/queue/message/group/revoked";

    private PushQueue() {
    }
}
