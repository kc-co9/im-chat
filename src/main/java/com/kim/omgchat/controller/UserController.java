package com.kim.omgchat.controller;

import com.alibaba.druid.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.constant.RedisKeyConstant;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserFriendListDTO;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.vo.ResultVO;
import com.kim.omgchat.vo.user.UserQueryDTO;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 16:28
 */
@RestController
@RequestMapping(value = "/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/getUser")
    public ResultVO getUser() {
        return null;
    }

    @PostMapping("/updateUser")
    public ResultVO updateUser() {
        return null;
    }

    @GetMapping("/listGuestUser")
    public ResultVO listGuestUser() {
        return null;
    }

    @GetMapping("/getGuestUser/{userId}")
    public ResultVO getGuestUser(@PathVariable("userId") Long userId) {
        return null;
    }

    @GetMapping("/listFriends")
    public ResultVO<List<UserFriendListDTO>> listFriends(@RequestHeader String token) {
        //获取对应的值
        String tokenKey = RedisKeyConstant.generateUserTokenKey(token);
        String userDOJson = redisTemplate.opsForValue().get(tokenKey);

        if (StringUtils.isEmpty(userDOJson)) {
            return ResultVO.failure("没有登录", 10004, null);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        UserDO userDO;
        try {
            userDO = objectMapper.readValue(userDOJson, UserDO.class);
        } catch (IOException e) {
            e.printStackTrace();
            return ResultVO.failure("token解析错误", 10003, null);
        }

        //获取所有好友
        List<UserDO> friendList = userService.listFriends(userDO.getId());
        //标识是否在线
        List<UserFriendListDTO> friendOnlineList = new ArrayList<>();
        for (UserDO user : friendList) {
            DozerBeanMapper mapper = new DozerBeanMapper();
            UserFriendListDTO userFriendListDTO = mapper.map(user, UserFriendListDTO.class);

            userFriendListDTO.setUserId(user.getId());

            String key = RedisKeyConstant.generateOnlineUserKey(String.valueOf(user.getId()));
            String onlineJson = redisTemplate.opsForValue().get(key);
            if (!StringUtils.isEmpty(onlineJson)) {
                userFriendListDTO.setStatus("online");
            } else {
                userFriendListDTO.setStatus("offline");
            }

            friendOnlineList.add(userFriendListDTO);
        }

        return ResultVO.success(friendOnlineList);
    }

}
