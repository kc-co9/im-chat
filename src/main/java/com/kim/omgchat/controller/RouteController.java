package com.kim.omgchat.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserOnlineDTO;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.vo.user.UserQueryDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static com.kim.omgchat.constant.RedisKeyConstant.generateUserTokenKey;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 11:56
 */
@Api("路由")
@Controller
@RequestMapping("/route")
public class RouteController {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @ApiOperation("登录页面")
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @ApiOperation("注册页面")
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @ApiOperation("首页")
    @GetMapping("/index")
    public String indexPage(@RequestParam("token") String token, ModelMap modelMap) throws IOException {
        //获取当前用户
        String key = generateUserTokenKey(token);
        String userJson = redisTemplate.opsForValue().get(key);
        ObjectMapper objectMapper = new ObjectMapper();
        UserDO userDO = objectMapper.readValue(userJson, UserDO.class);

        //类型转换
        DozerBeanMapper mapper = new DozerBeanMapper();

        UserOnlineDTO userOnlineDTO = mapper.map(userDO, UserOnlineDTO.class);
        userOnlineDTO.setUserId(userDO.getId());

        modelMap.addAttribute("user", userOnlineDTO);
        return "index";
    }

//    @GetMapping
//    public String groupChatPage() {
//        return null;
//    }

    @GetMapping("/user/chat/{userId}")
    public String userChatPage(@RequestParam("token") String token, @PathVariable("userId") Long userId, ModelMap modelMap) throws IOException {
        //获取当前用户
        String key = generateUserTokenKey(token);
        String userJson = redisTemplate.opsForValue().get(key);
        ObjectMapper objectMapper = new ObjectMapper();
        UserDO userDO = objectMapper.readValue(userJson, UserDO.class);

        //获取聊天用户
        UserQueryDTO userQueryDTO = new UserQueryDTO();
        userQueryDTO.setUserId(userId);
        UserDO friend = userService.getUser(userQueryDTO);

        //类型转换
        DozerBeanMapper mapper = new DozerBeanMapper();
        UserOnlineDTO friendOnlineDTO = mapper.map(friend, UserOnlineDTO.class);
        friendOnlineDTO.setUserId(friend.getId());

        UserOnlineDTO userOnlineDTO = mapper.map(userDO, UserOnlineDTO.class);
        userOnlineDTO.setUserId(userDO.getId());

        modelMap.addAttribute("user", userOnlineDTO);
        modelMap.addAttribute("friend", friendOnlineDTO);

        return "userChat";
    }

//    @GetMapping
//    public String userSettting() {
//        return null;
//    }
//
//    @GetMapping
//    public String systemSettting() {
//        return null;
//    }

}
