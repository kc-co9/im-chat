package com.kim.omgchat.infrastructure.mybatis.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊表(DbImPrivateChat)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DbImPrivateChat extends BaseEntity {
    //聊天ID
    private Long chatId;
    //成员ID_1
    private Long member1;
    //成员ID_2
    private Long member2;
}

