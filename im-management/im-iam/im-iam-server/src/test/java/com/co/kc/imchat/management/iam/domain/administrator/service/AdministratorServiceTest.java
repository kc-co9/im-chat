package com.co.kc.imchat.management.iam.domain.administrator.service;

import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorServiceTest {

    @Test
    void allowsRemovingAdministratorWithoutRoles() {
        IamRoleRepository roleRepository = mock(IamRoleRepository.class);
        IamAdministratorRoleRepository administratorRoleRepository =
                mock(IamAdministratorRoleRepository.class);
        AdministratorId administratorId = new AdministratorId(100L);
        when(administratorRoleRepository.findRoles(administratorId)).thenReturn(Set.of());
        when(roleRepository.findAll(Set.of())).thenReturn(java.util.List.of());
        AdministratorService service = new AdministratorService(
                roleRepository,
                administratorRoleRepository);

        assertThatCode(() -> service.ensureRemovable(administrator(administratorId)))
                .doesNotThrowAnyException();

        verify(administratorRoleRepository, never())
                .countActive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsRemovingAdministratorWithoutSuperAdministratorRole() {
        IamRoleRepository roleRepository = mock(IamRoleRepository.class);
        IamAdministratorRoleRepository administratorRoleRepository =
                mock(IamAdministratorRoleRepository.class);
        AdministratorId administratorId = new AdministratorId(100L);
        IamRoleId roleId = new IamRoleId(2L);
        IamRole customRole = mock(IamRole.class);
        when(customRole.isSuperAdmin()).thenReturn(false);
        when(administratorRoleRepository.findRoles(administratorId)).thenReturn(Set.of(roleId));
        when(roleRepository.findAll(Set.of(roleId))).thenReturn(java.util.List.of(customRole));
        AdministratorService service = new AdministratorService(
                roleRepository,
                administratorRoleRepository);

        assertThatCode(() -> service.ensureRemovable(administrator(administratorId)))
                .doesNotThrowAnyException();

        verify(administratorRoleRepository, never())
                .countActive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preventsRemovingTheOnlySuperAdministrator() {
        IamRoleRepository roleRepository = mock(IamRoleRepository.class);
        IamAdministratorRoleRepository administratorRoleRepository =
                mock(IamAdministratorRoleRepository.class);
        AdministratorId administratorId = new AdministratorId(100L);
        IamRoleId roleId = new IamRoleId(1L);
        IamRole superRole = mock(IamRole.class);
        when(superRole.isSuperAdmin()).thenReturn(true);
        when(superRole.getId()).thenReturn(roleId);
        when(administratorRoleRepository.findRoles(administratorId)).thenReturn(Set.of(roleId));
        when(roleRepository.findAll(Set.of(roleId))).thenReturn(java.util.List.of(superRole));
        when(administratorRoleRepository.countActive(roleId))
                .thenReturn(1L);
        AdministratorService service = new AdministratorService(
                roleRepository,
                administratorRoleRepository);

        assertThatThrownBy(() -> service.ensureRemovable(administrator(administratorId)))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("至少保留一个 IAM 超级管理员");
    }

    private Administrator administrator(AdministratorId id) {
        return Administrator.builder()
                .id(id)
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("encoded-password"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }
}
