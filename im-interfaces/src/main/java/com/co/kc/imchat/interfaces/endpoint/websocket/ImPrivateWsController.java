package com.co.kc.imchat.interfaces.endpoint.websocket;

import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.interfaces.model.enums.ParamsConstants;
import com.co.kc.imchat.interfaces.support.websocket.PushQueue;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.common.model.io.Result;
import com.co.kc.imchat.interfaces.model.io.WsResponse;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageReadRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageRevokeRequest;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageSendRequest;
import com.co.kc.imchat.interfaces.transformer.ImMessageHttpIoTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImPrivateWsController {
    private final PrivateMessageAppService privateMessageAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/send")
    public Result<WsResponse> sendPrivateMessage(ImPrivateMessageSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageSendCmd command = ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageSendCmdFrom(userId, request);
        privateMessageAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息读取
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/read")
    public Result<WsResponse> readPrivateMessage(ImPrivateMessageReadRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageReadCmd command = ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageReadCmdFrom(userId, request);
        privateMessageAppService.readMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/revoke")
    public Result<WsResponse> revokePrivateMessage(ImPrivateMessageRevokeRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageRevokeCmd command = ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageRevokeCmdFrom(userId, request);
        privateMessageAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

}
