package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamApplicationRoleType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamApplicationRoleStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接入应用角色数据库实体。 */
@Data
@TableName("db_iam_application_role")
@EqualsAndHashCode(callSuper = true)
public class DbIamApplicationRole extends BaseEntity {
    private Long roleId;
    private Long appId;
    private String code;
    private String name;
    private DbIamApplicationRoleType type;
    private DbIamApplicationRoleStatus status;
}
