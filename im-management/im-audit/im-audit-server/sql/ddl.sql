CREATE DATABASE IF NOT EXISTS `im_chat_audit`;

USE `im_chat_audit`;

CREATE TABLE IF NOT EXISTS `db_audit_event`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `audit_id`       VARCHAR(64)     NOT NULL COMMENT '全局审计业务标识',
    `type`           TINYINT         NOT NULL COMMENT '类别：1-业务，2-安全',
    `source_app`     VARCHAR(64)     NOT NULL COMMENT '来源应用',
    `action`         VARCHAR(128)    NOT NULL COMMENT '来源动作码',
    `actor_type`     VARCHAR(64)     NOT NULL COMMENT '操作者类型',
    `actor_id`       VARCHAR(128)    NULL COMMENT '操作者标识',
    `actor_name`     VARCHAR(128)    NULL COMMENT '操作者名称快照',
    `target_type`    VARCHAR(64)     NOT NULL COMMENT '目标类型',
    `target_id`      VARCHAR(128)    NULL COMMENT '目标标识',
    `outcome`        TINYINT         NOT NULL COMMENT '结果：1-成功，2-失败',
    `error_code`     VARCHAR(64)     NULL COMMENT '稳定失败码',
    `description`    VARCHAR(512)    NOT NULL COMMENT '安全审计说明',
    `client_address` VARCHAR(64)     NULL COMMENT '客户端地址',
    `user_agent`     VARCHAR(512)    NULL COMMENT '客户端标识',
    `trace_id`       VARCHAR(64)     NULL COMMENT '调用链标识',
    `attributes`     JSON            NOT NULL COMMENT '显式扩展属性',
    `occurred_at`    TIMESTAMP(3)    NOT NULL COMMENT '来源事件发生时间',
    `create_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    `update_time`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '标准更新时间，审计业务不使用',
    `is_deleted`     BIGINT          NOT NULL DEFAULT 0 COMMENT '标准逻辑删除字段，审计业务不使用',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_audit_event_audit` (`audit_id`) USING BTREE,
    KEY `idx_audit_event_source_time` (`source_app`, `occurred_at`, `id`) USING BTREE,
    KEY `idx_audit_event_actor_time` (`actor_id`, `occurred_at`, `id`) USING BTREE,
    KEY `idx_audit_event_target_time` (`target_type`, `target_id`, `occurred_at`, `id`) USING BTREE,
    KEY `idx_audit_event_type_action_outcome_time` (`type`, `action`, `outcome`, `occurred_at`, `id`) USING BTREE,
    KEY `idx_audit_event_trace` (`trace_id`, `id`) USING BTREE
) ENGINE = InnoDB COMMENT = '集中管理审计事实表';
