package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthSession;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.management.iam.transformer.domain.OAuthSessionDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 基于 MySQL 的 OAuth 授权会话仓储。 */
@RequiredArgsConstructor
public class MysqlOAuthSessionRepository implements OAuthSessionRepository {
    private final DbIamOAuthAuthorizationService authorizationService;
    private final DbIamAdministratorService administratorService;

    @Override
    public PagingResult<OAuthSession> pageActive(Paging paging) {
        IPage<DbIamOAuthAuthorization> page = authorizationService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                authorizationService.getQueryWrapper()
                        .eq(DbIamOAuthAuthorization::getStatus, DbIamOAuthAuthorizationStatus.ACTIVE)
                        .eq(DbIamOAuthAuthorization::getPrincipalType, "ADMINISTRATOR")
                        .orderByDesc(DbIamOAuthAuthorization::getUpdateTime)
                        .orderByDesc(DbIamOAuthAuthorization::getId));
        if (page.getRecords().isEmpty()) {
            return PagingResult.<OAuthSession>newBuilder()
                    .paging(paging)
                    .records(List.of())
                    .total(page.getTotal())
                    .build();
        }
        Set<Long> administratorIds = page.getRecords().stream()
                .map(DbIamOAuthAuthorization::getPrincipalName)
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        Map<Long, DbIamAdministrator> administrators = administratorService.list(
                        administratorService.getQueryWrapper()
                                .in(DbIamAdministrator::getAdministratorId, administratorIds))
                .stream()
                .collect(Collectors.toMap(
                        DbIamAdministrator::getAdministratorId,
                        Function.identity()));
        return PagingResult.<OAuthSession>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream()
                        .map(authorization -> OAuthSessionDomainTransformer.INSTANCE.oauthSessionFrom(
                                authorization,
                                administrator(administrators, authorization)))
                        .toList())
                .total(page.getTotal())
                .build();
    }

    private DbIamAdministrator administrator(
            Map<Long, DbIamAdministrator> administrators,
            DbIamOAuthAuthorization authorization
    ) {
        DbIamAdministrator administrator = administrators.get(
                Long.valueOf(authorization.getPrincipalName()));
        if (administrator == null) {
            throw new IllegalStateException("IAM administrator not found");
        }
        return administrator;
    }

    @Override
    public boolean revoke(OAuthAuthorizationId authorizationId, Instant revokedAt) {
        DbIamOAuthAuthorization update = revoked(revokedAt);
        return authorizationService.update(update, authorizationService.getUpdateWrapper()
                .eq(DbIamOAuthAuthorization::getAuthorizationId, authorizationId.value())
                .eq(DbIamOAuthAuthorization::getStatus, DbIamOAuthAuthorizationStatus.ACTIVE));
    }

    @Override
    public void revoke(AdministratorId administratorId, Instant revokedAt) {
        DbIamOAuthAuthorization update = revoked(revokedAt);
        authorizationService.update(update, authorizationService.getUpdateWrapper()
                .eq(DbIamOAuthAuthorization::getPrincipalType, "ADMINISTRATOR")
                .eq(DbIamOAuthAuthorization::getPrincipalName, administratorId.value().toString())
                .eq(DbIamOAuthAuthorization::getStatus, DbIamOAuthAuthorizationStatus.ACTIVE));
    }

    @Override
    public void revoke(OAuthClientId clientId, Instant revokedAt) {
        DbIamOAuthAuthorization update = revoked(revokedAt);
        authorizationService.update(update, authorizationService.getUpdateWrapper()
                .eq(DbIamOAuthAuthorization::getOauthClientId, clientId.value())
                .eq(DbIamOAuthAuthorization::getStatus, DbIamOAuthAuthorizationStatus.ACTIVE));
    }

    private DbIamOAuthAuthorization revoked(Instant revokedAt) {
        DbIamOAuthAuthorization authorization = new DbIamOAuthAuthorization();
        authorization.setStatus(DbIamOAuthAuthorizationStatus.REVOKED);
        authorization.setRevokedAt(revokedAt);
        return authorization;
    }
}
