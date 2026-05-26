package com.co.kc.imchat.interfaces.endpoint.websocket;

import com.co.kc.imchat.application.NotificationAckAppService;
import com.co.kc.imchat.common.model.io.Result;
import com.co.kc.imchat.interfaces.model.enums.ParamsConstants;
import com.co.kc.imchat.interfaces.model.io.WsResponse;
import com.co.kc.imchat.interfaces.model.io.im.ImNotificationReceiptRequest;
import com.co.kc.imchat.interfaces.support.websocket.PushQueue;
import com.co.kc.imchat.interfaces.transformer.ImMessageHttpIoTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ImNotificationWsController {
    private final NotificationAckAppService notificationAckAppService;

    @SendToUser(PushQueue.QUEUE_RESULT)
    @MessageMapping("/message/notification/ack")
    public Result<WsResponse> confirm(ImNotificationReceiptRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(ParamsConstants.USER_ID);
        notificationAckAppService.confirmMessage(ImMessageHttpIoTransformer.INSTANCE.imMessageAckCmdFrom(userId, request));
        return Result.success(new WsResponse(request.getRequestId()));
    }
}
