package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
                Wrappers.lambdaQuery(DbIamOAuthClient.class)
                        .eq(DbIamOAuthClient::getOauthClientId, clientId.value())) > 0;
    }

    @Override
    public Optional<OAuthClient> find(OAuthClientId clientId) {
        DbIamOAuthClient oauthClient = oauthClientService.getOne(
                Wrappers.lambdaQuery(DbIamOAuthClient.class)
                        .eq(DbIamOAuthClient::getOauthClientId, clientId.value())
                        .last("LIMIT 1"),
                false);
        return Optional.ofNullable(oauthClient).map(this::oauthClientFrom);
    }

    @Override
    public PagingResult<OAuthClient> page(AppId appId, Paging paging) {
        IPage<DbIamOAuthClient> page = oauthClientService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                Wrappers.lambdaQuery(DbIamOAuthClient.class)
                        .eq(DbIamOAuthClient::getAppId, appId.value())
                        .orderByAsc(DbIamOAuthClient::getOauthClientId)
                        .orderByAsc(DbIamOAuthClient::getId));
        return PagingResult.<OAuthClient>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream().map(this::oauthClientFrom).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public void save(OAuthClient oauthClient) {
        DbIamOAuthClient entity = OAuthClientDomainTransformer.INSTANCE.dbOAuthClientFrom(oauthClient);
        if (oauthClient.getPkId() == null) {
            oauthClientService.save(entity);
            oauthClient.setPkId(entity.getId());
            return;
        }
        oauthClientService.updateById(entity);
    }

    private OAuthClient oauthClientFrom(DbIamOAuthClient entity) {
        OAuthClient oauthClient = OAuthClientDomainTransformer.INSTANCE.oauthClientFrom(entity);
        oauthClient.setPkId(entity.getId());
        return oauthClient;
    }
}
