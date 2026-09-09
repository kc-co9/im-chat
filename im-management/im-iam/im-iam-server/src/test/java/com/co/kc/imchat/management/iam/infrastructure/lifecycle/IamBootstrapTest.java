package com.co.kc.imchat.management.iam.infrastructure.lifecycle;

import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamBootstrapProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamBootstrapTest {

    @Test
    void createsTheFirstAdministratorAndGrantsTheIamSuperRole() throws Exception {
        AdministratorRepository administrators = mock(AdministratorRepository.class);
        IamRoleRepository roles = mock(IamRoleRepository.class);
        IamAdministratorRoleRepository grants = mock(IamAdministratorRoleRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        SnowflakeId ids = mock(SnowflakeId.class);
        IamRole superRole = mock(IamRole.class);
        when(administrators.exists()).thenReturn(false);
        when(roles.find(IamRoleType.SUPER_ADMIN)).thenReturn(Optional.of(superRole));
        when(superRole.getId()).thenReturn(new IamRoleId(2L));
        when(passwords.encrypt(any(AdministratorRawPassword.class)))
                .thenReturn(new AdministratorPassword("digest"));
        when(ids.next()).thenReturn(100L);
        IamBootstrap bootstrap = new IamBootstrap(
                new IamBootstrapProperties(
                        true, "admin", "admin@example.com", "strong-password"),
                administrators, roles, grants, passwords, ids);

        bootstrap.run(null);

        verify(administrators).save(any(Administrator.class));
        verify(grants).assign(
                new AdministratorId(100L),
                new IamRoleId(2L));
    }

    @Test
    void ignoresBootstrapWhenAdministratorStorageIsInitialized() throws Exception {
        AdministratorRepository administrators = mock(AdministratorRepository.class);
        when(administrators.exists()).thenReturn(true);
        IamBootstrap bootstrap = new IamBootstrap(
                new IamBootstrapProperties(
                        true, "admin", "admin@example.com", "strong-password"),
                administrators,
                mock(IamRoleRepository.class),
                mock(IamAdministratorRoleRepository.class),
                mock(PasswordService.class),
                mock(SnowflakeId.class));

        bootstrap.run(null);

        verify(administrators, never()).save(any());
    }
}
