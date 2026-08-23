package com.co.kc.imchat.plugin.metrics.aspect;

import com.co.kc.imchat.plugin.metrics.annotation.IgnoreException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

class IgnoreExceptionAspectTest {

    @Test
    void ignoresExceptionFromBestEffortMethod() {
        Target target = proxy(new Target());

        assertThat(target.bestEffort()).isNull();
    }

    @Test
    void preservesSuccessfulResult() {
        Target target = proxy(new Target());

        assertThat(target.success()).isEqualTo("ok");
    }

    private Target proxy(Target target) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new IgnoreExceptionAspect());
        return proxyFactory.getProxy();
    }

    static class Target {
        @IgnoreException
        public String bestEffort() {
            throw new IllegalStateException("expected");
        }

        public String success() {
            return "ok";
        }
    }
}
