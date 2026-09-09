package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthClientStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** IAM OAuth 客户端数据库实体。 */
@Data
@TableName("db_iam_oauth_client")
@EqualsAndHashCode(callSuper = true)
public class DbIamOAuthClient extends BaseEntity {
    private String oauthClientId;
    private Long appId;
    private Long audienceAppId;
    private String name;
    @ToString.Exclude
    private String clientSecretHash;
    private String grantTypes;
    private String scopes;
    private String redirectUris;
    private String postLogoutRedirectUris;
    private DbIamOAuthClientStatus status;
}
