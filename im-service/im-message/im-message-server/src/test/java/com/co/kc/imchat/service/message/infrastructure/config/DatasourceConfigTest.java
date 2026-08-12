package com.co.kc.imchat.service.message.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceConfigTest {

    @Test
    void mybatisPlusInterceptorKeepsBlockAttackAndAvoidsIllegalSqlIndexFalsePositives() {
        MybatisPlusInterceptor interceptor = new ImDatasourceAutoConfiguration().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .anySatisfy(innerInterceptor -> assertThat(innerInterceptor)
                        .isInstanceOf(BlockAttackInnerInterceptor.class));
        assertThat(interceptor.getInterceptors())
                .noneSatisfy(innerInterceptor -> assertThat(innerInterceptor)
                        .extracting(inner -> inner.getClass().getName())
                        .isEqualTo("com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor"));
    }
}
