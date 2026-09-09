CREATE DATABASE IF NOT EXISTS `im_chat_message`;

USE `im_chat_message`;

DROP TABLE IF EXISTS `db_im_private_chat`;
CREATE TABLE `db_im_private_chat`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `peer_user_id`         BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天的用户ID',
    `last_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最新消息ID',
    `read_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读消息ID',
    `read_time`            DATETIME        NULL COMMENT '已读时间',
    `unread_message_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '未读消息数量',
    `status`               TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-未知，1-正常，2-隐藏',
    `active_time`          DATETIME        NULL COMMENT '活跃时间',
    `create_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`           BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_id` (`chat_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_user_peer` (`user_id`, `peer_user_id`, `is_deleted`) USING BTREE
) ENGINE = InnoDB COMMENT = '私聊表';

DROP TABLE IF EXISTS `db_im_group_chat`;
CREATE TABLE `db_im_group_chat`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '用户群会话ID',
    `group_id`             BIGINT          NOT NULL DEFAULT 0 COMMENT '群ID',
    `user_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `group_alias`          VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户定义的群备注',
    `last_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最新消息ID',
    `read_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读消息ID',
    `unread_message_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '未读消息数量',
    `status`               TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-未知，1-正常，2-隐藏',
    `active_time`          DATETIME        NULL COMMENT '活跃时间',
    `create_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`           BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_id` (`chat_id`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`, `is_deleted`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE,
    KEY `idx_group_id` (`group_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊会话表';

DROP TABLE IF EXISTS `db_im_private_inbox_message`;
CREATE TABLE `db_im_private_inbox_message`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
    `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '收件箱所属用户ID',
    `token`        VARCHAR(90)     NOT NULL DEFAULT '' COMMENT '消息TOKEN',
    `sender_id`    BIGINT          NOT NULL DEFAULT 0 COMMENT '发送的用户ID',
    `type`         TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息,7-系统消息',
    `content`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '消息内容',
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态 0-未知 1-已收到 2-已读 3-已撤回',
    `send_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `receive_time` TIMESTAMP                DEFAULT NULL COMMENT '接收时间',
    `read_time`    TIMESTAMP                DEFAULT NULL COMMENT '已读时间',
    `revoke_time`  TIMESTAMP                DEFAULT NULL COMMENT '撤回时间',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_token` (`chat_id`, `token`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_chat_message` (`chat_id`, `message_id`, `is_deleted`) USING BTREE,
    KEY `idx_chat_user_message` (`chat_id`, `user_id`, `message_id`) USING BTREE,
    KEY `idx_user_message` (`user_id`, `message_id`) USING BTREE,
    KEY `idx_message_id` (`message_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '私聊收件箱消息表';

DROP TABLE IF EXISTS `db_im_group_inbox_message`;
CREATE TABLE `db_im_group_inbox_message`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
    `group_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '群ID',
    `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '用户群会话ID',
    `user_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '收件箱所属用户ID',
    `token`        VARCHAR(90)     NOT NULL DEFAULT '' COMMENT '消息TOKEN',
    `sender_id`    BIGINT          NOT NULL DEFAULT 0 COMMENT '发送的用户ID',
    `type`         TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息,7-系统消息',
    `content`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '消息内容',
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态 0-未知 1-已发送 2-已收到 3-已读 4-已撤回',
    `send_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `receive_time` TIMESTAMP                DEFAULT NULL COMMENT '接收时间',
    `read_time`    TIMESTAMP                DEFAULT NULL COMMENT '已读时间',
    `revoke_time`  TIMESTAMP                DEFAULT NULL COMMENT '撤回时间',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   BIGINT          NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，>0-已删除，删除时写入主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_user_token` (`chat_id`, `user_id`, `token`, `is_deleted`) USING BTREE,
    UNIQUE KEY `uk_chat_user_message` (`chat_id`, `user_id`, `message_id`, `is_deleted`) USING BTREE,
    KEY `idx_group_message` (`group_id`, `message_id`) USING BTREE,
    KEY `idx_user_chat_message` (`user_id`, `chat_id`, `message_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊收件箱消息表';
