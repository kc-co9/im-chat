package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleName;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalRoleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlIamRoleRepositoryTest {

    @Test
    void updateUsesDomainRowVersionInsteadOfLatestDatabaseVersion() {
        DbIamInternalRoleService service = mock(DbIamInternalRoleService.class);
        when(service.saveOrUpdate(any(DbIamInternalRole.class))).thenReturn(false);
        IamRole role = new IamRole(
                new IamRoleId(70L),
                new IamRoleCode("ADMIN"),
                new IamRoleName("Administrator"),
                IamRoleType.SUPER_ADMIN,
                IamRoleStatus.ACTIVE,
                Set.of());
        role.setPkId(7L);
        role.setRowVersion(2L);

        assertThatThrownBy(() -> new MysqlIamRoleRepository(service).save(role))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        ArgumentCaptor<DbIamInternalRole> entityCaptor =
                ArgumentCaptor.forClass(DbIamInternalRole.class);
        verify(service).saveOrUpdate(entityCaptor.capture());
        verify(service, never()).getById(any());
        assertThat(entityCaptor.getValue().getId()).isEqualTo(7L);
        assertThat(entityCaptor.getValue().getVersion()).isEqualTo(2L);
        assertThat(role.getRowVersion()).isEqualTo(2L);
    }
}
