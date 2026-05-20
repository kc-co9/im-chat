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
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "好友接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/friend")
public class FriendController {
    private final FriendAppService friendAppService;

    @Operation(summary = "好友列表")
    @GetMapping(value = "/friendList")
    public FriendListResponse friendList() {
        Long userId = UserContextUtils.get().getUserId();
        FriendListQuery query = new FriendListQuery(userId);
        List<FriendItemDTO> friends = friendAppService.getFriendList(query);
        List<FriendListResponse.FriendItem> friendItems = FriendHttpIoTransformer.INSTANCE.friendListFrom(friends);
        return new FriendListResponse(friendItems);
    }

    @Operation(summary = "好友详情")
    @GetMapping(value = "/friendDetail")
    public FriendDetailResponse friendDetail(@RequestParam(name = "friendUserId") Long friendUserId) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDetailQuery query = new FriendDetailQuery(userId, friendUserId);
        FriendDetailDTO friendDetailDTO = friendAppService.getFriendDetail(query);
        return FriendHttpIoTransformer.INSTANCE.friendDetailResponseFrom(friendDetailDTO);
    }

    @Operation(summary = "搜索好友")
    @GetMapping(value = "/searchFriend")
    public FriendSearchResponse searchFriend(@RequestParam(name = "email") String email) {
        FriendSearchQuery query = new FriendSearchQuery(email);
        List<FriendSearchDTO> friendSearchList = friendAppService.searchFriends(query);
        List<FriendSearchResponse.SearchItem> searchList =
                FriendHttpIoTransformer.INSTANCE.searchListFrom(friendSearchList);
        return new FriendSearchResponse(searchList);
    }

    @Operation(summary = "添加好友")
    @PostMapping(value = "/addFriend")
    public void addFriend(@RequestBody @Validated FriendAddRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAddCmd command = new FriendAddCmd(userId, request.getFriendUserId());
        friendAppService.addFriend(command);
    }

    @Operation(summary = "删除好友")
    @PostMapping(value = "/deleteFriend")
    public void deleteFriend(@RequestBody @Validated FriendDeleteRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDeleteCmd command = new FriendDeleteCmd(userId, request.getFriendUserId());
        friendAppService.deleteFriend(command);
    }

    @Operation(summary = "拉黑好友")
    @PostMapping(value = "/blockFriend")
    public void blockFriend(@RequestBody @Validated FriendBlockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendBlockCmd command = new FriendBlockCmd(userId, request.getFriendUserId());
        friendAppService.blockFriend(command);
    }

    @Operation(summary = "取消拉黑好友")
    @PostMapping(value = "/unblockFriend")
    public void unblockFriend(@RequestBody @Validated FriendUnblockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendUnblockCmd command = new FriendUnblockCmd(userId, request.getFriendUserId());
        friendAppService.unblockFriend(command);
    }

    @Operation(summary = "修改好友备注")
    @PostMapping(value = "/changeFriendAlias")
    public void changeFriendAlias(@RequestBody @Validated FriendAliasChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAliasChangeCmd command = new FriendAliasChangeCmd(userId, request.getFriendUserId(), request.getFriendAlias());
        friendAppService.changeFriendAlias(command);
    }

}
