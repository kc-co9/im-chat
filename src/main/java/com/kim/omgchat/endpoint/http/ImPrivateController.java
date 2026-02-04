package com.kim.omgchat.endpoint.http;

import com.kim.omgchat.application.ImPrivateAppService;
import com.kim.omgchat.model.cqrs.dto.im.ImMessageDTO;
import com.kim.omgchat.model.cqrs.query.ImPrivateMessageHistoryQuery;
import com.kim.omgchat.model.io.im.ImPrivateMessageQueryResponse;
import com.kim.omgchat.support.context.UserContextUtils;
import com.kim.omgchat.transformer.ImMessageHttpIoTransformer;
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
        List<ImMessageDTO> messageList = imPrivateAppService.queryHistoryMessage(query);
        List<ImPrivateMessageQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.imPrivateMessageItemListFrom(messageList);
        return new ImPrivateMessageQueryResponse(messageResponseList);
    }

}
