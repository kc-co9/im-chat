package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.model.io.friend.FriendDetailResponse;
import com.co.kc.imchat.model.io.friend.FriendListResponse;
import com.co.kc.imchat.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.transformer.FriendHttpIoTransformer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api("好友接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/friend")
public class FriendController {
    private final FriendAppService friendAppService;

    @ApiOperation("好友列表")
    @GetMapping("/friendList")
    public FriendListResponse friendList() {
        Long userId = UserContextUtils.get().getUserId();
        FriendListQuery query = new FriendListQuery(userId);
        List<FriendItemDTO> friends = friendAppService.getFriendList(query);
        List<FriendListResponse.FriendItem> friendItems = FriendHttpIoTransformer.INSTANCE.friendListFrom(friends);
        return new FriendListResponse(friendItems);
    }

    @ApiOperation("好友详情")
    @GetMapping("/friendDetail")
    public FriendDetailResponse friendDetail(@RequestParam("friendId") Long friendId) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDetailQuery query = new FriendDetailQuery(userId, friendId);
        FriendDetailDTO friendDetailDTO = friendAppService.getFriendDetail(query);
        return FriendHttpIoTransformer.INSTANCE.friendDetailResponseFrom(friendDetailDTO);
    }


}
