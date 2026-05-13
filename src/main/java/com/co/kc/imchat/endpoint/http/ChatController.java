package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.model.cqrs.command.chat.ImChatExitCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupCreateDTO;
import com.co.kc.imchat.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.model.io.chat.ImChatListResponse;
import com.co.kc.imchat.model.io.chat.ImGroupChatOpenRequest;
import com.co.kc.imchat.model.io.chat.ImGroupChatOpenResponse;
import com.co.kc.imchat.model.io.chat.ImGroupCreateRequest;
import com.co.kc.imchat.model.io.chat.ImGroupCreateResponse;
import com.co.kc.imchat.model.io.chat.ImGroupInviteMembersRequest;
import com.co.kc.imchat.model.io.chat.ImPrivateChatOpenRequest;
import com.co.kc.imchat.model.io.chat.ImPrivateChatOpenResponse;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.transformer.http.ImChatHttpIoTransformer;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api("聊天接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/im/chat")
public class ChatController {
    private final ChatAppService chatAppService;

    @GetMapping(value = "/getChatList")
    public ImChatListResponse getChatList() {
        Long userId = UserContextUtils.get().getUserId();
        List<ImChatItemDTO> imChatList = chatAppService.getChatList(new ImChatListQuery(userId));
        List<ImChatListResponse.ImChatItem> imChatItemList = ImChatHttpIoTransformer.INSTANCE.imChatItemListFrom(imChatList);
        return new ImChatListResponse(imChatItemList);
    }

    @PostMapping(value = "/openPrivateChat")
    public ImPrivateChatOpenResponse openPrivateChat(@RequestBody @Validated ImPrivateChatOpenRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImChatOpenDTO dto = chatAppService.openPrivateChat(new ImPrivateChatOpenCmd(userId, request.getPeerUserId()));
        return ImChatHttpIoTransformer.INSTANCE.imPrivateChatOpenResponseFrom(dto);
    }

    @PostMapping(value = "/createGroup")
    public ImGroupCreateResponse createGroup(@RequestBody @Validated ImGroupCreateRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupCreateCmd command = new ImGroupCreateCmd(userId, request.getMemberIds(), request.getGroupName());
        ImGroupCreateDTO dto = chatAppService.createGroup(command);
        return ImChatHttpIoTransformer.INSTANCE.imGroupCreateResponseFrom(dto);
    }

    @PostMapping(value = "/openGroupChat")
    public ImGroupChatOpenResponse openGroupChat(@RequestBody @Validated ImGroupChatOpenRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImChatOpenDTO dto = chatAppService.openGroupChat(new ImGroupChatOpenCmd(userId, request.getChatId()));
        return ImChatHttpIoTransformer.INSTANCE.imGroupChatOpenResponseFrom(dto);
    }

    @PostMapping(value = "/inviteGroupMembers")
    public void inviteGroupMembers(@RequestBody @Validated ImGroupInviteMembersRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.inviteGroupMembers(new ImGroupInviteMembersCmd(userId, request.getGroupId(), request.getMemberIds()));
    }

    @PostMapping(value = "/exitChat")
    public void exitChat() {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.exitChat(new ImChatExitCmd(userId));
    }

}
