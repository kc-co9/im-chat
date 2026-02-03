package com.kim.omgchat.infrastructure.mybatis.entity;

import java.time.LocalDateTime;

import com.kim.omgchat.infrastructure.mybatis.enums.DbImMessageStatus;
import com.kim.omgchat.infrastructure.mybatis.enums.DbImMessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群聊消息表(DbImGroupMessage)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbImGroupMessage extends BaseEntity {
    //消息ID
    private Long messageId;
    //聊天ID
    private Long chatId;
    //消息TOKEN
    private String token;
    //发送的用户ID
    private Long senderId;
    //消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息
    private DbImMessageType type;
    //消息内容
    private String content;
    //消息状态 0-未知 1-已发送 2-已读 3-已撤回
    private DbImMessageStatus status;
    //发送时间
    private LocalDateTime sendTime;
    //撤回时间
    private LocalDateTime revokeTime;
}

