package com.kim.omgchat.model.enums;

public class PushQueue {

    public static final String QUEUE_RESULT = "/queue/result";

    public static final String QUEUE_PRIVATE_MESSAGE_SENT = "/queue/message/private/sent";

    public static final String QUEUE_PRIVATE_MESSAGE_READ = "/queue/message/private/read";

    public static final String QUEUE_PRIVATE_MESSAGE_REVOKED = "/queue/message/private/revoked";

    private PushQueue() {
    }
}
