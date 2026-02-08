package com.co.kc.imchat.endpoint.websocket;

import com.co.kc.imchat.application.ImPrivateAppService;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.model.enums.ParamsConstants;
import com.co.kc.imchat.model.enums.PushQueue;
import com.co.kc.imchat.model.io.im.ImPrivateMessageReceiveRequest;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImPrivateMessageSendCmd;
import com.co.kc.imchat.model.io.Result;
import com.co.kc.imchat.model.io.WsResponse;
import com.co.kc.imchat.model.io.im.ImPrivateMessageReadRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageRevokeRequest;
import com.co.kc.imchat.model.io.im.ImPrivateMessageSendRequest;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImPrivateWsController {
    private final ImPrivateAppService imPrivateAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/send")
    public Result<WsResponse> sendPrivateMessage(ImPrivateMessageSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageSendCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageSendCmdFrom(userId, request);
        imPrivateAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息接收
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/receive")
    public Result<WsResponse> receivePrivateMessage(ImPrivateMessageReceiveRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageReceiveCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageReceiveCmdFrom(userId, request);
        imPrivateAppService.receiveMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息读取
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/read")
    public Result<WsResponse> readPrivateMessage(ImPrivateMessageReadRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageReadCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageReadCmdFrom(userId, request);
        imPrivateAppService.readMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/private/revoke")
    public Result<WsResponse> revokePrivateMessage(ImPrivateMessageRevokeRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImPrivateMessageRevokeCmd command = ImMessageAppTransformer.INSTANCE.imPrivateMessageRevokeCmdFrom(userId, request);
        imPrivateAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

}
