package com.kim.omgchat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.kim.omgchat.component.UserComponent;
import com.kim.omgchat.constant.RedisKeyPrefixConstant;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserAddDTO;
import com.kim.omgchat.dto.UserOnlineDTO;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.vo.ResultVO;
import com.kim.omgchat.vo.user.UserLoginVO;
import com.kim.omgchat.vo.user.UserLogoutVO;
import com.kim.omgchat.vo.user.UserQueryDTO;
import com.kim.omgchat.vo.user.UserRegisterVO;
import jdk.nashorn.internal.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import static com.kim.omgchat.constant.RedisKeyConstant.generateOnlineUserKey;
import static com.kim.omgchat.constant.RedisKeyConstant.generateUserTokenKey;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 16:27
 */
@RestController
@RequestMapping(value = "/account")
public class AccountController {
    @Autowired
    private UserService userService;

    @Autowired
    private UserComponent userComponent;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/register")
    public ResultVO<Boolean> register(@RequestBody UserRegisterVO userRegisterVO) {
        UserAddDTO userAddDTO = new UserAddDTO();
        userAddDTO.setEmail(userRegisterVO.getEmail());
        userAddDTO.setPassword(userRegisterVO.getPassword());

        Boolean success = userService.saveUser(userAddDTO);
        if (!success) {
            return ResultVO.failure("注册失败", 10002, false);
        }
        return ResultVO.success();
    }

    @PostMapping("/login")
    public ResultVO<String> login(@RequestBody UserLoginVO userLoginVO) throws JsonProcessingException {
        UserQueryDTO userQueryDTO = new UserQueryDTO();
        userQueryDTO.setEmail(userLoginVO.getEmail());
        UserDO userDO = userService.getUser(userQueryDTO);
        if (!userDO.getPassword().equals(userLoginVO.getPassword())) {
            return ResultVO.failure("密码错误", 10001, "");
        }
        //通知好友和群该用户上线了

        //存储登录信息
        UserOnlineDTO userOnlineDTO = new UserOnlineDTO();
        userOnlineDTO.setUserId(userDO.getId());
        userOnlineDTO.setNickname(userDO.getNickname());

        String onlineKey = generateOnlineUserKey(Long.toString(userDO.getId()));
        redisTemplate.opsForValue().set(onlineKey, userOnlineDTO.toString());

        //生成token返回
        String token = userComponent.generateToken(userDO);

        //存储用户信息
        ObjectMapper mapper = new ObjectMapper();
        String userInfoJson = mapper.writeValueAsString(userDO);
        String tokenKey = generateUserTokenKey(token);
        redisTemplate.opsForValue().set(tokenKey, userInfoJson);

        return ResultVO.success(token);
    }

    @PostMapping("/logout")
    public ResultVO<Boolean> logout(@RequestHeader("token") String token, @RequestBody UserLogoutVO userLogoutVO) {
        //删除在线信息
        String onlineKey = generateOnlineUserKey(String.valueOf(userLogoutVO.getUserId()));
        redisTemplate.delete(onlineKey);

        //删除token
        String tokenKey = generateUserTokenKey(token);
        redisTemplate.delete(tokenKey);

        return ResultVO.success();
    }
}
