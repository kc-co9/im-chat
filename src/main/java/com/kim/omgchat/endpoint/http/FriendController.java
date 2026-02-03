package com.kim.omgchat.endpoint.http;

import com.kim.omgchat.application.FriendAppService;
import com.kim.omgchat.support.context.UserContextUtils;
import com.kim.omgchat.model.cqrs.dto.friend.FriendDetailDTO;
import com.kim.omgchat.model.cqrs.query.friend.FriendDetailQuery;
import com.kim.omgchat.model.io.friend.FriendDetailResponse;
import com.kim.omgchat.model.io.friend.FriendListResponse;
import com.kim.omgchat.model.cqrs.dto.friend.FriendItemDTO;
import com.kim.omgchat.model.cqrs.query.friend.FriendListQuery;
import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.transformer.FriendHttpIoTransformer;
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
    public Result<FriendListResponse> friendList() {
        Long userId = UserContextUtils.get().getUserId();
        FriendListQuery query = new FriendListQuery(userId);
        List<FriendItemDTO> friends = friendAppService.getFriendList(query);
        List<FriendListResponse.FriendItem> friendItems = FriendHttpIoTransformer.INSTANCE.friendListFrom(friends);
        return Result.success(new FriendListResponse(friendItems));
    }

    @ApiOperation("好友详情")
    @GetMapping("/friendDetail")
    public Result<FriendDetailResponse> friendDetail(@RequestParam("friendId") Long friendId) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDetailQuery query = new FriendDetailQuery(userId, friendId);
        FriendDetailDTO friendDetailDTO = friendAppService.getFriendDetail(query);
        return Result.success(FriendHttpIoTransformer.INSTANCE.friendDetailResponseFrom(friendDetailDTO));
    }


}
