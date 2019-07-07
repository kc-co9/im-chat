package com.kim.omgchat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kim.omgchat.component.UserComponent;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserAddDTO;
import com.kim.omgchat.holder.WebOnlineUser;
import com.kim.omgchat.holder.WebToken;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.vo.ResultVO;
import com.kim.omgchat.vo.user.UserLoginVO;
import com.kim.omgchat.vo.user.UserLogoutVO;
import com.kim.omgchat.vo.user.UserQueryDTO;
import com.kim.omgchat.vo.user.UserRegisterVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

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

    @ApiOperation("注册接口")
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

    @ApiOperation("登录接口")
    @PostMapping("/login")
    public ResultVO<String> login(@RequestBody UserLoginVO userLoginVO) throws JsonProcessingException {
        UserQueryDTO userQueryDTO = new UserQueryDTO();
        userQueryDTO.setEmail(userLoginVO.getEmail());
        UserDO userDO = userService.getUser(userQueryDTO);
        if (!userDO.getPassword().equals(userLoginVO.getPassword())) {
            return ResultVO.failure("密码错误", 10001, "");
        }

        //生成token返回
        WebToken webToken = new WebToken();
        webToken.setUserId(userDO.getId());
        webToken.setEmail(userDO.getEmail());
        webToken.setCreateTime(new Date());
        String token = webToken.generate();

        //存储登录信息
        WebOnlineUser webOnlineUser = new WebOnlineUser();
        webOnlineUser.setUserId(userDO.getId());
        webOnlineUser.setEmail(userDO.getEmail());
        webOnlineUser.setNickname(userDO.getNickname());
        webOnlineUser.setAvatar(userDO.getAvatar());

        String onlineKey = generateOnlineUserKey(Long.toString(userDO.getId()));
        redisTemplate.opsForValue().set(onlineKey, webOnlineUser.toString());

        //存储用户信息
        String tokenKey = generateUserTokenKey(token);
        redisTemplate.opsForValue().set(tokenKey, userDO.toString());

        return ResultVO.success(token);
    }

    @ApiOperation("退出登录接口")
    @PostMapping("/logout")
    public ResultVO<Boolean> logout(@RequestHeader("token") String token, @RequestBody UserLogoutVO userLogoutVO) {
        //删除在线记录
        String onlineKey = generateOnlineUserKey(String.valueOf(userLogoutVO.getUserId()));
        redisTemplate.delete(onlineKey);

        //删除用户信息
        String tokenKey = generateUserTokenKey(token);
        redisTemplate.delete(tokenKey);

        return ResultVO.success();
    }
}
