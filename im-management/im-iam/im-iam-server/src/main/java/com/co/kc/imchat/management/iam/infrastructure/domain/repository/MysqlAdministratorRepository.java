package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.transformer.domain.AdministratorDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 基于 MySQL 的 IAM 管理员仓储。
 */
@RequiredArgsConstructor
public class MysqlAdministratorRepository implements AdministratorRepository {
    private final DbIamAdministratorService administratorService;

    @Override
    public Optional<Administrator> find(AdministratorUsername username) {
        return find(Wrappers
                .lambdaQuery(DbIamAdministrator.class)
                .eq(DbIamAdministrator::getUsername, username.value()));
    }

    @Override
    public Optional<Administrator> find(AdministratorEmail email) {
        return find(Wrappers
                .lambdaQuery(DbIamAdministrator.class)
                .eq(DbIamAdministrator::getEmail, email.value()));
    }

    private Optional<Administrator> find(LambdaQueryWrapper<DbIamAdministrator> query) {
        query.last("LIMIT 1");
        DbIamAdministrator administrator = administratorService.getOne(query, false);
        return Optional.ofNullable(administrator)
                .map(this::administratorFrom);
    }

    @Override
    public Optional<Administrator> find(AdministratorId administratorId) {
        return find(Wrappers.lambdaQuery(DbIamAdministrator.class)
                .eq(DbIamAdministrator::getAdministratorId, administratorId.value()));
    }

    @Override
    public void save(Administrator administrator) {
        DbIamAdministrator dbAdministrator =
                AdministratorDomainTransformer.INSTANCE.dbAdministratorFrom(administrator);
        if (administrator.getPkId() == null) {
            administratorService.save(dbAdministrator);
            administrator.setPkId(dbAdministrator.getId());
            return;
        }
        administratorService.updateById(dbAdministrator);
    }

    @Override
    public void remove(Administrator administrator) {
        administratorService.removeById(administrator.getPkId());
    }

    @Override
    public PagingResult<Administrator> page(Paging paging) {
        IPage<DbIamAdministrator> page = administratorService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                administratorService.getQueryWrapper().orderByDesc(DbIamAdministrator::getId));
        return PagingResult.<Administrator>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream().map(this::administratorFrom).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public boolean exists() {
        return administratorService.exists(Wrappers.lambdaQuery(DbIamAdministrator.class));
    }

    private Administrator administratorFrom(DbIamAdministrator entity) {
        Administrator administrator =
                AdministratorDomainTransformer.INSTANCE.administratorFrom(entity);
        administrator.setPkId(entity.getId());
        return administrator;
    }
}
