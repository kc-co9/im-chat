package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天最后消息表
 * db_im_chat_last_message
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("db_im_chat_last_message")
public class DbImChatLastMessage extends BaseEntity {
    /**
     * 聊天ID
     */
    private Long chatId;

    /**
     * 消息ID
     */
    private Long messageId;
}