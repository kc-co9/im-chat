package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.ImPrivateAppService;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.model.io.im.ImPrivateMessageQueryResponse;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.transformer.http.ImMessageHttpIoTransformer;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Api("用户私聊接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/im/private")
public class ImPrivateController {
    private final ImPrivateAppService imPrivateAppService;

    @GetMapping("/queryHistoryMessage")
    public ImPrivateMessageQueryResponse queryHistoryMessage(@RequestParam("chatId") Long chatId,
                                                             @RequestParam("lastMessageId") Long lastMessageId,
                                                             @RequestParam("count") Integer count) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageHistoryQuery query = new ImPrivateMessageHistoryQuery(chatId, userId, lastMessageId, count);
        List<ImPrivateMessageDTO> messageList = imPrivateAppService.queryHistoryMessage(query);
        List<ImPrivateMessageQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageItemListFrom(messageList);
        return new ImPrivateMessageQueryResponse(messageResponseList);
    }

}
