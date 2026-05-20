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
    /**
     * 用户群会话ID。
     */
    private Long chatId;
    /**
     * 群ID。
     */
    private Long groupId;
    /**
     * 用户ID。
     */
    private Long userId;
    /**
     * 用户定义的群备注。
     */
    private String groupAlias;
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
