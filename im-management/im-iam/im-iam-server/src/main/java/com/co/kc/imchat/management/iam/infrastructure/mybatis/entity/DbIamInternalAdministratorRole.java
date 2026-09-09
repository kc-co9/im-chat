package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IAM 管理员内部角色关联数据库实体。 */
@Data
@TableName("db_iam_internal_administrator_role")
@EqualsAndHashCode(callSuper = true)
public class DbIamInternalAdministratorRole extends BaseEntity {
    private Long administratorId;
    private Long roleId;
}
