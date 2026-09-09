package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorRoleChangeCmd;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;

class AdministratorRoleUseCaseTest {

    @Test
    void preventsRemovingTheLastIamSuperAdministrator() {
        IamAdministratorRoleRepository grants = mock(IamAdministratorRoleRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        IamRole superRole = mock(IamRole.class);
        AdministratorId administratorId = new AdministratorId(100L);
        IamRoleId superRoleId = new IamRoleId(1L);
        when(superRole.isSuperAdmin()).thenReturn(true);
        when(superRole.getId()).thenReturn(superRoleId);
        when(grants.findRoles(administratorId)).thenReturn(Set.of(superRoleId));
        when(roles.findAll(Set.of(superRoleId))).thenReturn(List.of(superRole));
        when(grants.countActive(superRoleId)).thenReturn(1L);
        when(roles.findAll(Set.of())).thenReturn(List.of());
        AdministratorAppService service = new AdministratorAppService(
                mock(AdministratorRepository.class),
                mock(OAuthSessionRepository.class),
                mock(PasswordService.class),
                new AdministratorService(roles, grants),
                mock(AuthenticationRestriction.class),
                grants);

        assertThatThrownBy(() -> service.changeIamRoles(
                new AdministratorRoleChangeCmd(100L, Set.of())))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("至少保留一个 IAM 超级管理员");

        verify(grants, never()).replace(any(AdministratorId.class), anySet());
    }

    @Test
    void replacesRolesWhenAnotherSuperAdministratorRemains() {
        IamAdministratorRoleRepository grants = mock(IamAdministratorRoleRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        IamRole superRole = mock(IamRole.class);
        IamRole customRole = mock(IamRole.class);
        AdministratorId administratorId = new AdministratorId(100L);
        IamRoleId superRoleId = new IamRoleId(1L);
        IamRoleId customRoleId = new IamRoleId(2L);
        when(superRole.isSuperAdmin()).thenReturn(true);
        when(superRole.getId()).thenReturn(superRoleId);
        when(grants.findRoles(administratorId)).thenReturn(Set.of(superRoleId));
        when(roles.findAll(Set.of(superRoleId))).thenReturn(List.of(superRole));
        when(grants.countActive(superRoleId)).thenReturn(2L);
        when(roles.findAll(Set.of(customRoleId))).thenReturn(List.of(customRole));
        when(customRole.getId()).thenReturn(customRoleId);
        when(customRole.isActive()).thenReturn(true);
        when(customRole.getStatus()).thenReturn(IamRoleStatus.ACTIVE);
        AdministratorAppService service = new AdministratorAppService(
                mock(AdministratorRepository.class),
                mock(OAuthSessionRepository.class),
                mock(PasswordService.class),
                new AdministratorService(roles, grants),
                mock(AuthenticationRestriction.class),
                grants);

        service.changeIamRoles(new AdministratorRoleChangeCmd(100L, Set.of(2L)));

        verify(grants).replace(administratorId, Set.of(customRoleId));
    }

    @Test
    void rejectsMissingRoleBeforeReplacingAssignments() {
        IamAdministratorRoleRepository grants = mock(IamAdministratorRoleRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        AdministratorId administratorId = new AdministratorId(100L);
        when(roles.findAll(Set.of(new IamRoleId(2L)))).thenReturn(List.of());
        AdministratorAppService service = new AdministratorAppService(
                mock(AdministratorRepository.class),
                mock(OAuthSessionRepository.class),
                mock(PasswordService.class),
                new AdministratorService(roles, grants),
                mock(AuthenticationRestriction.class),
                grants);

        assertThatThrownBy(() -> service.changeIamRoles(
                new AdministratorRoleChangeCmd(100L, Set.of(2L))))
                .isInstanceOf(com.co.kc.imchat.common.exception.NotFoundException.class)
                .hasMessageContaining("角色不存在");

        verify(grants, never()).replace(any(AdministratorId.class), anySet());
    }

    @Test
    void rejectsInactiveRoleBeforeReplacingAssignments() {
        IamAdministratorRoleRepository grants = mock(IamAdministratorRoleRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        IamRole inactiveRole = mock(IamRole.class);
        AdministratorId administratorId = new AdministratorId(100L);
        IamRoleId roleId = new IamRoleId(2L);
        when(roles.findAll(Set.of(roleId))).thenReturn(List.of(inactiveRole));
        when(inactiveRole.isActive()).thenReturn(false);
        AdministratorAppService service = new AdministratorAppService(
                mock(AdministratorRepository.class),
                mock(OAuthSessionRepository.class),
                mock(PasswordService.class),
                new AdministratorService(roles, grants),
                mock(AuthenticationRestriction.class),
                grants);

        assertThatThrownBy(() -> service.changeIamRoles(
                new AdministratorRoleChangeCmd(100L, Set.of(2L))))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("角色已停用");

        verify(grants, never()).replace(any(AdministratorId.class), anySet());
    }
}
