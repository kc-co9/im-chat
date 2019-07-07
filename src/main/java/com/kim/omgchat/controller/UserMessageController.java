package com.kim.omgchat.controller;

import com.github.pagehelper.PageInfo;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageDTO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.holder.WebUserHolder;
import com.kim.omgchat.service.UserMessageService;
import com.kim.omgchat.vo.ResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Api("用户消息接口")
@RestController
@RequestMapping("/user/message")
public class UserMessageController {

    @Autowired
    private UserMessageService userMessageService;

    @ApiOperation("获取与每个其他用户的最新消息列表")
    @GetMapping("/listFriendsLatestMsg")
    public ResultVO<List<UserMessageDTO>> listFriendsLatestMsg() {
        WebUser webUser = WebUserHolder.get();

        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setUserId(webUser.getUserId());
        List<UserMessageDTO> list = userMessageService.listFriendsMessage(userMessageQueryDTO);

        Long uid = WebUserHolder.get().getUserId();

        List<UserMessageDTO> result = new ArrayList<>();
        for (UserMessageDTO userMessageDTO : list) {
            if (userMessageDTO.getToUid().equals(uid)) {
                userMessageDTO.setToUid(userMessageDTO.getFromUid());
                userMessageDTO.setToUserNickname(userMessageDTO.getFromUserNickname());
                userMessageDTO.setToUserAvatar(userMessageDTO.getFromUserAvatar());

                userMessageDTO.setFromUid(uid);
                userMessageDTO.setFromUserAvatar(null);
                userMessageDTO.setFromUserNickname(null);
            }

            result.add(userMessageDTO);
        }

        return ResultVO.success(result);
    }

    @ApiOperation("查看本用户与其他用户的聊天记录")
    @GetMapping("/listChatMsgWithFriend/{toUid}")
    public ResultVO<PageInfo<UserMessageDTO>> listChatMsgWithFriend(@PathVariable("toUid") Long toUid,
                                                                    @RequestParam("pageIndex") Integer pageIndex,
                                                                    @RequestParam("pageSize") Integer pageSize) {
        //获取用户hold
        WebUser webUser = WebUserHolder.get();

        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setFromUserId(webUser.getUserId());
        userMessageQueryDTO.setToUserId(toUid);
        userMessageQueryDTO.setPageIndex(pageIndex);
        userMessageQueryDTO.setPageSize(pageSize);

        PageInfo<UserMessageDTO> pageInfo = userMessageService.pageChatMsgWithFriend(userMessageQueryDTO);
        Collections.reverse(pageInfo.getList());

        // 修改消息状态
        userMessageService.updateUserMessageStatus(userMessageQueryDTO, MessageStatusEnum.READ);

        //构造用户信息
        return ResultVO.success(pageInfo);
    }


}
