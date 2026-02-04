package com.kim.omgchat.endpoint.websocket;

import com.kim.omgchat.application.ImGroupAppService;
import com.kim.omgchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.kim.omgchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.kim.omgchat.model.enums.PushQueue;
import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.model.io.WsResponse;
import com.kim.omgchat.model.io.im.ImGroupMessageRevokeRequest;
import com.kim.omgchat.model.io.im.ImGroupMessageSendRequest;
import com.kim.omgchat.support.context.UserContextUtils;
import com.kim.omgchat.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImGroupController {
    private final ImGroupAppService imGroupAppService;

    /**
     * 处理客户端消息发送
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/send")
    public Result<WsResponse> sendGroupMessage(ImGroupMessageSendRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupMessageSendCmd command = ImMessageAppTransformer.INSTANCE.imGroupMessageSendCmdFrom(userId, request);
        imGroupAppService.sendMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }

    /**
     * 处理客户端消息撤回
     */
    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/group/revoke")
    public Result<WsResponse> revokeGroupMessage(ImGroupMessageRevokeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupMessageRevokeCmd command = ImMessageAppTransformer.INSTANCE.imGroupMessageRevokeCmdFrom(userId, request);
        imGroupAppService.revokeMessage(command);
        return Result.success(new WsResponse(request.getRequestId()));
    }
}
