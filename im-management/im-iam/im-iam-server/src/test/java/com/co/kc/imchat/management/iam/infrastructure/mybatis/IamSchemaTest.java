package com.co.kc.imchat.management.iam.infrastructure.mybatis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class IamSchemaTest {

    @Test
    void declaresEveryIamOwnedTableWithoutDestructiveStatements() throws IOException {
        String ddl = ddl();

        assertThat(ddl)
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_administrator`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_app`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_oauth_client`")
                .contains("`audience_app_id`")
                .contains("`idx_iam_oauth_client_audience_status`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_application_permission`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_application_role`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_application_administrator_role`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_application_role_permission`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_internal_role`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_internal_administrator_role`")
                .contains("CREATE TABLE IF NOT EXISTS `db_iam_authorization`")
                .doesNotContain("db_iam_refresh_token_history")
                .doesNotContain("db_iam_security_audit")
                .doesNotContain("DROP TABLE")
                .doesNotContainIgnoringCase("SELECT *");
    }

    @Test
    void isolatesApplicationsAndRolePermissionsWithNaturalKeys() throws IOException {
        assertThat(ddl())
                .contains("UNIQUE KEY `uk_iam_app_id`")
                .contains("UNIQUE KEY `uk_iam_app_key`")
                .contains("UNIQUE KEY `uk_iam_oauth_client_id`")
                .containsPattern("`app_id`\\s+BIGINT UNSIGNED NOT NULL COMMENT '应用业务ID'")
                .doesNotContain("`application_id`")
                .doesNotContain("`service_client_id`")
                .contains("UNIQUE KEY `uk_iam_application_permission_app_code`")
                .contains("UNIQUE KEY `uk_iam_application_role_app_code`")
                .contains("UNIQUE KEY `uk_iam_application_administrator_role`")
                .contains("UNIQUE KEY `uk_iam_application_role_permission`")
                .contains("UNIQUE KEY `uk_iam_internal_role_code`")
                .contains("UNIQUE KEY `uk_iam_internal_administrator_role`");
    }

    @Test
    void persistsOnlyTokenDigests() throws IOException {
        assertThat(ddl())
                .contains("`authorization_code_digest`")
                .contains("`access_token_digest`")
                .contains("`refresh_token_digest`")
                .doesNotContain("`authorization_code`")
                .doesNotContain("`access_token`")
                .doesNotContain("`refresh_token`");
    }

    @Test
    void persistsRegisteredRedirectsAndPkceVerificationFacts() throws IOException {
        assertThat(ddl())
                .contains("`redirect_uris`")
                .contains("`post_logout_redirect_uris`")
                .contains("`redirect_uri`")
                .contains("`code_challenge`")
                .contains("`code_challenge_method`");
    }

    @Test
    void everyIamTableContainsTheStandardEntityColumns() throws IOException {
        Matcher tables = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS `([^`]+)`\\s*\\((.*?)\\) ENGINE",
                        Pattern.DOTALL)
                .matcher(ddl());
        int tableCount = 0;
        while (tables.find()) {
            tableCount++;
            assertThat(tables.group(2))
                    .as(tables.group(1))
                    .contains("`id`")
                    .contains("`create_time`")
                    .contains("`update_time`")
                    .contains("`is_deleted`");
        }
        assertThat(tableCount).isEqualTo(10);
    }

    private static String ddl() throws IOException {
        return Files.readString(Path.of("sql/ddl.sql"));
    }
}
