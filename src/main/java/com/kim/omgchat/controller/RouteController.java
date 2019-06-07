package com.kim.omgchat.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.dto.UserOnlineDTO;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.vo.user.UserQueryDTO;
import org.dozer.DozerBeanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

import static com.kim.omgchat.constant.RedisKeyConstant.generateOnlineUserKey;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 11:56
 */
@Controller
@RequestMapping("/route")
public class RouteController {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/index")
    public String indexPage() {

        return "index";
    }

//    @GetMapping
//    public String groupChatPage() {
//        return null;
//    }

    @GetMapping("/user/chat/{userId}")
    public String userChatPage(@PathVariable("userId") Long userId, ModelMap modelMap) throws IOException {

        UserQueryDTO userQueryDTO = new UserQueryDTO();
        userQueryDTO.setUserId(userId);
        UserDO userDO = userService.getUser(userQueryDTO);

        DozerBeanMapper mapper = new DozerBeanMapper();
        UserOnlineDTO userOnlineDTO = mapper.map(userDO, UserOnlineDTO.class);

        modelMap.addAttribute("friend", userOnlineDTO);

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
