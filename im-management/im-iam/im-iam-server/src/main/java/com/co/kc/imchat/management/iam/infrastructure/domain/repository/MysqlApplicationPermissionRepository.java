package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationPermissionDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

/** 基于 MySQL 的 IAM 权限目录仓储。 */
@RequiredArgsConstructor
public class MysqlApplicationPermissionRepository implements ApplicationPermissionRepository {
    private final DbIamApplicationPermissionService permissionService;
    private final ApplicationPermissionDomainTransformer transformer;
    private final DbIamApplicationRolePermissionService rolePermissionService;

    @Override
    public List<ApplicationPermission> find(AppId appId) {
        return permissionService.list(
                        Wrappers.lambdaQuery(DbIamApplicationPermission.class)
                                .eq(DbIamApplicationPermission::getAppId, appId.value()))
                .stream()
                .map(transformer::permissionFrom)
                .toList();
    }

    @Override
    public Optional<ApplicationPermission> find(ApplicationPermissionId permissionId) {
        DbIamApplicationPermission permission = permissionService.getOne(
                Wrappers.lambdaQuery(DbIamApplicationPermission.class)
                        .eq(DbIamApplicationPermission::getPermissionId, permissionId.value())
                        .last("LIMIT 1"), false);
        return Optional.ofNullable(permission).map(transformer::permissionFrom);
    }

    @Override
    public PagingResult<ApplicationPermission> page(
            AppId appId,
            ApplicationPermissionQueryCondition condition,
            Paging paging
    ) {
        LambdaQueryWrapper<DbIamApplicationPermission> query = permissionService.getQueryWrapper()
                .eq(DbIamApplicationPermission::getAppId, appId.value());
        condition.keyword().ifPresent(keyword -> query.and(keywordQuery -> keywordQuery
                .like(DbIamApplicationPermission::getCode, keyword)
                .or()
                .like(DbIamApplicationPermission::getName, keyword)));
        query.orderByAsc(DbIamApplicationPermission::getCode)
                .orderByAsc(DbIamApplicationPermission::getPermissionId);

        IPage<DbIamApplicationPermission> page = permissionService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                query);
        return PagingResult.<ApplicationPermission>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream().map(transformer::permissionFrom).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public boolean hasRoleAssignments(ApplicationPermissionId permissionId) {
        return rolePermissionService.count(
                Wrappers.lambdaQuery(DbIamApplicationRolePermission.class)
                        .eq(DbIamApplicationRolePermission::getPermissionId, permissionId.value())) > 0;
    }

    @Override
    public void saveAll(List<ApplicationPermission> permissions) {
        permissions.stream()
                .map(transformer::dbPermissionFrom)
                .forEach(this::save);
    }

    @Override
    public void remove(ApplicationPermission permission) {
        permissionService.remove(Wrappers.lambdaQuery(DbIamApplicationPermission.class)
                .eq(DbIamApplicationPermission::getPermissionId, permission.getId().value()));
    }

    private void save(DbIamApplicationPermission permission) {
        DbIamApplicationPermission stored = permissionService.getOne(
                Wrappers.lambdaQuery(DbIamApplicationPermission.class)
                        .eq(DbIamApplicationPermission::getPermissionId,
                                permission.getPermissionId())
                        .last("LIMIT 1"), false);
        if (stored == null) {
            permissionService.save(permission);
            return;
        }
        permission.setId(stored.getId());
        permissionService.updateById(permission);
    }
}
