package com.kim.omgchat.service;

import com.github.pagehelper.PageInfo;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageAddDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import sun.jvm.hotspot.debugger.Page;

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
     * 查询信息数量
     */
    Integer countUserMessage(UserMessageQueryDTO userMessageQueryDTO);

    /**
     * 查询每个好友的最新消息
     */
    List<UserMessageDO> listFriendsMessageFor3d(UserMessageQueryDTO userMessageQueryDTO);

    /**
     * 查询与某位好有的聊天记录(分页)
     */
    PageInfo<UserMessageDO> pageChatMsgWithFriend(UserMessageQueryDTO userMessageQueryDTO);
}
