package com.co.kc.imchat.service.account.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DbUserMapperSqlTest {
    @Test
    void rawUserQueryDoesNotFilterDeletionState() {
        String sql = sql(DbUserMapper.class.getName() + ".selectRawUsers",
                new DbUserQueryCondition(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(sql).doesNotContain("is_deleted = 0", "is_deleted <> 0");
    }

    @Test
    void rawUserQueryIncludesPresentOptionalFilters() {
        String sql = sql(DbUserMapper.class.getName() + ".selectRawUsers",
                new DbUserQueryCondition(
                        Optional.of(1001L), Optional.of("alice"),
                        Optional.of("alice@example.com"), Optional.of(DbUserStatus.BANNED)));

        assertThat(sql).contains("user_id = ?", "username like", "email = ?", "status = ?");
    }

    @Test
    void managementDetailQueryCanRestoreAggregateWithoutExposingWildcardSelection() {
        String sql = sql(DbUserMapper.class.getName() + ".selectRawByUserId",
                Map.of("userId", 1001L));

        assertThat(sql).contains("where user_id = ?");
        assertThat(sql).contains("password");
        assertThat(sql).doesNotContain("is_deleted = 0", "select *", "select db_user.*");
    }

    private static String sql(String statementId, DbUserQueryCondition condition) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", new Page<>(1, 20));
        parameters.put("condition", condition);
        return sql(statementId, parameters);
    }

    private static String sql(String statementId, Map<String, Object> parameters) {
        Configuration configuration = configuration();
        return sql(configuration, statementId, parameters);
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        String resource = "mapper/DbUserMapper.xml";
        try (InputStream input = DbUserMapperSqlTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statementId,
                              Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(parameters);
        return boundSql.getSql()
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
