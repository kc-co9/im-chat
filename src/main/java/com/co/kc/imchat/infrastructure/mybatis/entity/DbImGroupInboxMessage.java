package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group_inbox_message")
public class DbImGroupInboxMessage extends BaseEntity {
    private Long messageId;
    private Long groupId;
    private Long chatId;
    private Long userId;
    private String token;
    private Long senderId;
    private DbImMessageType type;
    private String content;
    private DbGroupImMessageStatus status;
    private LocalDateTime sendTime;
    private LocalDateTime receiveTime;
    private LocalDateTime readTime;
    private LocalDateTime revokeTime;
}
