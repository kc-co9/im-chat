package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
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
import static org.mockito.Mockito.mock;

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
                    is_deleted BIGINT NOT NULL DEFAULT 0
                )
                """);
    }

    @Test
    void readsOnlyTheRequestedApplicationCatalog() {
        insertPermission(10L, 1010L, "other:read", "Other");
        insertPermission(20L, 2020L, "user:read", "View users");
        MysqlApplicationPermissionRepository repository = repository();

        List<ApplicationPermission> result = repository.find(new AppId(2020L));

        assertThat(result)
                .extracting(permission -> permission.getId().value())
                .containsExactly(20L);
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
