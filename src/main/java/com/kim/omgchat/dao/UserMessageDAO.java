package com.kim.omgchat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageQueryDTO;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:02
 */
public interface UserMessageDAO extends BaseMapper<UserMessageDO> {

    List<UserMessageDO> listFriendsMessageForXd(UserMessageQueryDTO userMessageQueryDTO);
}
