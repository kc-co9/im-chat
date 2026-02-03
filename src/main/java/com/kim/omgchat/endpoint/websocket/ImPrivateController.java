package com.kim.omgchat.endpoint.websocket;

import com.kim.omgchat.application.PrivateAppService;
import com.kim.omgchat.infrastructure.context.UserContextUtils;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.model.io.WsResponse;
import com.kim.omgchat.model.io.im.ImPrivateMessageReadRequest;
import com.kim.omgchat.model.io.im.ImPrivateMessageRevokeRequest;
import com.kim.omgchat.model.io.im.ImPrivateMessageSendRequest;
import com.kim.omgchat.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImPrivateController {
    private final PrivateAppService privateAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser("/queue/result")
    @MessageMapping("/message/private/send")
    public Result<WsResponse> sendPrivateMessage(ImPrivateMessageSendRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageSendCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageSendCmdFrom(userId, request);
        privateAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息读取
     */
    @SendToUser("/queue/result")
    @MessageMapping("/message/private/read")
    public Result<WsResponse> readPrivateMessage(ImPrivateMessageReadRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageReadCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageReadCmdFrom(userId, request);
        privateAppService.readMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser("/queue/result")
    @MessageMapping("/message/private/revoke")
    public Result<WsResponse> revokePrivateMessage(ImPrivateMessageRevokeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageRevokeCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageReadCmdFrom(userId, request);
        privateAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

}
