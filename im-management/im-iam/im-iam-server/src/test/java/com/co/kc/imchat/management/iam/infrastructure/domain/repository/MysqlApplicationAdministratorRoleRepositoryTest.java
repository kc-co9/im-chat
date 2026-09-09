package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationAdministratorRoleService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlApplicationAdministratorRoleRepositoryTest {

    @Test
    void keepsExistingAssignmentsWithoutWrites() {
        DbIamApplicationAdministratorRoleService service = mockService(List.of(relation(10L, 1L)));
        MysqlApplicationAdministratorRoleRepository repository = new MysqlApplicationAdministratorRoleRepository(service);

        repository.replace(new AdministratorId(100L), Set.of(new ApplicationRoleId(1L)));

        verify(service, never()).remove(any(Wrapper.class));
        verify(service, never()).saveBatch(anyCollection());
    }

    @Test
    void insertsMissingAndRemovesExtraAssignmentsInBatches() {
        DbIamApplicationAdministratorRoleService service = mockService(List.of(
                relation(10L, 1L), relation(20L, 2L)));
        MysqlApplicationAdministratorRoleRepository repository = new MysqlApplicationAdministratorRoleRepository(service);

        repository.replace(new AdministratorId(100L), Set.of(new ApplicationRoleId(1L), new ApplicationRoleId(3L)));

        verify(service).remove(any(Wrapper.class));
        org.mockito.ArgumentCaptor<List> assignments = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(service).saveBatch(assignments.capture());
        List<?> saved = assignments.getValue();
        assertThat(saved).hasSize(1);
        DbIamApplicationAdministratorRole added = (DbIamApplicationAdministratorRole) saved.getFirst();
        assertThat(added.getAdministratorId()).isEqualTo(100L);
        assertThat(added.getRoleId()).isEqualTo(3L);
    }

    private static DbIamApplicationAdministratorRoleService mockService(List<DbIamApplicationAdministratorRole> current) {
        DbIamApplicationAdministratorRoleService service = mock(DbIamApplicationAdministratorRoleService.class);
        when(service.getQueryWrapper()).thenReturn(Wrappers.lambdaQuery());
        when(service.list(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<DbIamApplicationAdministratorRole>>any()))
                .thenReturn(current);
        return service;
    }

    private static DbIamApplicationAdministratorRole relation(Long id, Long roleId) {
        DbIamApplicationAdministratorRole relation = new DbIamApplicationAdministratorRole();
        relation.setId(id);
        relation.setAdministratorId(100L);
        relation.setRoleId(roleId);
        return relation;
    }
}
