package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamAdministratorStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** IAM 管理员数据库实体。 */
@Data
@TableName("db_iam_administrator")
@EqualsAndHashCode(callSuper = true)
public class DbIamAdministrator extends BaseEntity {
    private Long administratorId;
    private String username;
    private String email;
    @ToString.Exclude
    private String passwordHash;
    private DbIamAdministratorStatus status;
}
