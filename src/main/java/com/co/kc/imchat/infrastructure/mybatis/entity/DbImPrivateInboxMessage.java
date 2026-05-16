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
    /**
     * 消息ID。
     */
    private Long messageId;
    /**
     * 聊天ID。
     */
    private Long chatId;
    /**
     * 收件箱所属用户ID。
     */
    private Long userId;
    /**
     * 消息TOKEN。
     */
    private String token;
    /**
     * 发送的用户ID。
     */
    private Long senderId;
    /**
     * 消息类型：0-未知，1-文本消息，2-图片消息，3-语音消息，4-视频消息，5-文件消息，6-表情包消息，7-系统消息。
     */
    private DbImMessageType type;
    /**
     * 消息内容。
     */
    private String content;
    /**
     * 消息状态：0-未知，1-已收到，2-已读，3-已撤回。
     */
    private DbPrivateImMessageStatus status;
    /**
     * 发送时间。
     */
    private LocalDateTime sendTime;
    /**
     * 接收时间。
     */
    private LocalDateTime receiveTime;
    /**
     * 已读时间。
     */
    private LocalDateTime readTime;
    /**
     * 撤回时间。
     */
    private LocalDateTime revokeTime;
}
