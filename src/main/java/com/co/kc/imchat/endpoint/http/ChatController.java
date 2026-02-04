package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.model.cqrs.command.chat.ImChatExitCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatEnterCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatEnterCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatCreateDTO;
import com.co.kc.imchat.model.io.chat.ImGroupChatCreateRequest;
import com.co.kc.imchat.model.io.chat.ImGroupChatCreateResponse;
import com.co.kc.imchat.model.io.chat.ImGroupChatEnterRequest;
import com.co.kc.imchat.model.io.chat.ImPrivateChatEnterRequest;
import com.co.kc.imchat.model.io.chat.ImPrivateChatEnterResponse;
import com.co.kc.imchat.support.context.UserContextUtils;
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
    public ImPrivateChatEnterResponse enterPrivateChat(@RequestBody @Validated ImPrivateChatEnterRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateChatEnterCmd command = new ImPrivateChatEnterCmd(userId, request.getReceiverId());
        ImChatCreateDTO imChatCreateDTO = chatAppService.enterPrivateChat(command);
        return new ImPrivateChatEnterResponse(imChatCreateDTO.getChatId());
    }

    @PostMapping(value = "/createGroupChat")
    public ImGroupChatCreateResponse createGroupChat(@RequestBody @Validated ImGroupChatCreateRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupChatCreateCmd command = new ImGroupChatCreateCmd(userId, request.getMemberIds(), request.getGroupName());
        ImChatCreateDTO imChatCreateDTO = chatAppService.createGroupChat(command);
        return new ImGroupChatCreateResponse(imChatCreateDTO.getChatId());
    }

    @PostMapping(value = "/enterGroupChat")
    public void enterGroupChat(@RequestBody @Validated ImGroupChatEnterRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupChatEnterCmd command = new ImGroupChatEnterCmd(request.getChatId(), userId);
        chatAppService.enterGroupChat(command);
    }

    @PostMapping(value = "/exitChat")
    public void exitChat() {
        Long userId = UserContextUtils.get().getUserId();
        chatAppService.exitChat(new ImChatExitCmd(userId));
    }

}
