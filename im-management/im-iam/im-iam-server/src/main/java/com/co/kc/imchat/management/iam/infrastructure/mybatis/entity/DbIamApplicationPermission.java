package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamApplicationPermissionStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接入应用权限数据库实体。 */
@Data
@TableName("db_iam_application_permission")
@EqualsAndHashCode(callSuper = true)
public class DbIamApplicationPermission extends BaseEntity {
    private Long permissionId;
    private Long appId;
    private String code;
    private String name;
    private String description;
    private DbIamApplicationPermissionStatus status;
}
