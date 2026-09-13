package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalAdministratorRoleService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlIamAdministratorRoleRepositoryTest {

    @Test
    void failedBatchInsertAbortsAssignmentReplacement() {
        DbIamInternalAdministratorRoleService service = mock(DbIamInternalAdministratorRoleService.class);
        when(service.getQueryWrapper()).thenReturn(Wrappers.lambdaQuery());
        when(service.list(org.mockito.ArgumentMatchers
                .<LambdaQueryWrapper<DbIamInternalAdministratorRole>>any()))
                .thenReturn(List.of(relation(1L)));
        when(service.saveBatch(anyCollection())).thenReturn(false);
        MysqlIamAdministratorRoleRepository repository =
                new MysqlIamAdministratorRoleRepository(service);

        assertThatThrownBy(() -> repository.replace(
                new AdministratorId(100L),
                Set.of(new IamRoleId(1L), new IamRoleId(3L))))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }

    @Test
    void alreadyRemovedAssignmentsRemainIdempotent() {
        DbIamInternalAdministratorRoleService service = mock(DbIamInternalAdministratorRoleService.class);
        when(service.getQueryWrapper()).thenReturn(Wrappers.lambdaQuery());
        when(service.list(org.mockito.ArgumentMatchers
                .<LambdaQueryWrapper<DbIamInternalAdministratorRole>>any()))
                .thenReturn(List.of(relation(1L)));
        when(service.remove(any(Wrapper.class))).thenReturn(false);
        MysqlIamAdministratorRoleRepository repository =
                new MysqlIamAdministratorRoleRepository(service);

        org.assertj.core.api.Assertions.assertThatCode(() -> repository.replace(
                new AdministratorId(100L), Set.of()))
                .doesNotThrowAnyException();
    }

    private static DbIamInternalAdministratorRole relation(Long roleId) {
        DbIamInternalAdministratorRole relation = new DbIamInternalAdministratorRole();
        relation.setAdministratorId(100L);
        relation.setRoleId(roleId);
        return relation;
    }
}
