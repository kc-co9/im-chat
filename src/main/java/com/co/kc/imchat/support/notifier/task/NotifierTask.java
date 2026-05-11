package com.co.kc.imchat.support.notifier.task;

import lombok.Data;

@Data
public class NotifierTask {
    private NotifierTaskType type;
    private String command;
    private int attempts;
    private long nextAtMillis;
    private long createdAtMillis;
}
