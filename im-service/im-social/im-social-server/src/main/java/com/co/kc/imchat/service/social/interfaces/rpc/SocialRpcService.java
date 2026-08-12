package com.co.kc.imchat.service.social.interfaces.rpc;

import com.co.kc.imchat.service.social.application.FriendAppService;
import com.co.kc.imchat.service.social.application.GroupAppService;
import com.co.kc.imchat.service.social.facade.SocialService;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplaysDTO;
import com.co.kc.imchat.service.social.facade.dto.FriendRelationCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMembersDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientsDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummariesDTO;
import com.co.kc.imchat.service.social.facade.params.FriendDisplaysGetParams;
import com.co.kc.imchat.service.social.facade.params.FriendRelationCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMemberCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMembersGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupMessageRecipientsGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupSummariesGetParams;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@DubboService(interfaceClass = SocialService.class, version = "1.0.0")
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
public class SocialRpcService implements SocialService {

    private final FriendAppService friendAppService;
    private final GroupAppService groupAppService;

    public SocialRpcService(FriendAppService friendAppService, GroupAppService groupAppService) {
        this.friendAppService = friendAppService;
        this.groupAppService = groupAppService;
    }

    @Override
    public FriendRelationCheckDTO checkFriendRelation(FriendRelationCheckParams params) {
        return friendAppService.checkFriendRelation(params);
    }

    @Override
    public GroupMemberCheckDTO checkGroupMember(GroupMemberCheckParams params) {
        return groupAppService.checkGroupMember(params);
    }

    @Override
    public GroupMembersDTO getGroupMembers(GroupMembersGetParams params) {
        return groupAppService.getGroupMembers(params);
    }

    @Override
    public GroupMessageRecipientsDTO getGroupMessageRecipients(GroupMessageRecipientsGetParams params) {
        return groupAppService.getGroupMessageRecipients(params);
    }

    @Override
    public FriendDisplaysDTO getFriendDisplays(FriendDisplaysGetParams params) {
        return friendAppService.getFriendDisplays(params);
    }

    @Override
    public GroupSummariesDTO getGroupSummaries(GroupSummariesGetParams params) {
        return groupAppService.getGroupSummaries(params);
    }
}
