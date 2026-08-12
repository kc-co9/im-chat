package com.co.kc.imchat.common.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * 实时协议业务命令。
 */
public enum FrameCommand {
    /**
     * 发送单聊消息。
     */
    PRIVATE_MESSAGE_SEND("message.private.send"),

    /**
     * 标记单聊消息已读。
     */
    PRIVATE_MESSAGE_READ("message.private.read"),

    /**
     * 撤回单聊消息。
     */
    PRIVATE_MESSAGE_REVOKE("message.private.revoke"),

    /**
     * 发送群聊消息。
     */
    GROUP_MESSAGE_SEND("message.group.send"),

    /**
     * 标记群聊消息已读。
     */
    GROUP_MESSAGE_READ("message.group.read"),

    /**
     * 撤回群聊消息。
     */
    GROUP_MESSAGE_REVOKE("message.group.revoke"),

    /**
     * 确认服务端推送通知。
     */
    NOTIFICATION_ACK("notification.ack");

    private final String command;

    FrameCommand(String command) {
        this.command = command;
    }

    public String command() {
        return command;
    }

    public static Optional<FrameCommand> from(String command) {
        return Arrays.stream(values())
                .filter(it -> it.command.equals(command))
                .findFirst();
    }
}
