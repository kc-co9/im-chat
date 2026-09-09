package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthAuthorizationMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Optional;
import java.time.Instant;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthAuthorizationStatus;

/**
 * IAM 授权表 MyBatis 服务。
 */
@Service
public class DbIamOAuthAuthorizationService
        extends BaseMybatisService<DbIamOAuthAuthorizationMapper, DbIamOAuthAuthorization> {
    private static final String ID_TOKEN = "id_token";

    public Optional<DbIamOAuthAuthorization> getByAuthorizationId(String authorizationId) {
        return getFirst(getQueryWrapper().eq(DbIamOAuthAuthorization::getAuthorizationId, authorizationId));
    }

    public Optional<DbIamOAuthAuthorization> getByTokenDigest(
            String digest,
            OAuth2TokenType tokenType
    ) {
        return getFirst(getQueryWrapper().and(query -> {
            if (tokenType == null) {
                query.eq(DbIamOAuthAuthorization::getAuthorizationCodeDigest, digest)
                        .or().eq(DbIamOAuthAuthorization::getAccessTokenDigest, digest)
                        .or().eq(DbIamOAuthAuthorization::getRefreshTokenDigest, digest)
                        .or().eq(DbIamOAuthAuthorization::getIdTokenDigest, digest);
            } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
                query.eq(DbIamOAuthAuthorization::getAccessTokenDigest, digest);
            } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
                query.eq(DbIamOAuthAuthorization::getRefreshTokenDigest, digest);
            } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
                query.eq(DbIamOAuthAuthorization::getAuthorizationCodeDigest, digest);
            } else if (ID_TOKEN.equals(tokenType.getValue())) {
                query.eq(DbIamOAuthAuthorization::getIdTokenDigest, digest);
            } else {
                query.eq(DbIamOAuthAuthorization::getAuthorizationId, "");
            }
        }));
    }

    /**
     * 撤销指定 OAuth 授权及其当前 Token。
     */
    public void revoke(String authorizationId, Instant revokedAt) {
        DbIamOAuthAuthorization authorization = new DbIamOAuthAuthorization();
        authorization.setStatus(DbIamOAuthAuthorizationStatus.REVOKED);
        authorization.setRevokedAt(revokedAt);
        update(authorization, getUpdateWrapper()
                .eq(DbIamOAuthAuthorization::getAuthorizationId, authorizationId)
                .eq(DbIamOAuthAuthorization::getStatus, DbIamOAuthAuthorizationStatus.ACTIVE));
    }
}
