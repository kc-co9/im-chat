CREATE DATABASE IF NOT EXISTS `im_chat_social`;

USE `im_chat_social`;

DROP TABLE IF EXISTS `db_friend`;
CREATE TABLE `db_friend`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `friend_user_id` BIGINT          NOT NULL DEFAULT 0 COMMENT '好友ID',
    `friend_alias`   VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '好友别名',
    `friend_status`  TINYINT         NOT NULL DEFAULT 0 COMMENT '好友状态 0-未知, 1-正常, 2-拉黑 3-删除',
    `create_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_user_id`, `is_deleted`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '好友表';

DROP TABLE IF EXISTS `db_im_group`;
CREATE TABLE `db_im_group`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '群ID',
    `owner_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '群主ID',
    `name`         VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '群名称',
    `notification` VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '群公告',
    `member_count` INT             NOT NULL DEFAULT 0 COMMENT '群人数',
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '群状态 0-未知,1-正常,2-已解散',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_group_id` (`group_id`, `is_deleted`) USING BTREE
) ENGINE = InnoDB COMMENT = '群表';

DROP TABLE IF EXISTS `db_im_group_member`;
CREATE TABLE `db_im_group_member`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id`    BIGINT          NOT NULL DEFAULT 0 COMMENT '群ID',
    `user_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '成员用户ID',
    `user_alias`  VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户群昵称',
    `join_time`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入群时间',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`, `is_deleted`) USING BTREE,
    KEY `idx_group_id` (`group_id`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群成员表';
