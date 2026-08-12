package com.co.kc.imchat.service.social.facade;

import com.co.kc.imchat.service.social.facade.params.FriendRelationCheckParams;
import com.co.kc.imchat.service.social.facade.params.FriendDisplaysGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupMemberCheckParams;
import com.co.kc.imchat.service.social.facade.params.GroupMembersGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupMessageRecipientsGetParams;
import com.co.kc.imchat.service.social.facade.params.GroupSummariesGetParams;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplaysDTO;
import com.co.kc.imchat.service.social.facade.dto.FriendRelationCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMemberCheckDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMessageRecipientsDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupMembersDTO;
import com.co.kc.imchat.service.social.facade.dto.GroupSummariesDTO;

/**
 * 社交关系服务契约。
 * <p>
 * 为消息服务提供好友关系、群成员关系和群消息收件人查询能力。
 */
public interface SocialService {
    /**
     * 校验两个用户之间的好友关系。
     *
     * @param params 好友关系校验请求
     * @return 好友关系校验结果
     */
    FriendRelationCheckDTO checkFriendRelation(FriendRelationCheckParams params);

    /**
     * 校验用户是否属于指定群。
     *
     * @param params 群成员校验请求
     * @return 群成员校验结果
     */
    GroupMemberCheckDTO checkGroupMember(GroupMemberCheckParams params);

    /**
     * 查询群成员列表。
     *
     * @param params 群成员查询请求
     * @return 群成员列表
     */
    GroupMembersDTO getGroupMembers(GroupMembersGetParams params);

    /**
     * 查询群消息需要投递的收件人列表。
     *
     * @param params 群消息收件人查询请求
     * @return 群消息收件人列表
     */
    GroupMessageRecipientsDTO getGroupMessageRecipients(GroupMessageRecipientsGetParams params);

    /**
     * 查询好友展示信息。
     *
     * @param params 好友展示信息查询请求
     * @return 好友展示信息列表
     */
    FriendDisplaysDTO getFriendDisplays(FriendDisplaysGetParams params);

    /**
     * 查询群摘要。
     *
     * @param params 群摘要查询请求
     * @return 群摘要列表
     */
    GroupSummariesDTO getGroupSummaries(GroupSummariesGetParams params);
}
