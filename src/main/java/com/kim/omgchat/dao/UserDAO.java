package com.kim.omgchat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kim.omgchat.domain.UserDO;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 22:45
 */
public interface UserDAO extends BaseMapper<UserDO> {
    List<UserDO> listFriends(Long userId);
}
