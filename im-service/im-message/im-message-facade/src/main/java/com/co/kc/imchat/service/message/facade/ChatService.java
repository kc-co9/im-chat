package com.co.kc.imchat.service.message.facade;

import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummaryGetParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberRemoveParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsCreateParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsDismissParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsJoinParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatPrepareParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatRemoveParams;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummariesGetParams;

import java.util.List;

/**
 * 聊天会话服务。
 * <p>
 * 提供用户视角的群聊会话摘要查询，以及外部服务驱动的会话生命周期维护能力。
 */
public interface ChatService {
    /**
     * 查询用户单个群聊会话摘要。
     *
     * @param params 群聊会话查询请求
     * @return 群聊会话摘要
     */
    UserGroupChatSummaryDTO getUserGroupChatSummary(UserGroupChatSummaryGetParams params);

    /**
     * 查询用户多个群聊会话摘要。
     *
     * @param params 群聊会话批量查询请求
     * @return 群聊会话摘要列表
     */
    List<UserGroupChatSummaryDTO> getUserGroupChatSummaries(UserGroupChatSummariesGetParams params);

    /**
     * 预创建私聊会话。
     *
     * @param params 私聊会话预创建请求
     */
    void preparePrivateChat(PrivateChatPrepareParams params);

    /**
     * 删除私聊会话。
     *
     * @param params 私聊会话删除请求
     */
    void removePrivateChat(PrivateChatRemoveParams params);

    /**
     * 创建群聊会话。
     *
     * @param params 群聊会话创建请求
     */
    void createGroupChats(GroupChatsCreateParams params);

    /**
     * 解散群聊会话。
     *
     * @param params 群聊会话解散请求
     */
    void dismissGroupChats(GroupChatsDismissParams params);

    /**
     * 同步新成员群聊会话。
     *
     * @param params 群聊会话加入请求
     */
    void joinGroupChats(GroupChatsJoinParams params);

    /**
     * 移除成员群聊会话。
     *
     * @param params 群聊成员移除请求
     */
    void removeGroupChatMember(GroupChatMemberRemoveParams params);
}
