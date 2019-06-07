package com.kim.omgchat.service;

import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserAddDTO;
import com.kim.omgchat.vo.user.UserQueryDTO;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 17:09
 */
public interface UserService {
    UserDO getUser(UserQueryDTO userQueryDTO);

    Boolean saveUser(UserAddDTO userAddDTO);

    List<UserDO> listFriends(Long userId);
}
