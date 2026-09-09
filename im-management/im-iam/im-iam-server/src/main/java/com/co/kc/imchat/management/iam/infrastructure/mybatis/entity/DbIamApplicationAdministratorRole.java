package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接入应用管理员角色关联数据库实体。 */
@Data
@TableName("db_iam_application_administrator_role")
@EqualsAndHashCode(callSuper = true)
public class DbIamApplicationAdministratorRole extends BaseEntity {
    private Long administratorId;
    private Long roleId;
}
