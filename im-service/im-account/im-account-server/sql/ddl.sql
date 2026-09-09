CREATE DATABASE IF NOT EXISTS `im_chat_account`;

USE `im_chat_account`;

DROP TABLE IF EXISTS `db_user`;
CREATE TABLE `db_user`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `username`    VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户名',
    `email`       VARCHAR(45)     NOT NULL DEFAULT '' COMMENT '邮箱',
    `password`    VARCHAR(90)     NOT NULL DEFAULT '' COMMENT '密码',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '用户状态：1-正常，2-封禁',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user_id` (`user_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_email` (`email`, `is_deleted`) USING BTREE,
    KEY `idx_user_state` (`is_deleted`, `status`, `user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '用户表';
