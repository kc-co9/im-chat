package com.kim.omgchat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.kim.omgchat.dao.UserMessageDAO;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageAddDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import com.kim.omgchat.service.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:06
 */
@Service
public class UserMessageServiceImpl implements UserMessageService {

    @Autowired
    private UserMessageDAO userMessageDAO;

    @Override
    public boolean saveUserMessage(UserMessageAddDTO userMessageAddDTO) {
        return userMessageDAO.insert(userMessageAddDTO) > 0;
    }

    @Override
    public Integer countUserMessage(UserMessageQueryDTO userMessageQueryDTO) {
        QueryWrapper<UserMessageDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", userMessageQueryDTO.getStatus());
        queryWrapper.eq("to_uid", userMessageQueryDTO.getToUserId());
        return userMessageDAO.selectCount(queryWrapper);
    }

    @Override
    public List<UserMessageDO> listFriendsMessageFor3d(UserMessageQueryDTO userMessageQueryDTO) {
        userMessageQueryDTO.setInnerDay(3);
        return userMessageDAO.listFriendsMessageForXd(userMessageQueryDTO);
    }

    @Override
    public PageInfo<UserMessageDO> pageChatMsgWithFriend(UserMessageQueryDTO userMessageQueryDTO) {
        PageHelper.startPage(userMessageQueryDTO.getPageIndex(), userMessageQueryDTO.getPageSize());
        List<UserMessageDO> list = userMessageDAO.listChatMsgWithFriend(userMessageQueryDTO.getFromUserId(), userMessageQueryDTO.getToUserId());

        return new PageInfo<>(list);
    }

    @Override
    public boolean updateUserMessageStatus(UserMessageQueryDTO userMessageQueryDTO, MessageStatusEnum read) {
        return userMessageDAO.updateStatus(userMessageQueryDTO.getFromUserId(), userMessageQueryDTO.getToUserId(), read.getValue()) > 0;
    }
}
