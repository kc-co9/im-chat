package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.ImGroupAppService;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImGroupMessageDetailQuery;
import com.co.kc.imchat.model.cqrs.query.ImGroupMessageHistoryQuery;
import com.co.kc.imchat.model.io.im.ImGroupMessageDetailResponse;
import com.co.kc.imchat.model.io.im.ImGroupMessageHistoryQueryResponse;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.transformer.http.ImMessageHttpIoTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/im/group")
public class ImGroupController {
    private final ImGroupAppService imGroupAppService;

    @GetMapping("/queryMessageDetail")
    public ImGroupMessageDetailResponse queryMessageDetail(@RequestParam("chatId") Long chatId,
                                                           @RequestParam("messageToken") String messageToken) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupMessageDetailQuery query = new ImGroupMessageDetailQuery(chatId, userId, messageToken);
        ImGroupMessageDTO imGroupMessageDTO = imGroupAppService.queryMessageDetail(query);
        return ImMessageHttpIoTransformer.INSTANCE.imGroupMessageDetailResponseFrom(imGroupMessageDTO);
    }

    @GetMapping("/queryHistoryMessage")
    public ImGroupMessageHistoryQueryResponse queryHistoryMessage(@RequestParam("chatId") Long chatId,
                                                                  @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
                                                                  @RequestParam("count") Integer count) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupMessageHistoryQuery query = new ImGroupMessageHistoryQuery(chatId, userId, lastMessageId, count);
        List<ImGroupMessageDTO> messageList = imGroupAppService.queryHistoryMessage(query);
        List<ImGroupMessageHistoryQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.imGroupMessageItemListFrom(messageList);
        return new ImGroupMessageHistoryQueryResponse(messageResponseList);
    }

}
