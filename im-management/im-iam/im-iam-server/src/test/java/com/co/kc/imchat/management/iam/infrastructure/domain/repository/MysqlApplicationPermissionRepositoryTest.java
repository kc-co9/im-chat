package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDescription;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationPermissionMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationPermissionDomainTransformer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import(DbIamApplicationPermissionService.class)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        ImDatasourceAutoConfiguration.class
})
@MapperScan(basePackageClasses = DbIamApplicationPermissionMapper.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-permission-page;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
class MysqlApplicationPermissionRepositoryTest {
    @Autowired
    private DbIamApplicationPermissionService permissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbIamApplicationPermission.class);
    }

    @BeforeEach
    void recreateTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS db_iam_application_permission");
        jdbcTemplate.execute("""
                CREATE TABLE db_iam_application_permission (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    permission_id BIGINT NOT NULL,
                    app_id BIGINT NOT NULL,
                    code VARCHAR(128) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    status TINYINT NOT NULL,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL,
                    is_deleted BIGINT NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);
    }

    @Test
    void readsOnlyTheRequestedApplicationCatalog() {
        insertPermission(10L, 1010L, "other:read", "Other");
        insertPermission(20L, 2020L, "user:read", "View users");
        jdbcTemplate.update("UPDATE db_iam_application_permission SET version = 3 WHERE permission_id = 20");
        MysqlApplicationPermissionRepository repository = repository();

        List<ApplicationPermission> result = repository.find(new AppId(2020L));

        assertThat(result).singleElement().satisfies(permission -> {
            assertThat(permission.getId().value()).isEqualTo(20L);
            assertThat(permission.getPkId()).isNotNull();
            assertThat(permission.getRowVersion()).isEqualTo(3L);
        });
    }

    @Test
    void keywordMatchesCodeOrNameBeforePaging() {
        insertPermission(20L, 2020L, "member:read", "View accounts");
        insertPermission(21L, 2020L, "account:disable", "Manage members");
        insertPermission(22L, 2020L, "account:read", "View accounts");
        insertPermission(23L, 3030L, "member:write", "Manage members");
        MysqlApplicationPermissionRepository repository = repository();
        ApplicationPermissionQueryCondition condition =
                new ApplicationPermissionQueryCondition(Optional.of("member"));

        PagingResult<ApplicationPermission> firstPage = repository.page(
                new AppId(2020L), condition, new Paging(1, 1));
        PagingResult<ApplicationPermission> secondPage = repository.page(
                new AppId(2020L), condition, new Paging(2, 1));

        assertThat(firstPage.total()).isEqualTo(2L);
        assertThat(secondPage.total()).isEqualTo(2L);
        assertThat(permissionKeys(firstPage, secondPage)).containsExactly(
                "account:disable#21",
                "member:read#20");
    }

    @Test
    void pagesByPermissionCodeThenBusinessId() {
        insertPermission(32L, 2020L, "user:read", "Read users");
        insertPermission(31L, 2020L, "user:read", "Read users");
        insertPermission(33L, 2020L, "user:write", "Write users");
        MysqlApplicationPermissionRepository repository = repository();
        ApplicationPermissionQueryCondition condition =
                new ApplicationPermissionQueryCondition(Optional.empty());

        PagingResult<ApplicationPermission> firstPage = repository.page(
                new AppId(2020L), condition, new Paging(1, 2));
        PagingResult<ApplicationPermission> secondPage = repository.page(
                new AppId(2020L), condition, new Paging(2, 2));

        assertThat(permissionKeys(firstPage, secondPage)).containsExactly(
                "user:read#31",
                "user:read#32",
                "user:write#33");
    }

    @Test
    void updatesAnExistingPermissionUsingItsTechnicalPrimaryKey() {
        insertPermission(20L, 2020L, "user:read", "Old name");
        MysqlApplicationPermissionRepository repository = repository();
        ApplicationPermission permission = repository.find(new ApplicationPermissionId(20L)).orElseThrow();
        permission.revise(
                new ApplicationPermissionName("New name"),
                new ApplicationPermissionDescription("New description"));

        repository.saveAll(List.of(permission));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM db_iam_application_permission WHERE permission_id = 20",
                String.class)).isEqualTo("New name");
        assertThat(permission.getRowVersion()).isEqualTo(1L);
    }

    @Test
    void batchUpdateRejectsAStalePermission() {
        insertPermission(20L, 2020L, "user:read", "Old name");
        MysqlApplicationPermissionRepository repository = repository();
        ApplicationPermission current = repository.find(new ApplicationPermissionId(20L)).orElseThrow();
        ApplicationPermission stale = repository.find(new ApplicationPermissionId(20L)).orElseThrow();
        current.revise(
                new ApplicationPermissionName("Current name"),
                new ApplicationPermissionDescription("Current description"));
        repository.saveAll(List.of(current));
        stale.revise(
                new ApplicationPermissionName("Stale name"),
                new ApplicationPermissionDescription("Stale description"));

        assertThatThrownBy(() -> repository.saveAll(List.of(stale)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM db_iam_application_permission WHERE permission_id = 20",
                String.class)).isEqualTo("Current name");
    }

    @Test
    void stalePermissionUpdateIsNotSilentlyIgnored() {
        DbIamApplicationPermissionService failedService = mock(DbIamApplicationPermissionService.class);
        ApplicationPermissionDomainTransformer transformer = mock(ApplicationPermissionDomainTransformer.class);
        ApplicationPermission permission = mock(ApplicationPermission.class);
        DbIamApplicationPermission row = new DbIamApplicationPermission();
        row.setId(7L);
        row.setPermissionId(20L);
        row.setVersion(2L);
        when(permission.getPkId()).thenReturn(7L);
        when(permission.getId()).thenReturn(new ApplicationPermissionId(20L));
        when(transformer.dbPermissionFrom(permission)).thenReturn(row);
        when(failedService.updateById(row)).thenReturn(false);
        MysqlApplicationPermissionRepository repository = new MysqlApplicationPermissionRepository(
                failedService, transformer, mock(DbIamApplicationRolePermissionService.class));

        assertThatThrownBy(() -> repository.saveAll(List.of(permission)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verify(failedService).updateById(row);
    }

    @Test
    void insertsNewPermissionsAsOneBatch() {
        DbIamApplicationPermissionService service = mock(DbIamApplicationPermissionService.class);
        ApplicationPermissionDomainTransformer transformer = mock(ApplicationPermissionDomainTransformer.class);
        ApplicationPermission first = mock(ApplicationPermission.class);
        ApplicationPermission second = mock(ApplicationPermission.class);
        DbIamApplicationPermission firstRow = new DbIamApplicationPermission();
        firstRow.setId(7L);
        firstRow.setPermissionId(20L);
        firstRow.setVersion(0L);
        DbIamApplicationPermission secondRow = new DbIamApplicationPermission();
        secondRow.setId(8L);
        secondRow.setPermissionId(21L);
        secondRow.setVersion(0L);
        when(first.getPkId()).thenReturn(null);
        when(second.getPkId()).thenReturn(null);
        when(transformer.dbPermissionFrom(first)).thenReturn(firstRow);
        when(transformer.dbPermissionFrom(second)).thenReturn(secondRow);
        when(service.saveBatch(anyCollection())).thenReturn(true);
        MysqlApplicationPermissionRepository repository = new MysqlApplicationPermissionRepository(
                service, transformer, mock(DbIamApplicationRolePermissionService.class));

        repository.saveAll(List.of(first, second));

        verify(service).saveBatch(List.of(firstRow, secondRow));
        verify(service, never()).save(any());
        verify(first).setPkId(7L);
        verify(second).setPkId(8L);
    }

    private MysqlApplicationPermissionRepository repository() {
        return new MysqlApplicationPermissionRepository(
                permissionService,
                ApplicationPermissionDomainTransformer.INSTANCE,
                mock(DbIamApplicationRolePermissionService.class));
    }

    private void insertPermission(Long permissionId, Long appId, String code, String name) {
        jdbcTemplate.update("""
                        INSERT INTO db_iam_application_permission (
                            permission_id, app_id, code, name, description, status, is_deleted
                        ) VALUES (?, ?, ?, ?, 'description', 1, 0)
                        """,
                permissionId,
                appId,
                code,
                name);
    }

    @SafeVarargs
    private static List<String> permissionKeys(PagingResult<ApplicationPermission>... pages) {
        return Arrays.stream(pages)
                .flatMap(page -> page.records().stream())
                .map(permission -> permission.getCode().value()
                        + "#" + permission.getId().value())
                .toList();
    }
}
