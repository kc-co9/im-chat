package com.kim.omgchat.controller;

import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.service.UserMessageService;
import com.kim.omgchat.vo.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/message")
public class UserMessageController {

    @Autowired
    private UserMessageService userMessageService;

    @GetMapping("/")
    public ResultVO listUserMessage(@RequestHeader("token") String token) {


        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        List<UserMessageDO> list = userMessageService.listFriendsMessageFor3d(userMessageQueryDTO);

        return null;
    }


}
