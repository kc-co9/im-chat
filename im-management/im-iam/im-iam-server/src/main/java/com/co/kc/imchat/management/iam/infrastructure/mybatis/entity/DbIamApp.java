package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamAppStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IAM 接入应用数据库实体。 */
@Data
@TableName("db_iam_app")
@EqualsAndHashCode(callSuper = true)
public class DbIamApp extends BaseEntity {
    private Long appId;
    private String appKey;
    private String name;
    private DbIamAppStatus status;
}
