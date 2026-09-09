package com.co.kc.imchat.management.iam.domain.authorization.service;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdministratorAuthorizationServiceTest {

    @Test
    void resolvesIamManagementPermissionsOnlyFromInternalRoles() {
        IamAdministratorRoleRepository administratorRoles = mock(IamAdministratorRoleRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        ApplicationAdministratorRoleRepository applicationAdministratorRoles =
                mock(ApplicationAdministratorRoleRepository.class);
        ApplicationRoleRepository applicationRoles = mock(ApplicationRoleRepository.class);
        ApplicationPermissionRepository applicationPermissions = mock(ApplicationPermissionRepository.class);
        AdministratorId administratorId = new AdministratorId(10L);
        IamRoleId roleId = new IamRoleId(20L);
        IamPermissionCode permission = new IamPermissionCode("iam:administrator:read");
        IamRole role = mock(IamRole.class);

        when(administratorRoles.findRoles(administratorId)).thenReturn(Set.of(roleId));
        when(roles.findAll(Set.of(roleId))).thenReturn(List.of(role));
        when(role.isActive()).thenReturn(true);
        when(role.getPermissions()).thenReturn(Set.of(permission));

        AdministratorAuthorizationService service = new AdministratorAuthorizationService(
                administratorRoles,
                roles,
                applicationAdministratorRoles,
                applicationRoles,
                applicationPermissions);

        assertThat(service.getPermissions(administratorId)).containsExactly(permission);
        verifyNoInteractions(applicationAdministratorRoles,
                applicationRoles, applicationPermissions);
    }
}
