package com.kim.omgchat.service;

import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageAddDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;

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
    boolean saveUserMessage(UserMessageAddDTO userMessageAddDTO);

    Integer countUserMessage(UserMessageQueryDTO userMessageQueryDTO);

    List<UserMessageDO> listFriendsMessageFor3d(UserMessageQueryDTO userMessageQueryDTO);
}
