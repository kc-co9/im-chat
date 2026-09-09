package com.co.kc.imchat.management.iam.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthAuthorizationStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

/** OAuth 授权状态数据库实体。 */
@Data
@TableName(value = "db_iam_authorization", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class DbIamOAuthAuthorization extends BaseEntity {
    private String authorizationId;
    private Long appId;
    private String oauthClientId;
    private String principalType;
    private String principalName;
    private String authorizationGrantType;
    private String authorizationUri;
    private String redirectUri;
    private String state;
    private String codeChallenge;
    private String codeChallengeMethod;
    @ToString.Exclude
    private String authorizationCodeDigest;
    private Instant authorizationCodeIssuedAt;
    private Instant authorizationCodeExpiresAt;
    private Instant authorizationCodeUsedAt;
    @ToString.Exclude
    private String accessTokenDigest;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> accessTokenClaims;
    @ToString.Exclude
    private String refreshTokenDigest;
    private Instant refreshTokenIssuedAt;
    private Instant refreshTokenExpiresAt;
    @ToString.Exclude
    private String idTokenDigest;
    private Instant idTokenIssuedAt;
    private Instant idTokenExpiresAt;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> idTokenClaims;
    private String requestedScope;
    private String scope;
    private DbIamOAuthAuthorizationStatus status;
    private Instant revokedAt;
}
