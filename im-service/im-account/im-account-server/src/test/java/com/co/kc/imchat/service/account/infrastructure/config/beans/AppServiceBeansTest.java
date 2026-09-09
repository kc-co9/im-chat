package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.service.account.infrastructure.config.CacheConfig;
import com.co.kc.imchat.service.account.infrastructure.config.RedisConfig;
import com.co.kc.imchat.service.account.infrastructure.config.properties.AccountBrokerProperties;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import com.co.kc.imchat.service.account.interfaces.rpc.AccountRpcService;
import com.co.kc.imchat.service.account.admin.facade.AccountAdminService;
import com.co.kc.imchat.service.account.interfaces.rpc.AccountAdminRpcService;
import com.co.kc.imchat.service.account.application.ManagedUserAppService;
import com.co.kc.imchat.service.account.adapter.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.domain.user.service.ManagedUserService;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.MysqlManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.CachedManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.mybatis.service.DbUserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AppServiceBeansTest {

    @Test
    void accountBeanConfigsFollowMessageModuleNamingStyle() {
        assertThat(AppServiceBeans.class.getSimpleName()).isEqualTo("AppServiceBeans");
        assertThat(DomainServiceBeans.class.getSimpleName()).isEqualTo("DomainServiceBeans");
        assertThat(RepositoryBeans.class.getSimpleName()).isEqualTo("RepositoryBeans");
        assertThat(AdapterBeans.class.getSimpleName()).isEqualTo("AdapterBeans");
        assertThat(CacheConfig.class.getSimpleName()).isEqualTo("CacheConfig");
        assertThat(RedisConfig.class.getSimpleName()).isEqualTo("RedisConfig");
    }

    @Test
    void adapterBeansOwnsBrokerClientAndExternalAdapterChain() throws NoSuchMethodException {
        assertThat(AdapterBeans.class.getMethod(
                "brokerClient",
                BoltInvoker.class,
                DiscoveryClient.class,
                AccountBrokerProperties.class)).isNotNull();
    }

    @Test
    void accountRpcServiceIsExposedAsDubboService() {
        DubboService dubboService = AccountRpcService.class.getAnnotation(DubboService.class);

        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(AccountService.class);
        assertThat(dubboService.version()).isEqualTo("1.0.0");
    }

    @Test
    void accountAdminRpcAndApplicationBeansAreWiredThroughOwnedBoundaries() {
        DubboService dubboService = AccountAdminRpcService.class.getAnnotation(DubboService.class);
        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(AccountAdminService.class);

        RepositoryBeans repositoryBeans = new RepositoryBeans();
        MysqlManagedUserRepository mysqlManagedUserRepository = repositoryBeans
                .mysqlManagedUserRepository(mock(DbUserService.class));
        ManagedUserRepository managedUserRepository = repositoryBeans.managedUserRepository(
                mysqlManagedUserRepository,
                mock(Cache.class),
                mock(Cache.class),
                mock(Cache.class));
        assertThat(managedUserRepository).isInstanceOf(CachedManagedUserRepository.class);

        ManagedUserAppService appService = new AppServiceBeans().managedUserAppService(
                managedUserRepository,
                mock(PasswordService.class),
                mock(ManagedUserService.class),
                mock(SessionService.class),
                mock(SessionConnectionAdapter.class),
                mock(AfterTransactionCommitTemplate.class),
                mock(DistributedLockTemplate.class));
        assertThat(appService).isNotNull();
    }

    @Test
    void accountServiceSupportsFindingUserProfileByEmail() throws NoSuchMethodException {
        Method method = AccountService.class.getMethod(
                "findUserProfileByEmail",
                UserProfileFindParams.class);

        assertThat(method.getReturnType()).isEqualTo(UserProfileFindDTO.class);
    }

    @Test
    void accountServiceSupportsBatchUserProfileQuery() throws NoSuchMethodException {
        Method method = AccountService.class.getMethod("getUserProfiles", UserProfilesGetParams.class);

        assertThat(method.getReturnType()).isEqualTo(List.class);
    }
}
