package com.co.kc.imchat.service.message.interfaces.http;

import com.co.kc.imchat.service.message.application.ChatAppService;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.GroupAliasChangeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupChatHideCmd;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.service.message.model.io.chat.ImChatListResponse;
import com.co.kc.imchat.service.message.model.io.chat.PrivateChatHideRequest;
import com.co.kc.imchat.service.message.model.io.group.GroupAliasChangeRequest;
import com.co.kc.imchat.service.message.model.io.group.GroupChatHideRequest;
import com.co.kc.imchat.service.message.model.io.group.GroupChatOpenRequest;
import com.co.kc.imchat.service.message.model.io.group.GroupChatOpenResponse;
import com.co.kc.imchat.service.message.model.io.chat.ImPrivateChatOpenRequest;
import com.co.kc.imchat.service.message.model.io.chat.ImPrivateChatOpenResponse;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import com.co.kc.imchat.service.message.transformer.ImChatHttpIoTransformer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "聊天接口")
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
        ImPrivateChatOpenDTO dto = chatAppService.openPrivateChat(new ImPrivateChatOpenCmd(userId, request.getPeerUserId()));
        return ImChatHttpIoTransformer.INSTANCE.imPrivateChatOpenResponseFrom(dto);
    }

    @PostMapping(value = "/openGroupChat")
    public GroupChatOpenResponse openGroupChat(@RequestBody @Validated GroupChatOpenRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        GroupChatOpenDTO dto = chatAppService.openGroupChat(new ImGroupChatOpenCmd(userId, request.getChatId()));
        return ImChatHttpIoTransformer.INSTANCE.groupChatOpenResponseFrom(dto);
    }

    @PostMapping(value = "/hidePrivateChat")
    public void hidePrivateChat(@RequestBody @Validated PrivateChatHideRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.hidePrivateChat(new PrivateChatHideCmd(userId, request.getChatId()));
    }

    @PostMapping(value = "/hideGroupChat")
    public void hideGroupChat(@RequestBody @Validated GroupChatHideRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.hideGroupChat(new GroupChatHideCmd(userId, request.getChatId()));
    }

    @PostMapping(value = "/changeGroupAlias")
    public void changeGroupAlias(@RequestBody @Validated GroupAliasChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.changeGroupAlias(new GroupAliasChangeCmd(userId, request.getChatId(), request.getGroupAlias()));
    }

}
