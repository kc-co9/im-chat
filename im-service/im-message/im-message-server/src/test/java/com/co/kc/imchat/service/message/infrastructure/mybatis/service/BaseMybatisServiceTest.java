package com.co.kc.imchat.service.message.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMybatisServiceTest {

    @Test
    void queryWrapperDoesNotDuplicateLogicDeleteCondition() {
        TestMybatisService service = new TestMybatisService();

        LambdaQueryWrapper<DbImPrivateChat> wrapper = service.getQueryWrapper();

        assertThat(wrapper.getSqlSegment()).doesNotContain("is_deleted");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    @Test
    void updateWrapperDoesNotDuplicateLogicDeleteCondition() {
        TestMybatisService service = new TestMybatisService();

        LambdaUpdateWrapper<DbImPrivateChat> wrapper = service.getUpdateWrapper();

        assertThat(wrapper.getSqlSegment()).doesNotContain("is_deleted");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    private static class TestMybatisService
            extends BaseMybatisService<DbImPrivateChatMapper, DbImPrivateChat> {
    }
}
