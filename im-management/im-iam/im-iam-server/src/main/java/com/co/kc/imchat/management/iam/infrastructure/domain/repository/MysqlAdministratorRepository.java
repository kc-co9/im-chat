package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

/**
 * 基于 MySQL 的 IAM 管理员仓储。
 */
@RequiredArgsConstructor
public class MysqlAdministratorRepository implements AdministratorRepository {
    private final DbIamAdministratorService administratorService;

    @Override
    public Optional<Administrator> find(AdministratorUsername username) {
        return find(administratorService.getQueryWrapper()
                .eq(DbIamAdministrator::getUsername, username.value()));
    }

    @Override
    public Optional<Administrator> find(AdministratorEmail email) {
        return find(administratorService.getQueryWrapper()
                .eq(DbIamAdministrator::getEmail, email.value()));
    }

    private Optional<Administrator> find(LambdaQueryWrapper<DbIamAdministrator> query) {
        return administratorService.getFirst(query)
                .map(AdministratorDomainTransformer.INSTANCE::administratorFrom);
    }

    @Override
    public Optional<Administrator> find(AdministratorId administratorId) {
        return find(administratorService.getQueryWrapper()
                .eq(DbIamAdministrator::getAdministratorId, administratorId.value()));
    }

    @Override
    public void save(Administrator administrator) {
        DbIamAdministrator dbAdministrator =
                AdministratorDomainTransformer.INSTANCE.dbAdministratorFrom(administrator);
        if (administrator.getPkId() == null) {
            if (!administratorService.save(dbAdministrator)) {
                throw new DataAccessResourceFailureException(
                        "Administrator was not inserted: " + administrator.getId().value());
            }
            administrator.setPkId(dbAdministrator.getId());
            administrator.setRowVersion(dbAdministrator.getVersion());
            return;
        }
        if (!administratorService.updateById(dbAdministrator)) {
            throw new OptimisticLockingFailureException(
                    "Administrator was modified concurrently: " + administrator.getId().value());
        }
        administrator.setRowVersion(dbAdministrator.getVersion());
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
                .records(page.getRecords().stream()
                        .map(AdministratorDomainTransformer.INSTANCE::administratorFrom)
                        .toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public boolean exists() {
        return administratorService.exists(administratorService.getQueryWrapper());
    }

}
