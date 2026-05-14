package com.co.kc.imchat.infrastructure.mybatis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊收件箱消息行，表 {@code db_im_private_inbox_message}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_private_inbox_message")
public class DbImPrivateInboxMessage extends BaseEntity {
    private Long messageId;
    private Long chatId;
    private Long userId;
    private String token;
    private Long senderId;
    private DbImMessageType type;
    private String content;
    private DbPrivateImMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime receiveTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
