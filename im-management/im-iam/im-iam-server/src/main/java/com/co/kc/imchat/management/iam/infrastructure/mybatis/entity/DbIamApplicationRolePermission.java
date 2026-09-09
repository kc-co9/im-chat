package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接入应用角色权限关联数据库实体。 */
@Data
@TableName("db_iam_application_role_permission")
@EqualsAndHashCode(callSuper = true)
public class DbIamApplicationRolePermission extends BaseEntity {
    private Long roleId;
    private Long permissionId;
}
