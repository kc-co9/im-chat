package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleType;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IAM 内部角色数据库实体。 */
@Data
@TableName("db_iam_internal_role")
@EqualsAndHashCode(callSuper = true)
public class DbIamInternalRole extends BaseEntity {
    private Long roleId;
    private String code;
    private String name;
    private DbIamInternalRoleType type;
    private DbIamInternalRoleStatus status;
    private String permissions;
}
