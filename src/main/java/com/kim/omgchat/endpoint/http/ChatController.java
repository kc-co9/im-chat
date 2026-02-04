package com.kim.omgchat.endpoint.http;

import com.kim.omgchat.application.ChatAppService;
import com.kim.omgchat.model.cqrs.command.chat.ImChatExitCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImGroupChatCreateCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImGroupChatEnterCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImPrivateChatEnterCmd;
import com.kim.omgchat.model.cqrs.dto.im.ImChatCreateDTO;
import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.model.io.chat.ImGroupChatCreateRequest;
import com.kim.omgchat.model.io.chat.ImGroupChatCreateResponse;
import com.kim.omgchat.model.io.chat.ImGroupChatEnterRequest;
import com.kim.omgchat.model.io.chat.ImPrivateChatEnterRequest;
import com.kim.omgchat.model.io.chat.ImPrivateChatEnterResponse;
import com.kim.omgchat.support.context.UserContextUtils;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api("聊天接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/im/chat")
public class ChatController {
    private final ChatAppService chatAppService;

    @PostMapping(value = "/enterPrivateChat")
    public Result<ImPrivateChatEnterResponse> enterPrivateChat(@RequestBody @Validated ImPrivateChatEnterRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateChatEnterCmd command = new ImPrivateChatEnterCmd(userId, request.getReceiverId());
        ImChatCreateDTO imChatCreateDTO = chatAppService.enterPrivateChat(command);
        return Result.success(new ImPrivateChatEnterResponse(imChatCreateDTO.getChatId()));
    }

    @PostMapping(value = "/createGroupChat")
    public Result<ImGroupChatCreateResponse> createGroupChat(@RequestBody @Validated ImGroupChatCreateRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupChatCreateCmd command = new ImGroupChatCreateCmd(userId, request.getMemberIds(), request.getGroupName());
        ImChatCreateDTO imChatCreateDTO = chatAppService.createGroupChat(command);
        return Result.success(new ImGroupChatCreateResponse(imChatCreateDTO.getChatId()));
    }

    @PostMapping(value = "/enterGroupChat")
    public Result<?> enterGroupChat(@RequestBody @Validated ImGroupChatEnterRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupChatEnterCmd command = new ImGroupChatEnterCmd(request.getChatId(), userId);
        chatAppService.enterGroupChat(command);
        return Result.success();
    }

    @PostMapping(value = "/exitChat")
    public Result<?> exitChat() {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.exitChat(new ImChatExitCmd(userId));
        return Result.success();
    }

}
