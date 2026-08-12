package com.co.kc.imchat.service.social.infrastructure.config.beans;

import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import com.co.kc.imchat.service.social.application.FriendAppService;
import com.co.kc.imchat.service.social.application.GroupAppService;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.friend.service.FriendService;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.service.social.domain.group.service.GroupService;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 社交应用服务 Bean 装配。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
public class AppServiceBeans {

    @Bean
    public FriendAppService friendAppService(AccountAdapter accountAdapter,
                                             FriendRepository friendRepository,
                                             FriendService friendService,
                                             MessageSocialAdapter messageSocialAdapter,
                                             AfterTransactionCommitTemplate afterTransactionCommitTemplate) {
        return new FriendAppService(
                accountAdapter,
                friendRepository,
                friendService,
                messageSocialAdapter,
                afterTransactionCommitTemplate);
    }

    @Bean
    public GroupAppService groupAppService(GroupRepository groupRepository,
                                           GroupMemberRepository groupMemberRepository,
                                           FriendRepository friendRepository,
                                           GroupService groupService,
                                           AccountAdapter accountAdapter,
                                           MessageSocialAdapter messageSocialAdapter,
                                           SnowflakeId snowflakeId,
                                           AfterTransactionCommitTemplate afterTransactionCommitTemplate) {
        return new GroupAppService(
                groupRepository,
                groupMemberRepository,
                friendRepository,
                groupService,
                accountAdapter,
                messageSocialAdapter,
                snowflakeId,
                afterTransactionCommitTemplate);
    }
}
