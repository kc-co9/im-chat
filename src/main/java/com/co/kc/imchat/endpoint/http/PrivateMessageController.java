package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.model.io.im.ImPrivateMessageHistoryQueryResponse;
import com.co.kc.imchat.model.io.im.ImPrivateMessageDetailQueryResponse;
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
public class PrivateMessageController {
    private final PrivateMessageAppService privateMessageAppService;

    @GetMapping("/queryMessageDetail")
    public ImPrivateMessageDetailQueryResponse queryMessageDetail(@RequestParam("chatId") Long chatId,
                                                                  @RequestParam("messageToken") String messageToken) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageDetailQuery query = new ImPrivateMessageDetailQuery(chatId, userId, messageToken);
        ImPrivateMessageDTO imPrivateMessageDTO = privateMessageAppService.queryMessageDetail(query);
        return ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageDetailQueryResponseFrom(imPrivateMessageDTO);
    }

    @GetMapping("/queryHistoryMessage")
    public ImPrivateMessageHistoryQueryResponse queryHistoryMessage(@RequestParam("chatId") Long chatId,
                                                                    @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
                                                                    @RequestParam("count") Integer count) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageHistoryQuery query = new ImPrivateMessageHistoryQuery(chatId, userId, lastMessageId, count);
        List<ImPrivateMessageDTO> messageList = privateMessageAppService.queryHistoryMessage(query);
        List<ImPrivateMessageHistoryQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageItemListFrom(messageList);
        return new ImPrivateMessageHistoryQueryResponse(messageResponseList);
    }

}
