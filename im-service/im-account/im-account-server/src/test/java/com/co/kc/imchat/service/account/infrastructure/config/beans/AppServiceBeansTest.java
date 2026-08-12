package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.service.account.infrastructure.config.CacheConfig;
import com.co.kc.imchat.service.account.infrastructure.config.DatasourceConfig;
import com.co.kc.imchat.service.account.infrastructure.config.RedisConfig;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.interfaces.rpc.AccountRpcService;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AppServiceBeansTest {

    @Test
    void accountBeanConfigsFollowMessageModuleNamingStyle() {
        assertThat(AppServiceBeans.class.getSimpleName()).isEqualTo("AppServiceBeans");
        assertThat(DomainServiceBeans.class.getSimpleName()).isEqualTo("DomainServiceBeans");
        assertThat(RepositoryBeans.class.getSimpleName()).isEqualTo("RepositoryBeans");
        assertThat(BasicBeans.class.getSimpleName()).isEqualTo("BasicBeans");
        assertThat(CacheConfig.class.getSimpleName()).isEqualTo("CacheConfig");
        assertThat(RedisConfig.class.getSimpleName()).isEqualTo("RedisConfig");
        assertThat(DatasourceConfig.class.getSimpleName()).isEqualTo("DatasourceConfig");
    }

    @Test
    void accountRpcServiceIsExposedAsDubboService() {
        DubboService dubboService = AccountRpcService.class.getAnnotation(DubboService.class);

        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(AccountService.class);
        assertThat(dubboService.version()).isEqualTo("1.0.0");
    }

    @Test
    void accountServiceSupportsFindingUserProfileByEmail() throws NoSuchMethodException {
        Method method = AccountService.class.getMethod(
                "findUserProfileByEmail",
                UserProfileFindParams.class);

        assertThat(method.getReturnType()).isEqualTo(UserProfileFindDTO.class);
    }
}
