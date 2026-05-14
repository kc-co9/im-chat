package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.model.cqrs.query.group.GroupMessageDetailQuery;
import com.co.kc.imchat.model.cqrs.query.group.GroupMessageHistoryQuery;
import com.co.kc.imchat.model.io.group.GroupMessageDetailResponse;
import com.co.kc.imchat.model.io.group.GroupMessageHistoryQueryResponse;
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
public class GroupMessageController {
    private final GroupMessageAppService groupMessageAppService;

    @GetMapping("/queryMessageDetail")
    public GroupMessageDetailResponse queryMessageDetail(@RequestParam("chatId") Long chatId,
                                                           @RequestParam("messageToken") String messageToken) {
        Long userId = UserContextUtils.get().getUserId();
        GroupMessageDetailQuery query = new GroupMessageDetailQuery(chatId, userId, messageToken);
        GroupMessageDTO imGroupMessageDTO = groupMessageAppService.queryMessageDetail(query);
        return ImMessageHttpIoTransformer.INSTANCE.groupMessageDetailResponseFrom(imGroupMessageDTO);
    }

    @GetMapping("/queryHistoryMessage")
    public GroupMessageHistoryQueryResponse queryHistoryMessage(@RequestParam("chatId") Long chatId,
                                                                  @RequestParam(value = "lastMessageId", required = false) Long lastMessageId,
                                                                  @RequestParam("count") Integer count) {
        Long userId = UserContextUtils.get().getUserId();
        GroupMessageHistoryQuery query = new GroupMessageHistoryQuery(chatId, userId, lastMessageId, count);
        List<GroupMessageDTO> messageList = groupMessageAppService.queryHistoryMessage(query);
        List<GroupMessageHistoryQueryResponse.MessageItem> messageResponseList =
                ImMessageHttpIoTransformer.INSTANCE.groupMessageItemListFrom(messageList);
        return new GroupMessageHistoryQueryResponse(messageResponseList);
    }

}
