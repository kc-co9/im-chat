package com.co.kc.imchat.service.social.infrastructure.config.beans;

import com.co.kc.imchat.service.social.facade.SocialService;
import com.co.kc.imchat.service.social.interfaces.rpc.SocialRpcService;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AppServiceBeansTest {

    @Test
    void socialRpcServiceIsExposedAsDubboService() {
        DubboService dubboService = SocialRpcService.class.getAnnotation(DubboService.class);

        assertThat(dubboService).isNotNull();
        assertThat(dubboService.interfaceClass()).isEqualTo(SocialService.class);
        assertThat(dubboService.version()).isEqualTo("1.0.0");
    }

    @Test
    void socialAppServiceBeansExposeFriendAndGroupApplicationServices() throws NoSuchMethodException {
        Method friendMethod = AppServiceBeans.class.getMethod(
                "friendAppService",
                com.co.kc.imchat.service.social.adapter.account.AccountAdapter.class,
                com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository.class,
                com.co.kc.imchat.service.social.domain.friend.service.FriendService.class,
                com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter.class,
                com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate.class);
        Method groupMethod = AppServiceBeans.class.getMethod(
                "groupAppService",
                com.co.kc.imchat.service.social.domain.group.repository.GroupRepository.class,
                com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository.class,
                com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository.class,
                com.co.kc.imchat.service.social.domain.group.service.GroupService.class,
                com.co.kc.imchat.service.social.adapter.account.AccountAdapter.class,
                com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter.class,
                com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId.class,
                com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate.class);

        assertThat(friendMethod.getReturnType()).isEqualTo(com.co.kc.imchat.service.social.application.FriendAppService.class);
        assertThat(groupMethod.getReturnType()).isEqualTo(com.co.kc.imchat.service.social.application.GroupAppService.class);
    }
}
