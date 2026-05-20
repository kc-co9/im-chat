package com.co.kc.imchat.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceConfigTest {

    @Test
    void mybatisPlusInterceptorKeepsBlockAttackAndAvoidsIllegalSqlIndexFalsePositives() {
        MybatisPlusInterceptor interceptor = new DatasourceConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .anySatisfy(innerInterceptor -> assertThat(innerInterceptor)
                        .isInstanceOf(BlockAttackInnerInterceptor.class));
        assertThat(interceptor.getInterceptors())
                .noneSatisfy(innerInterceptor -> assertThat(innerInterceptor)
                        .isInstanceOf(IllegalSQLInnerInterceptor.class));
    }
}
