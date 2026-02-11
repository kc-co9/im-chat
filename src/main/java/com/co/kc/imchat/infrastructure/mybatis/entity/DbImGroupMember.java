package com.co.kc.imchat.infrastructure.mybatis.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群聊表
 * db_im_group_member
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DbImGroupMember extends BaseEntity {
    /**
     * 聊天ID
     */
    private Long chatId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户别名
     */
    private String userAlias;

    /**
     * 用户定义的群别名
     */
    private String groupAlias;

    /**
     * 群成员设置
     */
    private String setting;

}