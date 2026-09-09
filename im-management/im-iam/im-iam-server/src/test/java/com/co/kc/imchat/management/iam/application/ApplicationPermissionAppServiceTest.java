package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDescription;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDefinitionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPermissionPageQuery;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationPermissionAppServiceTest {

    @Test
    void pageVerifiesApplicationAndPassesTrimmedKeywordCondition() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        ApplicationPermissionRepository permissions = mock(ApplicationPermissionRepository.class);
        Paging paging = new Paging(2, 10);
        when(applications.find(new AppId(1L))).thenReturn(Optional.of(application()));
        when(permissions.page(
                new AppId(1L),
                new ApplicationPermissionQueryCondition(Optional.of("user")),
                paging)).thenReturn(new PagingResult<>(paging, List.of(), 0L));
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                applications,
                mock(OAuthClientRepository.class),
                permissions,
                mock(ApplicationPermissionService.class));

        PagingResult<?> result = service.page(new ApplicationPermissionPageQuery(
                1L,
                "  user  ",
                paging));

        assertThat(result).isEqualTo(new PagingResult<>(paging, List.of(), 0L));
        verify(permissions).page(
                new AppId(1L),
                new ApplicationPermissionQueryCondition(Optional.of("user")),
                paging);
    }

    @Test
    void pagePassesEmptyKeywordConditionWhenKeywordIsOmitted() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        ApplicationPermissionRepository permissions = mock(ApplicationPermissionRepository.class);
        Paging paging = new Paging(1, 20);
        when(applications.find(new AppId(1L))).thenReturn(Optional.of(application()));
        when(permissions.page(
                new AppId(1L),
                new ApplicationPermissionQueryCondition(Optional.empty()),
                paging)).thenReturn(new PagingResult<>(paging, List.of(), 0L));
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                applications,
                mock(OAuthClientRepository.class),
                permissions,
                mock(ApplicationPermissionService.class));

        service.page(new ApplicationPermissionPageQuery(1L, null, paging));

        verify(permissions).page(
                new AppId(1L),
                new ApplicationPermissionQueryCondition(Optional.empty()),
                paging);
    }

    @Test
    void pageRejectsUnknownApplicationBeforeQueryingPermissions() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        ApplicationPermissionRepository permissions = mock(ApplicationPermissionRepository.class);
        Paging paging = new Paging(1, 20);
        when(applications.find(new AppId(404L))).thenReturn(Optional.empty());
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                applications,
                mock(OAuthClientRepository.class),
                permissions,
                mock(ApplicationPermissionService.class));

        assertThatThrownBy(() -> service.page(
                new ApplicationPermissionPageQuery(404L, "user", paging)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("IAM 应用不存在");
        verifyNoInteractions(permissions);
    }

    @Test
    void synchronizesAFullSnapshotAndMarksMissingPermissionsInactive() {
        MemoryApplicationRepository applications = new MemoryApplicationRepository(application());
        MemoryPermissionRepository permissions = new MemoryPermissionRepository(List.of(
                permission(10L, "user:read"),
                permission(11L, "user:delete")));
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                applications, oauthClientRepository(), permissions,
                new ApplicationPermissionService(permissions, snowflakeId(12L)));

        service.synchronize(new ApplicationPermissionCatalogSyncCmd(
                "im-admin-client",
                List.of(
                        new ApplicationPermissionDefinitionDTO("user:read", "查询用户", "查询普通用户"),
                        new ApplicationPermissionDefinitionDTO("role:read", "查询角色", "查询角色配置"))));

        assertThat(permissions.permissions)
                .filteredOn(permission -> permission.getCode().equals(new ApplicationPermissionCode("user:delete")))
                .singleElement()
                .extracting(ApplicationPermission::getStatus)
                .isEqualTo(ApplicationPermissionStatus.INACTIVE);
        assertThat(permissions.permissions)
                .filteredOn(permission -> permission.getCode().equals(new ApplicationPermissionCode("role:read")))
                .singleElement()
                .extracting(ApplicationPermission::getStatus)
                .isEqualTo(ApplicationPermissionStatus.ACTIVE);
    }

    @Test
    void repeatedSnapshotKeepsTheSamePermissionIdentity() {
        MemoryApplicationRepository applications = new MemoryApplicationRepository(application());
        MemoryPermissionRepository permissions = new MemoryPermissionRepository(List.of(
                permission(10L, "user:read")));
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                applications, oauthClientRepository(), permissions,
                new ApplicationPermissionService(permissions, snowflakeId(12L)));
        ApplicationPermissionCatalogSyncCmd command = new ApplicationPermissionCatalogSyncCmd(
                "im-admin-client", List.of(
                new ApplicationPermissionDefinitionDTO("user:read", "查询用户", "查询普通用户")));

        service.synchronize(command);
        service.synchronize(command);

        assertThat(permissions.permissions).singleElement()
                .extracting(ApplicationPermission::getId)
                .isEqualTo(new ApplicationPermissionId(10L));
    }

    private static Application application() {
        return new Application(new AppId(1L), new AppKey("imAdmin"),
                new AppName("IM 管理后台"), AppStatus.ACTIVE);
    }

    private static OAuthClientRepository oauthClientRepository() {
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthClient client = mock(OAuthClient.class);
        when(client.getAppId()).thenReturn(new AppId(1L));
        when(clients.find(new OAuthClientId("im-admin-client")))
                .thenReturn(Optional.of(client));
        return clients;
    }

    private static ApplicationPermission permission(long id, String code) {
        return ApplicationPermission.builder()
                .id(new ApplicationPermissionId(id))
                .appId(new AppId(1L))
                .code(new ApplicationPermissionCode(code))
                .name(new ApplicationPermissionName(code))
                .description(new ApplicationPermissionDescription(code))
                .status(ApplicationPermissionStatus.ACTIVE)
                .build();
    }

    private static SnowflakeId snowflakeId(long id) {
        SnowflakeId snowflakeId = mock(SnowflakeId.class);
        when(snowflakeId.next()).thenReturn(id);
        return snowflakeId;
    }

    private static final class MemoryApplicationRepository implements ApplicationRepository {
        private final Application application;

        private MemoryApplicationRepository(Application application) {
            this.application = application;
        }

        @Override
        public boolean contains(AppKey appKey) {
            return false;
        }

        @Override
        public Optional<Application> find(AppKey appKey) {
            return Optional.of(application);
        }

        @Override
        public Optional<Application> find(AppId appId) {
            return Optional.of(application);
        }

        @Override
        public List<Application> find(Set<AppId> appIds) {
            return List.of(application);
        }

        @Override
        public com.co.kc.imchat.common.model.page.PagingResult<Application> page(
                com.co.kc.imchat.common.model.page.Paging paging
        ) {
            return new com.co.kc.imchat.common.model.page.PagingResult<>(
                    paging, List.of(application), 1L);
        }

        @Override
        public void save(Application application) {
        }
    }

    private static final class MemoryPermissionRepository implements ApplicationPermissionRepository {
        private List<ApplicationPermission> permissions;

        private MemoryPermissionRepository(List<ApplicationPermission> permissions) {
            this.permissions = new ArrayList<>(permissions);
        }

        @Override
        public List<ApplicationPermission> find(AppId appId) {
            return List.copyOf(permissions);
        }

        @Override
        public Optional<ApplicationPermission> find(ApplicationPermissionId permissionId) {
            return permissions.stream()
                    .filter(permission -> permission.getId().equals(permissionId))
                    .findFirst();
        }

        @Override
        public com.co.kc.imchat.common.model.page.PagingResult<ApplicationPermission> page(
                AppId appId,
                ApplicationPermissionQueryCondition condition,
                com.co.kc.imchat.common.model.page.Paging paging
        ) {
            return new com.co.kc.imchat.common.model.page.PagingResult<>(
                    paging, permissions, (long) permissions.size());
        }

        @Override
        public boolean hasRoleAssignments(ApplicationPermissionId permissionId) {
            return false;
        }

        @Override
        public void saveAll(List<ApplicationPermission> permissions) {
            this.permissions = List.copyOf(permissions);
        }

        @Override
        public void remove(ApplicationPermission permission) {
            permissions.remove(permission);
        }
    }
}
