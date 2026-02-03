package com.kim.omgchat.infrastructure.mybatis.entity;

import com.kim.omgchat.infrastructure.mybatis.enums.DbImChatType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天表(DbImChat)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbImChat extends BaseEntity {
    //聊天ID
    private Long chatId;
    //聊天名称
    private String name;
    //聊天类型 0-未知, 1-单聊,2-群聊
    private DbImChatType type;
}

