package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 私聊表(DbImPrivateChat)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("db_im_private_chat")
public class DbImPrivateChat extends BaseEntity {
    /**
     * 聊天ID。
     */
    private Long chatId;
    /**
     * 用户ID。
     */
    private Long userId;
    /**
     * 聊天的用户ID（对端）。
     */
    private Long peerUserId;
    /**
     * 最新消息ID。
     */
    private Long lastMessageId;
    /**
     * 已读消息ID。
     */
    private Long readMessageId;
    /**
     * 未读消息数量。
     */
    private Integer unreadMessageCount;
    /**
     * 状态：0-未知，1-正常，2-隐藏。
     */
    private DbImChatStatus status;
    /**
     * 活跃时间。
     */
    private LocalDateTime activeTime;
}
