package com.co.kc.imchat.endpoint.websocket;

import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.model.enums.ParamsConstants;
import com.co.kc.imchat.model.enums.PushQueue;
import com.co.kc.imchat.model.io.Result;
import com.co.kc.imchat.model.io.WsResponse;
import com.co.kc.imchat.model.io.group.GroupMessageReceiveRequest;
import com.co.kc.imchat.model.io.group.GroupMessageReadRequest;
import com.co.kc.imchat.model.io.group.GroupMessageRevokeRequest;
import com.co.kc.imchat.model.io.group.GroupMessageSendRequest;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImGroupWsController {
    private final GroupMessageAppService groupMessageAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/send")
    public Result<WsResponse> sendGroupMessage(GroupMessageSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        GroupMessageSendCmd command = ImMessageAppTransformer.INSTANCE.groupMessageSendCmdFrom(userId, request);
        groupMessageAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息接收
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/receive")
    public Result<WsResponse> receiveGroupMessage(GroupMessageReceiveRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        GroupMessageReceiveCmd command = ImMessageAppTransformer.INSTANCE.groupMessageReceiveCmdFrom(userId, request);
        groupMessageAppService.receiveMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息读取
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/read")
    public Result<WsResponse> readGroupMessage(GroupMessageReadRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        GroupMessageReadCmd command = ImMessageAppTransformer.INSTANCE.groupMessageReadCmdFrom(userId, request);
        groupMessageAppService.readMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/revoke")
    public Result<WsResponse> revokeGroupMessage(GroupMessageRevokeRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        GroupMessageRevokeCmd command = ImMessageAppTransformer.INSTANCE.groupMessageRevokeCmdFrom(userId, request);
        groupMessageAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }
}
