package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

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
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationPermissionDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

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
                permissionService.getQueryWrapper()
                        .eq(DbIamApplicationPermission::getAppId, appId.value()))
                .stream()
                .map(transformer::permissionFrom)
                .toList();
    }

    @Override
    public Optional<ApplicationPermission> find(ApplicationPermissionId permissionId) {
        return permissionService.getFirst(
                        permissionService.getQueryWrapper()
                                .eq(DbIamApplicationPermission::getPermissionId, permissionId.value()))
                .map(transformer::permissionFrom);
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
                rolePermissionService.getQueryWrapper()
                        .eq(DbIamApplicationRolePermission::getPermissionId, permissionId.value())) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(List<ApplicationPermission> permissions) {
        List<PermissionWrite> writes = permissions.stream()
                .map(permission -> new PermissionWrite(
                        permission,
                        transformer.dbPermissionFrom(permission)))
                .toList();
        List<PermissionWrite> updates = writes.stream()
                .filter(write -> write.permission().getPkId() != null)
                .toList();
        for (PermissionWrite update : updates) {
            if (!permissionService.updateById(update.row())) {
                throw new OptimisticLockingFailureException(
                        "Application permission was modified concurrently: "
                                + update.permission().getId().value());
            }
        }
        List<DbIamApplicationPermission> inserts = writes.stream()
                .filter(write -> write.permission().getPkId() == null)
                .map(PermissionWrite::row)
                .toList();
        if (!inserts.isEmpty() && !permissionService.saveBatch(inserts)) {
            throw new DataAccessResourceFailureException(
                    "Application permissions were not inserted");
        }
        writes.forEach(PermissionWrite::updatePersistenceState);
    }

    @Override
    public void remove(ApplicationPermission permission) {
        permissionService.remove(permissionService.getQueryWrapper()
                .eq(DbIamApplicationPermission::getPermissionId, permission.getId().value()));
    }

    private record PermissionWrite(
            ApplicationPermission permission,
            DbIamApplicationPermission row
    ) {
        private void updatePersistenceState() {
            if (permission.getPkId() == null) {
                permission.setPkId(row.getId());
            }
            permission.setRowVersion(row.getVersion());
        }
    }

}
