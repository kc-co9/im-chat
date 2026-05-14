package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("db_im_group_chat")
public class DbImGroupChat extends BaseEntity {
    private Long chatId;
    private Long groupId;
    private Long userId;
    private String groupAlias;
    private Long lastMessageId;
    private Long readMessageId;
    private LocalDateTime readTime;
    private Integer unreadMessageCount;
    private DbImChatStatus status;
    private LocalDateTime activeTime;
}
