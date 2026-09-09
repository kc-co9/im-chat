CREATE DATABASE IF NOT EXISTS `im_chat_iam`;

USE `im_chat_iam`;

CREATE TABLE IF NOT EXISTS `db_iam_administrator`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `administrator_id` BIGINT UNSIGNED NOT NULL COMMENT '管理员业务ID',
    `username`       VARCHAR(64)     NOT NULL COMMENT '管理员用户名',
    `email`          VARCHAR(128)    NOT NULL COMMENT '管理员邮箱',
    `password_hash`  VARCHAR(255)    NOT NULL COMMENT '密码摘要',
    `status`         TINYINT         NOT NULL COMMENT '状态：1-启用，2-禁用',
    `create_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`     BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_administrator_id` (`administrator_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_administrator_username` (`username`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_administrator_email` (`email`, `is_deleted`) USING BTREE,
    KEY `idx_iam_administrator_status` (`status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM管理员表';

CREATE TABLE IF NOT EXISTS `db_iam_app`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `app_id`             BIGINT UNSIGNED NOT NULL COMMENT '应用业务ID',
    `app_key`            VARCHAR(64)     NOT NULL COMMENT '可识别应用编码',
    `name`               VARCHAR(64)     NOT NULL COMMENT '应用名称',
    `status`                    TINYINT         NOT NULL COMMENT '状态：1-启用，2-禁用',
    `create_time`               TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`               TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`                BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_app_id` (`app_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_app_key` (`app_key`, `is_deleted`) USING BTREE,
    KEY `idx_iam_app_status` (`status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM业务应用表';

CREATE TABLE IF NOT EXISTS `db_iam_oauth_client`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `oauth_client_id`    VARCHAR(128)    NOT NULL COMMENT 'OAuth客户端业务ID',
    `app_id`             BIGINT UNSIGNED NOT NULL COMMENT '所属应用业务ID',
    `audience_app_id`    BIGINT UNSIGNED NOT NULL COMMENT 'Token目标应用业务ID',
    `name`               VARCHAR(64)     NOT NULL COMMENT 'OAuth客户端名称',
    `client_secret_hash` VARCHAR(255)    NOT NULL COMMENT 'OAuth客户端密钥摘要',
    `grant_types`        VARCHAR(256)    NOT NULL COMMENT '允许的OAuth授权类型集合',
    `scopes`             VARCHAR(512)    NOT NULL COMMENT '允许申请的Scope集合',
    `redirect_uris`             JSON     NOT NULL COMMENT '允许的登录回调地址集合',
    `post_logout_redirect_uris` JSON     NOT NULL COMMENT '允许的登出回调地址集合',
    `status`             TINYINT         NOT NULL COMMENT '状态：1-启用，2-禁用',
    `create_time`        TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`        TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`         BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_oauth_client_id` (`oauth_client_id`, `is_deleted`) USING BTREE,
    KEY `idx_iam_oauth_client_app_status` (`app_id`, `status`, `is_deleted`, `id`) USING BTREE,
    KEY `idx_iam_oauth_client_audience_status` (`audience_app_id`, `status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM OAuth客户端表';

CREATE TABLE IF NOT EXISTS `db_iam_application_permission`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `permission_id`      BIGINT UNSIGNED NOT NULL COMMENT '权限业务ID',
    `app_id`             BIGINT UNSIGNED NOT NULL COMMENT '所属应用业务ID',
    `code`               VARCHAR(128)    NOT NULL COMMENT '权限编码',
    `name`               VARCHAR(64)     NOT NULL COMMENT '权限名称',
    `description`        VARCHAR(255)    NOT NULL COMMENT '权限说明',
    `status`             TINYINT         NOT NULL COMMENT '状态：1-启用，2-停用',
    `create_time`        TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`        TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`         BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_application_permission_id` (`permission_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_application_permission_app_code` (`app_id`, `code`, `is_deleted`) USING BTREE,
    KEY `idx_iam_application_permission_app_status` (`app_id`, `status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM应用权限目录表';

CREATE TABLE IF NOT EXISTS `db_iam_application_role`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `role_id`        BIGINT UNSIGNED NOT NULL COMMENT '角色业务ID',
    `app_id`         BIGINT UNSIGNED NOT NULL COMMENT '所属应用业务ID',
    `code`           VARCHAR(64)     NOT NULL COMMENT '角色编码',
    `name`           VARCHAR(64)     NOT NULL COMMENT '角色名称',
    `type`           TINYINT         NOT NULL COMMENT '角色类型：1-预置，2-自定义',
    `status`         TINYINT         NOT NULL COMMENT '状态：1-启用，2-停用',
    `create_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`     BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_application_role_id` (`role_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_application_role_app_code` (`app_id`, `code`, `is_deleted`) USING BTREE,
    KEY `idx_iam_application_role_app_status` (`app_id`, `status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM应用角色表';

CREATE TABLE IF NOT EXISTS `db_iam_internal_role`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `role_id`        BIGINT UNSIGNED NOT NULL COMMENT 'IAM内部角色业务ID',
    `code`           VARCHAR(64)     NOT NULL COMMENT 'IAM内部角色编码',
    `name`           VARCHAR(64)     NOT NULL COMMENT 'IAM内部角色名称',
    `type`           TINYINT         NOT NULL COMMENT '角色类型：1-超级管理员，2-自定义',
    `status`         TINYINT         NOT NULL COMMENT '状态：1-启用，2-停用',
    `permissions`    VARCHAR(1024)   NOT NULL COMMENT 'IAM内部权限编码集合',
    `create_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`    TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`     BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_internal_role_id` (`role_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_internal_role_code` (`code`, `is_deleted`) USING BTREE,
    KEY `idx_iam_internal_role_status` (`status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM内部角色表';

CREATE TABLE IF NOT EXISTS `db_iam_internal_administrator_role`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `administrator_id` BIGINT UNSIGNED NOT NULL COMMENT '管理员业务ID',
    `role_id`          BIGINT UNSIGNED NOT NULL COMMENT 'IAM内部角色业务ID',
    `create_time`      TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`      TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`       BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_internal_administrator_role` (`administrator_id`, `role_id`, `is_deleted`) USING BTREE,
    KEY `idx_iam_internal_administrator_role_role` (`role_id`, `is_deleted`, `administrator_id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM管理员内部角色关联表';

CREATE TABLE IF NOT EXISTS `db_iam_application_administrator_role`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `administrator_id` BIGINT UNSIGNED NOT NULL COMMENT '管理员业务ID',
    `role_id`          BIGINT UNSIGNED NOT NULL COMMENT '角色业务ID',
    `create_time`      TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`      TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`       BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_application_administrator_role` (`administrator_id`, `role_id`, `is_deleted`) USING BTREE,
    KEY `idx_iam_application_administrator_role_role` (`role_id`, `is_deleted`, `administrator_id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM管理员角色关联表';

CREATE TABLE IF NOT EXISTS `db_iam_application_role_permission`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色业务ID',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限业务ID',
    `create_time`   TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`    BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_application_role_permission` (`role_id`, `permission_id`, `is_deleted`) USING BTREE,
    KEY `idx_iam_application_role_permission_permission` (`permission_id`, `is_deleted`, `role_id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM角色权限关联表';

CREATE TABLE IF NOT EXISTS `db_iam_authorization`
(
    `id`                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '授权记录ID',
    `authorization_id`          VARCHAR(64)     NOT NULL COMMENT '稳定授权标识',
    `app_id`                    BIGINT UNSIGNED NOT NULL COMMENT '应用业务ID',
    `oauth_client_id`           VARCHAR(128)    NOT NULL COMMENT 'OAuth客户端业务ID',
    `principal_type`            VARCHAR(32)      NOT NULL COMMENT 'OAuth授权主体类型',
    `principal_name`            VARCHAR(64)     NOT NULL COMMENT '授权主体名称',
    `authorization_grant_type`  VARCHAR(64)     NOT NULL COMMENT '授权类型',
    `authorization_uri`         VARCHAR(512)    NULL COMMENT '授权端点地址，机器授权为空',
    `redirect_uri`              VARCHAR(512)    NULL COMMENT '本次授权回调地址，机器授权为空',
    `state`                     VARCHAR(256)    NULL COMMENT 'OAuth2 State',
    `code_challenge`            VARCHAR(128)    NULL COMMENT 'PKCE Challenge，机器授权为空',
    `code_challenge_method`     VARCHAR(16)     NULL COMMENT 'PKCE Challenge算法，机器授权为空',
    `authorization_code_digest` CHAR(64)        NULL COMMENT '一次性授权码摘要',
    `authorization_code_issued_at` TIMESTAMP(3)    NULL COMMENT '授权码签发时间',
    `authorization_code_expires_at` TIMESTAMP(3)   NULL COMMENT '授权码过期时间',
    `authorization_code_used_at` TIMESTAMP(3)      NULL COMMENT '授权码使用时间',
    `access_token_digest`       CHAR(64)        NULL COMMENT 'Access Token摘要',
    `access_token_issued_at`    TIMESTAMP(3)       NULL COMMENT 'Access Token签发时间',
    `access_token_expires_at`   TIMESTAMP(3)       NULL COMMENT 'Access Token过期时间',
    `access_token_claims`       JSON            NULL COMMENT 'Access Token非敏感Claims',
    `refresh_token_digest`      CHAR(64)        NULL COMMENT 'Refresh Token摘要',
    `refresh_token_issued_at`   TIMESTAMP(3)       NULL COMMENT 'Refresh Token签发时间',
    `refresh_token_expires_at`  TIMESTAMP(3)       NULL COMMENT 'Refresh Token过期时间',
    `id_token_digest`           CHAR(64)        NULL COMMENT 'OIDC ID Token摘要',
    `id_token_issued_at`        TIMESTAMP(3)       NULL COMMENT 'OIDC ID Token签发时间',
    `id_token_expires_at`       TIMESTAMP(3)       NULL COMMENT 'OIDC ID Token过期时间',
    `id_token_claims`           JSON            NULL COMMENT 'OIDC ID Token非敏感Claims',
    `requested_scope`           VARCHAR(512)    NULL COMMENT '授权请求Scope集合，机器授权为空',
    `scope`                     VARCHAR(512)    NOT NULL COMMENT '授权Scope集合',
    `status`                    TINYINT         NOT NULL COMMENT '状态：1-有效，2-撤销，3-过期',
    `revoked_at`                TIMESTAMP(3)       NULL COMMENT '撤销时间',
    `create_time`               TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`               TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `is_deleted`                BIGINT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，>0-删除时的主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_iam_authorization_identifier` (`authorization_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_authorization_code_digest` (`authorization_code_digest`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_authorization_access_digest` (`access_token_digest`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_authorization_refresh_digest` (`refresh_token_digest`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_iam_authorization_id_token_digest` (`id_token_digest`, `is_deleted`) USING BTREE,
    KEY `idx_iam_authorization_principal` (`principal_type`, `principal_name`, `status`, `is_deleted`, `id`) USING BTREE,
    KEY `idx_iam_authorization_oauth_client` (`oauth_client_id`, `status`, `is_deleted`, `id`) USING BTREE,
    KEY `idx_iam_authorization_status` (`status`, `is_deleted`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = 'IAM OAuth2授权表';
