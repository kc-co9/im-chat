package com.co.kc.imchat.infrastructure.mybatis.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群聊表(DbImGroupChat)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbImGroupChat extends BaseEntity {
    //聊天ID
    private Long chatId;
    //群公告
    private String notification;
    //群设置
    private String settings;
}

