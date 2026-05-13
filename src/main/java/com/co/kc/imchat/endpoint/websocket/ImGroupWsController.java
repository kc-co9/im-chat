package com.co.kc.imchat.endpoint.websocket;

import com.co.kc.imchat.application.ImGroupAppService;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.co.kc.imchat.model.enums.ParamsConstants;
import com.co.kc.imchat.model.enums.PushQueue;
import com.co.kc.imchat.model.io.Result;
import com.co.kc.imchat.model.io.WsResponse;
import com.co.kc.imchat.model.io.im.ImGroupMessageRevokeRequest;
import com.co.kc.imchat.model.io.im.ImGroupMessageSendRequest;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImGroupWsController {
    private final ImGroupAppService imGroupAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/send")
    public Result<WsResponse> sendGroupMessage(ImGroupMessageSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImGroupMessageSendCmd command = ImMessageAppTransformer.INSTANCE.imGroupMessageSendCmdFrom(userId, request);
        imGroupAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/revoke")
    public Result<WsResponse> revokeGroupMessage(ImGroupMessageRevokeRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        ImGroupMessageRevokeCmd command = ImMessageAppTransformer.INSTANCE.imGroupMessageRevokeCmdFrom(userId, request);
        imGroupAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }
}
