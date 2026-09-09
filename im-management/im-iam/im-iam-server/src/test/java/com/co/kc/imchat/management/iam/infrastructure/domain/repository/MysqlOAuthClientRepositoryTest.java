package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthClientStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthClientMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthClientService;
import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import(DbIamOAuthClientService.class)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        ImDatasourceAutoConfiguration.class
})
@MapperScan(basePackageClasses = DbIamOAuthClientMapper.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:oauth-client-page;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
class MysqlOAuthClientRepositoryTest {
    @Autowired
    private DbIamOAuthClientService oauthClientService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbIamOAuthClient.class);
    }

    @BeforeEach
    void recreateTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS db_iam_oauth_client");
        jdbcTemplate.execute("""
                CREATE TABLE db_iam_oauth_client (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    oauth_client_id VARCHAR(128) NOT NULL,
                    app_id BIGINT NOT NULL,
                    audience_app_id BIGINT NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    client_secret_hash VARCHAR(255) NOT NULL,
                    grant_types VARCHAR(255) NOT NULL,
                    scopes VARCHAR(255) NOT NULL,
                    redirect_uris VARCHAR(1000),
                    post_logout_redirect_uris VARCHAR(1000),
                    status TINYINT NOT NULL,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL,
                    is_deleted BIGINT NOT NULL DEFAULT 0
                )
                """);
    }

    @Test
    void pagesOnlyRequestedOwnerAcrossStableAdjacentPages() {
        insertClient(11L, "aaa-other-owner", 2002L);
        insertClient(13L, "client-a", 1001L);
        insertClient(14L, "client-a", 1001L);
        insertClient(12L, "client-b", 1001L);
        insertClient(15L, "client-c", 1001L);
        MysqlOAuthClientRepository repository =
                new MysqlOAuthClientRepository(oauthClientService);

        PagingResult<OAuthClient> firstPage = repository.page(
                new AppId(1001L),
                new Paging(1, 2));
        PagingResult<OAuthClient> secondPage = repository.page(
                new AppId(1001L),
                new Paging(2, 2));

        assertThat(firstPage.total()).isEqualTo(4L);
        assertThat(secondPage.total()).isEqualTo(4L);
        assertThat(clientKeys(firstPage, secondPage)).containsExactly(
                "client-a#13",
                "client-a#14",
                "client-b#12",
                "client-c#15");
    }

    @Test
    void restoresTechnicalPrimaryKeyAfterDomainMapping() {
        DbIamOAuthClientService service = mock(DbIamOAuthClientService.class);
        DbIamOAuthClient row = new DbIamOAuthClient();
        row.setId(30L);
        row.setOauthClientId("client-30");
        row.setAppId(1L);
        row.setAudienceAppId(2L);
        row.setName("client");
        row.setClientSecretHash("secret");
        row.setGrantTypes("CLIENT_CREDENTIALS");
        row.setScopes("audit:read");
        row.setRedirectUris("[]");
        row.setPostLogoutRedirectUris("[]");
        row.setStatus(DbIamOAuthClientStatus.ACTIVE);
        when(service.getOne(any(Wrapper.class), any(Boolean.class))).thenReturn(row);
        MysqlOAuthClientRepository repository =
                new MysqlOAuthClientRepository(service);

        assertThat(repository.find(new OAuthClientId("client-30")))
                .get()
                .extracting(OAuthClient::getPkId)
                .isEqualTo(30L);
    }

    private static DbIamOAuthClient oauthClientRow(Long id, String clientId, Long appId) {
        DbIamOAuthClient row = new DbIamOAuthClient();
        row.setId(id);
        row.setOauthClientId(clientId);
        row.setAppId(appId);
        row.setAudienceAppId(2001L);
        row.setName("client");
        row.setClientSecretHash("encoded-secret");
        row.setGrantTypes("CLIENT_CREDENTIALS");
        row.setScopes("audit:read");
        row.setRedirectUris("[]");
        row.setPostLogoutRedirectUris("[]");
        row.setStatus(DbIamOAuthClientStatus.ACTIVE);
        return row;
    }

    private void insertClient(Long id, String clientId, Long appId) {
        jdbcTemplate.update("""
                        INSERT INTO db_iam_oauth_client (
                            id, oauth_client_id, app_id, audience_app_id, name,
                            client_secret_hash, grant_types, scopes, redirect_uris,
                            post_logout_redirect_uris, status, is_deleted
                        ) VALUES (?, ?, ?, 2001, 'client', 'encoded-secret',
                            'CLIENT_CREDENTIALS', 'audit:read', '[]', '[]', 1, 0)
                        """,
                id,
                clientId,
                appId);
    }

    @SafeVarargs
    private static List<String> clientKeys(PagingResult<OAuthClient>... pages) {
        return java.util.Arrays.stream(pages)
                .flatMap(page -> page.records().stream())
                .map(client -> client.getClientId().value() + "#" + client.getPkId())
                .toList();
    }
}
