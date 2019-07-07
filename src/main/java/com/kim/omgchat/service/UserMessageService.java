package com.kim.omgchat.service;

import com.github.pagehelper.PageInfo;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageAddDTO;
import com.kim.omgchat.dto.UserMessageDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.dto.UserMessageReceiveDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:04
 */
public interface UserMessageService {
    /**
     * 保存信息
     */
    boolean saveUserMessage(UserMessageAddDTO userMessageAddDTO);
    /**
     * 保存信息
     */
    boolean saveUserMessage(UserMessageReceiveDTO userMessageReceiveDTO , MessageStatusEnum messageStatusEnum);

    /**
     * 查询信息数量
     */
    Integer countUserMessage(UserMessageQueryDTO userMessageQueryDTO);

    /**
     * 查询每个好友的最新消息
     */
    List<UserMessageDO> listFriendsMessageFor3d(UserMessageQueryDTO userMessageQueryDTO);

    List<UserMessageDTO> listFriendsMessage(UserMessageQueryDTO userMessageQueryDTO);

    /**
     * 查询与某位好有的聊天记录(分页)
     */
    PageInfo<UserMessageDTO> pageChatMsgWithFriend(UserMessageQueryDTO userMessageQueryDTO);

    boolean updateUserMessageStatus(UserMessageQueryDTO userMessageQueryDTO, MessageStatusEnum read);
}
