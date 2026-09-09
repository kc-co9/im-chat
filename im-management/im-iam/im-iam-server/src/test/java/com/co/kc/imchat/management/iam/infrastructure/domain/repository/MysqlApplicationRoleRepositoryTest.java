package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRoleService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationRoleDomainTransformer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlApplicationRoleRepositoryTest {

    @Test
    void savesRoleAndReplacesItsPermissionLinks() {
        DbIamApplicationRoleService roleService = mock(DbIamApplicationRoleService.class);
        DbIamApplicationRolePermissionService linkService = mock(DbIamApplicationRolePermissionService.class);
        ApplicationRoleDomainTransformer transformer = mock(ApplicationRoleDomainTransformer.class);
        ApplicationRole role = mock(ApplicationRole.class);
        DbIamApplicationRole row = new DbIamApplicationRole();
        row.setId(20L);
        when(role.getPkId()).thenReturn(null);
        when(role.getId()).thenReturn(new ApplicationRoleId(100L));
        when(role.getPermissionIds()).thenReturn(Set.of(new ApplicationPermissionId(10L), new ApplicationPermissionId(11L)));
        when(transformer.dbRoleFrom(role)).thenReturn(row);
        MysqlApplicationRoleRepository repository = new MysqlApplicationRoleRepository(
                roleService, linkService, transformer);

        repository.save(role);

        verify(roleService).save(row);
        verify(role).setPkId(20L);
        verify(linkService).remove(any());
        verify(linkService, atLeastOnce()).save(any(DbIamApplicationRolePermission.class));
    }

    @Test
    void restoresTechnicalPrimaryKeyAfterDomainMapping() {
        DbIamApplicationRoleService roleService = mock(DbIamApplicationRoleService.class);
        DbIamApplicationRolePermissionService linkService = mock(DbIamApplicationRolePermissionService.class);
        ApplicationRoleDomainTransformer transformer = mock(ApplicationRoleDomainTransformer.class);
        ApplicationRole role = mock(ApplicationRole.class);
        DbIamApplicationRole row = new DbIamApplicationRole();
        row.setId(20L);
        when(roleService.getOne(any(), any(Boolean.class))).thenReturn(row);
        when(linkService.list(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(transformer.roleFrom(row, Set.of())).thenReturn(role);
        MysqlApplicationRoleRepository repository = new MysqlApplicationRoleRepository(
                roleService, linkService, transformer);

        repository.find(new AppId(1L), new ApplicationRoleCode("ADMIN"));

        verify(role).setPkId(20L);
    }
}
