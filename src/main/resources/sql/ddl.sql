CREATE DATABASE IF NOT EXISTS `im_chat`;

USE `im_chat`;

DROP TABLE IF EXISTS `db_user`;
CREATE TABLE `db_user`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `username`    VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户名',
    `email`       VARCHAR(45)     NOT NULL DEFAULT '' COMMENT '邮箱',
    `password`    VARCHAR(90)     NOT NULL DEFAULT '' COMMENT '密码',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE,
    UNIQUE KEY `uk_email` (`email`) USING BTREE
) ENGINE = InnoDB COMMENT = '用户表';

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
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '好友表';

# 旧表
# DROP TABLE IF EXISTS `db_im_private_chat`;
# CREATE TABLE `db_im_private_chat`
# (
#     `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
#     `chat_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
#     `member1`     BIGINT          NOT NULL DEFAULT 0 COMMENT '成员ID_1',
#     `member2`     BIGINT          NOT NULL DEFAULT 0 COMMENT '成员ID_2',
#     `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
#     `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
#     PRIMARY KEY (`id`) USING BTREE,
#     UNIQUE KEY `uk_chat_id` (`chat_id`) USING BTREE
# ) ENGINE = InnoDB COMMENT = '私聊表';

DROP TABLE IF EXISTS `db_im_private_chat`;
CREATE TABLE `db_im_private_chat`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `peer_user_id` BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天的用户ID',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_id` (`chat_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '私聊表';

DROP TABLE IF EXISTS `db_im_private_chat_session`;
CREATE TABLE `db_im_private_chat_session`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `last_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最新消息ID',
    `read_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读消息ID',
    `unread_message_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '未读消息数量',
    `create_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_chatId_userId` (`chat_id`, `user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '私聊会话表';

DROP TABLE IF EXISTS `db_im_group_chat`;
CREATE TABLE `db_im_group_chat`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `owner_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '群主ID',
    `name`         VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '群名称',
    `notification` VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '群公告',
    `setting`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '群设置',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chat_id` (`chat_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊表';

DROP TABLE IF EXISTS `db_im_group_chat_session`;
CREATE TABLE `db_im_group_chat_session`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`              BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `last_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最新消息ID',
    `read_message_id`      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读消息ID',
    `unread_message_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '未读消息数量',
    `create_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_chatId_userId` (`chat_id`, `user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊会话表';

DROP TABLE IF EXISTS `db_im_group_member`;
CREATE TABLE `db_im_group_member`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chat_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
    `user_alias`  VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户别名',
    `group_alias` VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '用户定义的群别名',
    `setting`     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '用户群设置',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chatId_userId` (`chat_id`, `user_id`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊表';

# 旧表
# DROP TABLE IF EXISTS `db_im_private_message`;
# CREATE TABLE `db_im_private_message`
# (
#     `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
#     `message_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
#     `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
#     `token`        VARCHAR(45)     NOT NULL DEFAULT '' COMMENT '消息TOKEN',
#     `sender_id`    BIGINT          NOT NULL DEFAULT 0 COMMENT '发送的用户ID',
#     `receiver_id`  BIGINT          NOT NULL DEFAULT 0 COMMENT '接收的用户ID',
#     `type`         TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息',
#     `content`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '消息内容',
#     `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态 0-未知 1-已发送 2-已读 3-已撤回',
#     `send_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
#     `receive_time` TIMESTAMP                DEFAULT NULL COMMENT '接收时间',
#     `revoke_time`  TIMESTAMP                DEFAULT NULL COMMENT '撤回时间',
#     `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
#     `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
#     PRIMARY KEY (`id`) USING BTREE,
#     KEY `idx_message_id` (`message_id`) USING BTREE,
#     KEY `idx_chat_id` (`chat_id`) USING BTREE,
#     KEY `idx_token` (`token`) USING BTREE
# ) ENGINE = InnoDB COMMENT = '私聊消息表';

DROP TABLE IF EXISTS `db_im_private_receive_message`;
CREATE TABLE `db_im_private_receive_message`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
    `chat_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `user_id`      BIGINT          NOT NULL DEFAULT 0 COMMENT '接收用户ID',
    `token`        VARCHAR(45)     NOT NULL DEFAULT '' COMMENT '消息TOKEN',
    `sender_id`    BIGINT          NOT NULL DEFAULT 0 COMMENT '发送的用户ID',
    `type`         TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息',
    `content`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '消息内容',
    `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态 0-未知 1-已收到 2-已读 3-已撤回',
    `send_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `receive_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    `revoke_time`  TIMESTAMP                DEFAULT NULL COMMENT '撤回时间',
    `create_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_message_id` (`message_id`) USING BTREE,
    KEY `idx_chat_id` (`chat_id`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE,
    KEY `idx_token` (`token`) USING BTREE
) ENGINE = InnoDB COMMENT = '私聊消息接收表';

DROP TABLE IF EXISTS `db_im_group_receive_message`;
CREATE TABLE `db_im_group_receive_message`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
    `chat_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
    `token`       VARCHAR(45)     NOT NULL DEFAULT '' COMMENT '消息TOKEN',
    `sender_id`   BIGINT          NOT NULL DEFAULT 0 COMMENT '发送的用户ID',
    `type`        TINYINT         NOT NULL DEFAULT 0 COMMENT '消息类型 0-未知,1-文本消息,2-图片消息,3-语音消息,4-视频消息,5-文件消息,6-表情包消息',
    `content`     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '消息内容',
    `status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态 0-未知 1-已发送 2-已读 3-已撤回',
    `send_time`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `revoke_time` TIMESTAMP                DEFAULT NULL COMMENT '撤回时间',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_message_id` (`message_id`) USING BTREE,
    KEY `idx_chat_id` (`chat_id`) USING BTREE,
    KEY `idx_token` (`token`) USING BTREE
) ENGINE = InnoDB COMMENT = '群聊消息接收表';

# 旧表
# DROP TABLE IF EXISTS `db_im_chat_last_message`;
# CREATE TABLE `db_im_chat_last_message`
# (
#     `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
#     `chat_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
#     `chat_type`   TINYINT         NOT NULL DEFAULT 0 COMMENT '聊天类型',
#     `message_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
#     `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
#     `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
#     PRIMARY KEY (`id`) USING BTREE,
#     KEY `idx_chat_id` (`chat_id`) USING BTREE,
#     KEY `idx_message_id` (`message_id`) USING BTREE
# ) ENGINE = InnoDB COMMENT = '聊天最后消息表';

# 旧表
# DROP TABLE IF EXISTS `db_im_chat_last_message_read`;
# CREATE TABLE `db_im_chat_last_message_read`
# (
#     `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
#     `chat_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '聊天ID',
#     `user_id`     BIGINT          NOT NULL DEFAULT 0 COMMENT '用户ID',
#     `chat_type`   TINYINT         NOT NULL DEFAULT 0 COMMENT '聊天类型',
#     `message_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息ID',
#     `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
#     `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
#     PRIMARY KEY (`id`) USING BTREE,
#     KEY `idx_chat_id` (`chat_id`) USING BTREE,
#     KEY `idx_message_id` (`message_id`) USING BTREE
# ) ENGINE = InnoDB COMMENT = '聊天最后已读消息表';

