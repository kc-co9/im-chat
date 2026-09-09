package com.co.kc.imchat.management.iam.domain.authorization.service;

import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationAdministratorRoleServiceTest {

    @Test
    void replacesOnlyRolesOwnedByTheRequestedApplication() {
        AdministratorId administratorId = new AdministratorId(100L);
        AppId appId = new AppId(1L);
        ApplicationRoleId currentRoleId = new ApplicationRoleId(11L);
        ApplicationRoleId otherAppRoleId = new ApplicationRoleId(21L);
        ApplicationRoleId requestedRoleId = new ApplicationRoleId(12L);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        AdministratorRepository administrators = mock(AdministratorRepository.class);
        ApplicationRoleRepository roles = mock(ApplicationRoleRepository.class);
        ApplicationAdministratorRoleRepository assignments =
                mock(ApplicationAdministratorRoleRepository.class);
        ApplicationRole currentRole = role(currentRoleId, appId);
        ApplicationRole otherAppRole = role(otherAppRoleId, new AppId(2L));
        ApplicationRole requestedRole = role(requestedRoleId, appId);
        when(applications.find(appId)).thenReturn(Optional.of(mock(Application.class)));
        when(administrators.find(administratorId)).thenReturn(Optional.of(mock(Administrator.class)));
        when(assignments.findRoles(administratorId))
                .thenReturn(Set.of(currentRoleId, otherAppRoleId));
        when(roles.findAll(Set.of(currentRoleId, otherAppRoleId)))
                .thenReturn(List.of(currentRole, otherAppRole));
        when(roles.findAll(Set.of(requestedRoleId)))
                .thenReturn(List.of(requestedRole));
        ApplicationAdministratorRoleService service = new ApplicationAdministratorRoleService(
                applications,
                administrators,
                roles,
                assignments);

        service.replace(administratorId, appId, Set.of(requestedRoleId));

        verify(assignments).replace(administratorId, Set.of(requestedRoleId, otherAppRoleId));
    }

    private ApplicationRole role(ApplicationRoleId roleId, AppId appId) {
        ApplicationRole role = mock(ApplicationRole.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getAppId()).thenReturn(appId);
        return role;
    }
}
