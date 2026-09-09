package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDescription;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleType;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleCreateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleUpdateCmd;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ApplicationRoleAppServiceTest {

    @Test
    void createsRoleFromPermissionsOwnedByItsApplication() {
        CapturingApplicationRoleRepository roles = new CapturingApplicationRoleRepository();
        SnowflakeId ids = mock(SnowflakeId.class);
        when(ids.next()).thenReturn(20L);
        ApplicationRoleAppService service = new ApplicationRoleAppService(
                applicationRepository(), new ApplicationPermissionService(permissionRepository(), ids), roles, ids,
                mock(), mock(), mock());

        service.create(new ApplicationRoleCreateCmd(
                1L, "USER_MANAGER", "用户管理员", Set.of(10L)));

        assertThat(roles.saved.getAppId()).isEqualTo(new AppId(1L));
        assertThat(roles.saved.getPermissionIds()).containsExactly(new ApplicationPermissionId(10L));
    }

    @Test
    void rejectsPermissionOutsideTheApplicationCatalog() {
        ApplicationRoleAppService service = new ApplicationRoleAppService(
                applicationRepository(), new ApplicationPermissionService(permissionRepository(), mock(SnowflakeId.class)),
                new CapturingApplicationRoleRepository(), mock(SnowflakeId.class), mock(), mock(), mock());

        assertThatThrownBy(() -> service.create(new ApplicationRoleCreateCmd(
                1L, "BROKER_MANAGER", "Broker 管理员", Set.of(99L))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesRolePermissionsAndRevokesAffectedAdministratorSessions() {
        ApplicationRoleRepository roles = mock(ApplicationRoleRepository.class);
        ApplicationRole role = ApplicationRole.builder()
                .id(new ApplicationRoleId(20L))
                .appId(new AppId(1L))
                .code(new ApplicationRoleCode("AUDITOR"))
                .name(new com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleName("审计员"))
                .type(ApplicationRoleType.CUSTOM)
                .status(com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleStatus.ACTIVE)
                .permissionIds(Set.of())
                .build();
        when(roles.find(new ApplicationRoleId(20L))).thenReturn(Optional.of(role));
        ApplicationAdministratorRoleRepository assignments = mock(ApplicationAdministratorRoleRepository.class);
        AdministratorId administratorId = new AdministratorId(100L);
        when(assignments.findAdministrators(new ApplicationRoleId(20L)))
                .thenReturn(Set.of(administratorId));
        OAuthSessionRepository sessions = mock(OAuthSessionRepository.class);
        ApplicationRoleAppService service = new ApplicationRoleAppService(
                applicationRepository(),
                new ApplicationPermissionService(permissionRepository(), mock(SnowflakeId.class)),
                roles,
                mock(SnowflakeId.class),
                assignments,
                sessions,
                mock());

        service.update(new ApplicationRoleUpdateCmd(20L, "高级审计员", Set.of(10L)));

        assertThat(role.getName().value()).isEqualTo("高级审计员");
        assertThat(role.getPermissionIds()).containsExactly(new ApplicationPermissionId(10L));
        verify(roles).save(role);
        verify(sessions).revoke(eq(administratorId), any(Instant.class));
    }

    private static ApplicationRepository applicationRepository() {
        return new ApplicationRepository() {
            @Override
            public boolean contains(AppKey appKey) {
                return false;
            }

            @Override
            public Optional<Application> find(AppKey appKey) {
                return Optional.of(application());
            }

            @Override
            public Optional<Application> find(AppId appId) {
                return Optional.of(application());
            }

            @Override
            public List<Application> find(Set<AppId> appIds) {
                return List.of(application());
            }

            @Override
            public com.co.kc.imchat.common.model.page.PagingResult<Application> page(
                    com.co.kc.imchat.common.model.page.Paging paging
            ) {
                return new com.co.kc.imchat.common.model.page.PagingResult<>(
                        paging, List.of(application()), 1L);
            }

            @Override
            public void save(Application application) {
            }
        };
    }

    private static ApplicationPermissionRepository permissionRepository() {
        return new ApplicationPermissionRepository() {
            @Override
            public List<ApplicationPermission> find(AppId appId) {
                return List.of(permission());
            }

            @Override
            public Optional<ApplicationPermission> find(ApplicationPermissionId permissionId) {
                return Optional.of(permission());
            }

            @Override
            public com.co.kc.imchat.common.model.page.PagingResult<ApplicationPermission> page(
                    AppId appId,
                    com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition condition,
                    com.co.kc.imchat.common.model.page.Paging paging
            ) {
                return new com.co.kc.imchat.common.model.page.PagingResult<>(
                        paging, List.of(permission()), 1L);
            }

            @Override
            public boolean hasRoleAssignments(ApplicationPermissionId permissionId) {
                return false;
            }

            @Override
            public void saveAll(List<ApplicationPermission> permissions) {
            }

            @Override
            public void remove(ApplicationPermission permission) {
            }
        };
    }

    private static Application application() {
        return new Application(new AppId(1L), new AppKey("imAdmin"),
                new AppName("IM 管理后台"), AppStatus.ACTIVE);
    }

    private static ApplicationPermission permission() {
        return ApplicationPermission.builder()
                .id(new ApplicationPermissionId(10L))
                .appId(new AppId(1L))
                .code(new ApplicationPermissionCode("user:write"))
                .name(new ApplicationPermissionName("管理用户"))
                .description(new ApplicationPermissionDescription("管理普通用户"))
                .status(ApplicationPermissionStatus.ACTIVE)
                .build();
    }

    private static final class CapturingApplicationRoleRepository implements ApplicationRoleRepository {
        private ApplicationRole saved;

        @Override
        public boolean contains(AppId appId, ApplicationRoleCode code) {
            return false;
        }

        @Override
        public Optional<ApplicationRole> find(AppId appId, ApplicationRoleCode code) {
            return Optional.empty();
        }

        @Override
        public Optional<ApplicationRole> find(ApplicationRoleId roleId) {
            return Optional.empty();
        }

        @Override
        public List<ApplicationRole> findAll(Set<ApplicationRoleId> roleIds) {
            return List.of();
        }

        @Override
        public com.co.kc.imchat.common.model.page.PagingResult<ApplicationRole> page(
                AppId appId,
                com.co.kc.imchat.common.model.page.Paging paging
        ) {
            return com.co.kc.imchat.common.model.page.PagingResult.empty(paging);
        }

        @Override
        public void save(ApplicationRole role) {
            saved = role;
        }
    }
}
