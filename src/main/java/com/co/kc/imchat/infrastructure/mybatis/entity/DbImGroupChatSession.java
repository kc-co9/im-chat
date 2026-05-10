package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群聊会话表 db_im_group_chat_session
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("db_im_group_chat_session")
public class DbImGroupChatSession extends BaseEntity {

    private Long chatId;

    private Long userId;

    private Long lastMessageId;

    private Long readMessageId;

    private Integer unreadMessageCount;
}
