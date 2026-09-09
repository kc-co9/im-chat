package com.co.kc.imchat.service.message.adapter.social;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupMessageRecipient;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import com.co.kc.imchat.service.social.facade.SocialService;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplayDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummaryDTO;
import com.co.kc.imchat.service.social.facade.params.FriendDisplaysGetParams;
import com.co.kc.imchat.service.social.facade.params.FriendRelationCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMemberCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMessageRecipientsGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupSummariesGetParams;

import java.util.List;
import java.util.Objects;

/**
 * 消息服务访问社交服务的适配器。
 */
public class SocialAdapter {

    private final SocialService socialService;

    public SocialAdapter(SocialService socialService) {
        this.socialService = Objects.requireNonNull(socialService, "socialService");
    }

    /**
     * 校验两个用户之间存在可发送消息的好友关系。
     *
     * @param userId     当前用户 ID
     * @param peerUserId 对端用户 ID
     */
    public void ensureFriendshipActive(Long userId, Long peerUserId) {
        boolean active = socialService.checkFriendRelation(
                new FriendRelationCheckParams(userId, peerUserId)).active();
        if (!active) {
            throw new NotFoundException("好友不存在");
        }
    }

    /**
     * 校验用户属于指定群。
     *
     * @param groupId 群 ID
     * @param userId  用户 ID
     */
    public void ensureGroupMember(Long groupId, Long userId) {
        boolean member = socialService.checkGroupMember(new GroupMemberCheckParams(groupId, userId)).member();
        if (!member) {
            throw new NotFoundException("群成员不存在");
        }
    }

    /**
     * 查询群消息投递收件人。
     *
     * @param groupId 群 ID
     * @return 收件人列表
     */
    public List<GroupMessageRecipient> getGroupMessageRecipients(Long groupId) {
        return socialService.getGroupMessageRecipients(new GroupMessageRecipientsGetParams(groupId)).recipients().stream()
                .map(this::toRecipient)
                .toList();
    }

    /**
     * 查询好友展示信息。
     *
     * @param userId        当前用户 ID
     * @param friendUserIds 好友用户 ID 列表
     * @return 好友展示信息列表
     */
    public List<FriendDisplay> getFriendDisplays(Long userId, List<Long> friendUserIds) {
        return socialService.getFriendDisplays(new FriendDisplaysGetParams(userId, friendUserIds))
                .stream()
                .map(this::toFriendDisplay)
                .toList();
    }

    /**
     * 查询群摘要。
     *
     * @param groupIds 群 ID 列表
     * @return 群摘要列表
     */
    public List<GroupSummary> getGroupSummaries(List<Long> groupIds) {
        return socialService.getGroupSummaries(new GroupSummariesGetParams(groupIds))
                .groups()
                .stream()
                .map(this::toGroupSummary)
                .toList();
    }

    private GroupMessageRecipient toRecipient(GroupMessageRecipientDTO response) {
        return new GroupMessageRecipient(response.userId());
    }

    private FriendDisplay toFriendDisplay(FriendDisplayDTO response) {
        return new FriendDisplay(response.userId(), response.displayName());
    }

    private GroupSummary toGroupSummary(GroupSummaryDTO response) {
        return new GroupSummary(response.groupId(), response.name(), response.active());
    }
}
