package com.kim.omgchat.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kim.omgchat.dao.UserDAO;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserAddDTO;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.utils.IdGenerateUtil;
import com.kim.omgchat.vo.user.UserQueryDTO;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 22:45
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDAO userDAO;

    @Override
    public UserDO getUser(UserQueryDTO userQueryDTO) {
        QueryWrapper<UserDO> queryWrapper = new QueryWrapper<>();
        if (userQueryDTO.getUserId()!=null){
            queryWrapper.eq("id", userQueryDTO.getUserId());
        }
        if (!StringUtils.isEmpty(userQueryDTO.getEmail())) {
            queryWrapper.eq("email", userQueryDTO.getEmail());
        }
        return userDAO.selectOne(queryWrapper);
    }

    @Override
    public Boolean saveUser(UserAddDTO userAddDTO) {
        DozerBeanMapper mapper = new DozerBeanMapper();
        UserDO userDO = mapper.map(userAddDTO, UserDO.class);

        //插入ID
        userDO.setId(IdGenerateUtil.nextId());

        return userDAO.insert(userDO) > 0;
    }

    @Override
    public List<UserDO> listFriends(Long userId) {
        return userDAO.listFriends(userId);
    }
}
