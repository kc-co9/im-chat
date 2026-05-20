package com.co.kc.imchat.interfaces.endpoint.http;

import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.application.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendSearchQuery;
import com.co.kc.imchat.interfaces.model.io.friend.FriendAddRequest;
import com.co.kc.imchat.interfaces.model.io.friend.FriendAliasChangeRequest;
import com.co.kc.imchat.interfaces.model.io.friend.FriendBlockRequest;
import com.co.kc.imchat.interfaces.model.io.friend.FriendDeleteRequest;
import com.co.kc.imchat.interfaces.model.io.friend.FriendSearchResponse;
import com.co.kc.imchat.interfaces.model.io.friend.FriendUnblockRequest;
import com.co.kc.imchat.interfaces.support.context.UserContextUtils;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.interfaces.model.io.friend.FriendDetailResponse;
import com.co.kc.imchat.interfaces.model.io.friend.FriendListResponse;
import com.co.kc.imchat.application.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.application.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.interfaces.transformer.FriendHttpIoTransformer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
    public FriendDetailResponse friendDetail(@RequestParam("friendUserId") Long friendUserId) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDetailQuery query = new FriendDetailQuery(userId, friendUserId);
        FriendDetailDTO friendDetailDTO = friendAppService.getFriendDetail(query);
        return FriendHttpIoTransformer.INSTANCE.friendDetailResponseFrom(friendDetailDTO);
    }

    @ApiOperation("搜索好友")
    @GetMapping("/searchFriend")
    public FriendSearchResponse searchFriend(@RequestParam("email") String email) {
        FriendSearchQuery query = new FriendSearchQuery(email);
        List<FriendSearchDTO> friendSearchList = friendAppService.searchFriends(query);
        List<FriendSearchResponse.SearchItem> searchList =
                FriendHttpIoTransformer.INSTANCE.searchListFrom(friendSearchList);
        return new FriendSearchResponse(searchList);
    }

    @ApiOperation("添加好友")
    @PostMapping("/addFriend")
    public void addFriend(@RequestBody @Validated FriendAddRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAddCmd command = new FriendAddCmd(userId, request.getFriendUserId());
        friendAppService.addFriend(command);
    }

    @ApiOperation("删除好友")
    @PostMapping("/deleteFriend")
    public void deleteFriend(@RequestBody @Validated FriendDeleteRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDeleteCmd command = new FriendDeleteCmd(userId, request.getFriendUserId());
        friendAppService.deleteFriend(command);
    }

    @ApiOperation("拉黑好友")
    @PostMapping("/blockFriend")
    public void blockFriend(@RequestBody @Validated FriendBlockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendBlockCmd command = new FriendBlockCmd(userId, request.getFriendUserId());
        friendAppService.blockFriend(command);
    }

    @ApiOperation("取消拉黑好友")
    @PostMapping("/unblockFriend")
    public void unblockFriend(@RequestBody @Validated FriendUnblockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendUnblockCmd command = new FriendUnblockCmd(userId, request.getFriendUserId());
        friendAppService.unblockFriend(command);
    }

    @ApiOperation("修改好友备注")
    @PostMapping("/changeFriendAlias")
    public void changeFriendAlias(@RequestBody @Validated FriendAliasChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAliasChangeCmd command = new FriendAliasChangeCmd(userId, request.getFriendUserId(), request.getFriendAlias());
        friendAppService.changeFriendAlias(command);
    }

}
