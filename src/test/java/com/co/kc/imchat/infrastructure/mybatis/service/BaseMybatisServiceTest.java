package com.co.kc.imchat.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMybatisServiceTest {

    @Test
    void queryWrapperDoesNotDuplicateLogicDeleteCondition() {
        TestMybatisService service = new TestMybatisService();

        LambdaQueryWrapper<DbFriend> wrapper = service.getQueryWrapper();

        assertThat(wrapper.getSqlSegment()).doesNotContain("is_deleted");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    @Test
    void updateWrapperDoesNotDuplicateLogicDeleteCondition() {
        TestMybatisService service = new TestMybatisService();

        LambdaUpdateWrapper<DbFriend> wrapper = service.getUpdateWrapper();

        assertThat(wrapper.getSqlSegment()).doesNotContain("is_deleted");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    private static class TestMybatisService
            extends BaseMybatisService<com.co.kc.imchat.infrastructure.mybatis.mapper.DbFriendMapper, DbFriend> {
    }
}
