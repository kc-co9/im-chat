package com.co.kc.imchat.service.message.interfaces.rpc;

import com.co.kc.imchat.service.message.application.ChatAppService;
import com.co.kc.imchat.service.message.facade.ChatService;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummaryGetParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberRemoveParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsCreateParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsDismissParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsJoinParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatPrepareParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatRemoveParams;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummariesGetParams;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@RequiredArgsConstructor
@DubboService(interfaceClass = ChatService.class, version = "1.0.0")
public class ChatRpcService implements ChatService {
    private final ChatAppService chatAppService;

    @Override
    public UserGroupChatSummaryDTO getUserGroupChatSummary(UserGroupChatSummaryGetParams params) {
        return chatAppService.getUserGroupChatSummary(params);
    }

    @Override
    public List<UserGroupChatSummaryDTO> getUserGroupChatSummaries(UserGroupChatSummariesGetParams params) {
        return chatAppService.getUserGroupChatSummaries(params);
    }

    @Override
    public void preparePrivateChat(PrivateChatPrepareParams params) {
        chatAppService.preparePrivateChat(params);
    }

    @Override
    public void removePrivateChat(PrivateChatRemoveParams params) {
        chatAppService.removePrivateChat(params);
    }

    @Override
    public void createGroupChats(GroupChatsCreateParams params) {
        chatAppService.createGroupChats(params);
    }

    @Override
    public void dismissGroupChats(GroupChatsDismissParams params) {
        chatAppService.dismissGroupChats(params);
    }

    @Override
    public void joinGroupChats(GroupChatsJoinParams params) {
        chatAppService.joinGroupChats(params);
    }

    @Override
    public void removeGroupChatMember(GroupChatMemberRemoveParams params) {
        chatAppService.removeGroupChatMember(params);
    }
}
