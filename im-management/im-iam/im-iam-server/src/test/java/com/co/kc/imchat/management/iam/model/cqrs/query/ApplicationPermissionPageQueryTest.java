package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPermissionPageQueryTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void nullableOrBlankKeywordIsOmitted(String keyword) {
        ApplicationPermissionPageQuery query = new ApplicationPermissionPageQuery(
                1001L,
                keyword,
                new Paging(1, 20));

        assertThat(query.keyword()).isNull();
    }

    @Test
    void nonBlankKeywordIsTrimmed() {
        ApplicationPermissionPageQuery query = new ApplicationPermissionPageQuery(
                1001L,
                "  user:read  ",
                new Paging(1, 20));

        assertThat(query.keyword()).isEqualTo("user:read");
    }
}
