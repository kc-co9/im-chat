package com.co.kc.imchat.service.message.application.notification.task;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Objects;

/**
 * 等待客户端回执的通知重投任务。
 * 任务只保存再次通知所需的数据，重试次数等运行状态由存储实现维护。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptTask {
    /**
     * 本次通知回执任务的唯一标识，用于确认、读取任务内容和记录重试次数。
     */
    private String receiptId;
    /**
     * 需要客户端回执的通知类型，用于找到对应通知器并生成确认标识。
     */
    private ReceiptType receiptType;
    /**
     * 需要再次发送给客户端的通知载荷。
     */
    private Object notification;
    /**
     * 再次通知前等待的延迟时间，毫秒。
     */
    private Long delayMillis;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String receiptId;
        private ReceiptType receiptType;
        private Object notification;
        private Long delayMillis;

        public Builder receiptId(String receiptId) {
            this.receiptId = receiptId;
            return this;
        }

        public Builder receiptType(ReceiptType receiptType) {
            this.receiptType = receiptType;
            return this;
        }

        public Builder notification(Object notification) {
            this.notification = notification;
            return this;
        }

        public Builder delayMillis(Long delayMillis) {
            this.delayMillis = delayMillis;
            return this;
        }

        public ReceiptTask build() {
            Objects.requireNonNull(receiptId, "receiptId不能为空");
            Objects.requireNonNull(receiptType, "receiptType不能为空");
            Objects.requireNonNull(notification, "notification不能为空");
            Objects.requireNonNull(delayMillis, "delayMillis不能为空");
            if (delayMillis <= 0) {
                throw new IllegalArgumentException("delayMillis必须大于0");
            }
            return new ReceiptTask(receiptId, receiptType, notification, delayMillis);
        }
    }
}
