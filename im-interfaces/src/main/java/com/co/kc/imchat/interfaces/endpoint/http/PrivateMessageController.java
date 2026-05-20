package com.co.kc.imchat.interfaces.endpoint.http;

import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImPrivateMessageDTO;
import com.co.kc.imchat.application.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.co.kc.imchat.application.model.cqrs.query.ImPrivateMessageDetailQuery;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageHistoryQueryResponse;
import com.co.kc.imchat.interfaces.model.io.im.ImPrivateMessageDetailQueryResponse;
import com.co.kc.imchat.interfaces.support.context.UserContextUtils;
import com.co.kc.imchat.interfaces.transformer.ImMessageHttpIoTransformer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "用户私聊接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/im/private")
public class PrivateMessageController {
    private final PrivateMessageAppService privateMessageAppService;

    @GetMapping(value = "/queryMessageDetail")
    public ImPrivateMessageDetailQueryResponse queryMessageDetail(@RequestParam(name = "chatId") Long chatId,
                                                                  @RequestParam(name = "messageToken") String messageToken) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageDetailQuery query = new ImPrivateMessageDetailQuery(chatId, userId, messageToken);
        ImPrivateMessageDTO imPrivateMessageDTO = privateMessageAppService.queryMessageDetail(query);
        return ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageDetailQueryResponseFrom(imPrivateMessageDTO);
    }

    @GetMapping(value = "/queryHistoryMessage")
    public ImPrivateMessageHistoryQueryResponse queryHistoryMessage(@RequestParam(name = "chatId") Long chatId,
                                                                    @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
                                                                    @RequestParam(name = "count") Integer count) {
        Long userId = UserContextUtils.get().getUserId();
        ImPrivateMessageHistoryQuery query = new ImPrivateMessageHistoryQuery(chatId, userId, lastMessageId, count);
        List<ImPrivateMessageDTO> messageList = privateMessageAppService.queryHistoryMessage(query);
        List<ImPrivateMessageHistoryQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageItemListFrom(messageList);
        return new ImPrivateMessageHistoryQueryResponse(messageResponseList);
    }

}
