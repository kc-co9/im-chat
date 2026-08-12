package com.co.kc.imchat.service.account.infrastructure.mybatis.entity;

import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表(DbUser)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbUser extends BaseEntity {
    /**
     * 用户ID。
     */
    private Long userId;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 邮箱。
     */
    private String email;
    /**
     * 密码。
     */
    private String password;
}
