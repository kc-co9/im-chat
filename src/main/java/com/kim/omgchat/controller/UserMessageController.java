package com.kim.omgchat.controller;

import com.github.pagehelper.PageInfo;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import com.kim.omgchat.enums.MessageStatusEnum;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.holder.WebUserHolder;
import com.kim.omgchat.service.UserMessageService;
import com.kim.omgchat.vo.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/message")
public class UserMessageController {

    @Autowired
    private UserMessageService userMessageService;

    @GetMapping("/listFriendsLatestMsg")
    public ResultVO<List<UserMessageDO>> listFriendsLatestMsg() {
        WebUser webUser = WebUserHolder.get();

        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setUserId(webUser.getUserId());
        List<UserMessageDO> list = userMessageService.listFriendsMessageFor3d(userMessageQueryDTO);

        return ResultVO.success(list);
    }

    @GetMapping("/listChatMsgWithFriend/{toUid}")
    public ResultVO<PageInfo<UserMessageDO>> listChatMsgWithFriend(@PathVariable("toUid") Long toUid,
                                                                   @RequestParam("pageIndex") Integer pageIndex,
                                                                   @RequestParam("pageSize") Integer pageSize) {

        WebUser webUser = WebUserHolder.get();

        UserMessageQueryDTO userMessageQueryDTO = new UserMessageQueryDTO();
        userMessageQueryDTO.setFromUserId(webUser.getUserId());
        userMessageQueryDTO.setToUserId(toUid);
        userMessageQueryDTO.setPageIndex(pageIndex);
        userMessageQueryDTO.setPageSize(pageSize);

        PageInfo<UserMessageDO> pageInfo = userMessageService.pageChatMsgWithFriend(userMessageQueryDTO);

        // 修改消息状态
        userMessageService.updateUserMessageStatus(userMessageQueryDTO , MessageStatusEnum.READ);

        //构造用户信息
        return ResultVO.success(pageInfo);
    }

}
