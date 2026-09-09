package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAppService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationDomainTransformer;

import java.util.Optional;
import java.util.List;
import java.util.Set;

/** 基于 MySQL 的 IAM 注册应用仓储。 */
public class MysqlApplicationRepository implements ApplicationRepository {
    private final DbIamAppService appService;

    public MysqlApplicationRepository(DbIamAppService appService) {
        this.appService = appService;
    }

    @Override
    public boolean contains(AppKey appKey) {
        return appService.count(
                Wrappers.lambdaQuery(DbIamApp.class)
                        .eq(DbIamApp::getAppKey, appKey.value())) > 0;
    }

    @Override
    public Optional<Application> find(AppKey appKey) {
        DbIamApp application = appService.getOne(
                Wrappers.lambdaQuery(DbIamApp.class)
                        .eq(DbIamApp::getAppKey, appKey.value())
                        .last("LIMIT 1"), false);
        return Optional.ofNullable(application).map(this::applicationFrom);
    }

    @Override
    public Optional<Application> find(AppId appId) {
        DbIamApp application = appService.getOne(
                Wrappers.lambdaQuery(DbIamApp.class)
                        .eq(DbIamApp::getAppId, appId.value())
                        .last("LIMIT 1"), false);
        return Optional.ofNullable(application)
                .map(this::applicationFrom);
    }

    @Override
    public List<Application> find(Set<AppId> appIds) {
        if (appIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = appIds.stream().map(AppId::value).toList();
        return appService.list(
                        Wrappers.lambdaQuery(DbIamApp.class)
                                .in(DbIamApp::getAppId, ids)
                                .orderByAsc(DbIamApp::getAppId))
                .stream()
                .map(this::applicationFrom)
                .toList();
    }

    @Override
    public PagingResult<Application> page(Paging paging) {
        IPage<DbIamApp> page = appService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                appService.getQueryWrapper().orderByDesc(DbIamApp::getId));
        return PagingResult.<Application>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream().map(this::applicationFrom).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public void save(Application app) {
        DbIamApp entity = ApplicationDomainTransformer.INSTANCE.dbApplicationFrom(app);
        if (app.getPkId() == null) {
            appService.save(entity);
            app.setPkId(entity.getId());
            return;
        }
        appService.updateById(entity);
    }

    private Application applicationFrom(DbIamApp entity) {
        return ApplicationDomainTransformer.INSTANCE.applicationFrom(entity);
    }
}
