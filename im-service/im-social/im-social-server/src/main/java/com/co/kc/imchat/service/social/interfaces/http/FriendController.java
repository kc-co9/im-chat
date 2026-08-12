package com.co.kc.imchat.service.social.interfaces.http;

import com.co.kc.imchat.service.social.application.FriendAppService;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendAddCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendAliasChangeCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendBlockCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendDeleteCmd;
import com.co.kc.imchat.service.social.model.cqrs.command.friend.FriendUnblockCmd;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendSearchDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendSearchQuery;
import com.co.kc.imchat.service.social.model.io.friend.FriendAddRequest;
import com.co.kc.imchat.service.social.model.io.friend.FriendAliasChangeRequest;
import com.co.kc.imchat.service.social.model.io.friend.FriendBlockRequest;
import com.co.kc.imchat.service.social.model.io.friend.FriendDeleteRequest;
import com.co.kc.imchat.service.social.model.io.friend.FriendSearchResponse;
import com.co.kc.imchat.service.social.model.io.friend.FriendUnblockRequest;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendDetailDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendDetailQuery;
import com.co.kc.imchat.service.social.model.io.friend.FriendDetailResponse;
import com.co.kc.imchat.service.social.model.io.friend.FriendListResponse;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendListQuery;
import com.co.kc.imchat.service.social.transformer.FriendHttpIoTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@ConditionalOnBean(FriendAppService.class)
@RequiredArgsConstructor
@RequestMapping(value = "/friend")
public class FriendController {
    private final FriendAppService friendAppService;

    @GetMapping(value = "/friendList")
    public FriendListResponse friendList() {
        Long userId = UserContextUtils.get().getUserId();
        FriendListQuery query = new FriendListQuery(userId);
        List<FriendItemDTO> friends = friendAppService.getFriendList(query);
        List<FriendListResponse.FriendItem> friendItems = FriendHttpIoTransformer.INSTANCE.friendListFrom(friends);
        return new FriendListResponse(friendItems);
    }

    @GetMapping(value = "/friendDetail")
    public FriendDetailResponse friendDetail(@RequestParam(name = "friendUserId") Long friendUserId) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDetailQuery query = new FriendDetailQuery(userId, friendUserId);
        FriendDetailDTO friendDetailDTO = friendAppService.getFriendDetail(query);
        return FriendHttpIoTransformer.INSTANCE.friendDetailResponseFrom(friendDetailDTO);
    }

    @GetMapping(value = "/searchFriend")
    public FriendSearchResponse searchFriend(@RequestParam(name = "email") String email) {
        FriendSearchQuery query = new FriendSearchQuery(email);
        List<FriendSearchDTO> friendSearchList = friendAppService.searchFriends(query);
        List<FriendSearchResponse.SearchItem> searchList =
                FriendHttpIoTransformer.INSTANCE.searchListFrom(friendSearchList);
        return new FriendSearchResponse(searchList);
    }

    @PostMapping(value = "/addFriend")
    public void addFriend(@RequestBody @Validated FriendAddRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAddCmd command = new FriendAddCmd(userId, request.getFriendUserId());
        friendAppService.addFriend(command);
    }

    @PostMapping(value = "/deleteFriend")
    public void deleteFriend(@RequestBody @Validated FriendDeleteRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendDeleteCmd command = new FriendDeleteCmd(userId, request.getFriendUserId());
        friendAppService.deleteFriend(command);
    }

    @PostMapping(value = "/blockFriend")
    public void blockFriend(@RequestBody @Validated FriendBlockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendBlockCmd command = new FriendBlockCmd(userId, request.getFriendUserId());
        friendAppService.blockFriend(command);
    }

    @PostMapping(value = "/unblockFriend")
    public void unblockFriend(@RequestBody @Validated FriendUnblockRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendUnblockCmd command = new FriendUnblockCmd(userId, request.getFriendUserId());
        friendAppService.unblockFriend(command);
    }

    @PostMapping(value = "/changeFriendAlias")
    public void changeFriendAlias(@RequestBody @Validated FriendAliasChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        FriendAliasChangeCmd command = new FriendAliasChangeCmd(userId, request.getFriendUserId(), request.getFriendAlias());
        friendAppService.changeFriendAlias(command);
    }

}
