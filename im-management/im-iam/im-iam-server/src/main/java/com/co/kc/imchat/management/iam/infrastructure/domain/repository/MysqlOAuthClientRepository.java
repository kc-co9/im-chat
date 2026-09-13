package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthClientService;
import com.co.kc.imchat.management.iam.transformer.domain.OAuthClientDomainTransformer;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

/** 基于 MySQL 的 IAM 机器客户端仓储。 */
public class MysqlOAuthClientRepository implements OAuthClientRepository {
    private final DbIamOAuthClientService oauthClientService;

    public MysqlOAuthClientRepository(DbIamOAuthClientService oauthClientService) {
        this.oauthClientService = oauthClientService;
    }

    @Override
    public boolean contains(OAuthClientId clientId) {
        return oauthClientService.count(
                oauthClientService.getQueryWrapper()
                        .eq(DbIamOAuthClient::getOauthClientId, clientId.value())) > 0;
    }

    @Override
    public Optional<OAuthClient> find(OAuthClientId clientId) {
        return oauthClientService.getFirst(
                        oauthClientService.getQueryWrapper()
                                .eq(DbIamOAuthClient::getOauthClientId, clientId.value()))
                .map(OAuthClientDomainTransformer.INSTANCE::oauthClientFrom);
    }

    @Override
    public PagingResult<OAuthClient> page(AppId appId, Paging paging) {
        IPage<DbIamOAuthClient> page = oauthClientService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                oauthClientService.getQueryWrapper()
                        .eq(DbIamOAuthClient::getAppId, appId.value())
                        .orderByAsc(DbIamOAuthClient::getOauthClientId)
                        .orderByAsc(DbIamOAuthClient::getId));
        return PagingResult.<OAuthClient>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream()
                        .map(OAuthClientDomainTransformer.INSTANCE::oauthClientFrom)
                        .toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public void save(OAuthClient oauthClient) {
        DbIamOAuthClient entity = OAuthClientDomainTransformer.INSTANCE.dbOAuthClientFrom(oauthClient);
        if (oauthClient.getPkId() == null) {
            if (!oauthClientService.save(entity)) {
                throw new DataAccessResourceFailureException(
                        "OAuth client was not inserted: " + oauthClient.getClientId().value());
            }
            oauthClient.setPkId(entity.getId());
            oauthClient.setRowVersion(entity.getVersion());
            return;
        }
        if (!oauthClientService.updateById(entity)) {
            throw new OptimisticLockingFailureException(
                    "OAuth client was modified concurrently: " + oauthClient.getClientId().value());
        }
        oauthClient.setRowVersion(entity.getVersion());
    }

}
