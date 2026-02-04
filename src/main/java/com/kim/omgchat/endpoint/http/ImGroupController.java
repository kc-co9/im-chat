package com.kim.omgchat.endpoint.http;

import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.model.io.im.ImGroupMessageQueryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/im/group")
public class ImGroupController {

    @GetMapping("/queryHistoryMessage")
    public Result<ImGroupMessageQueryResponse> queryHistoryMessage(@RequestParam("chatId") Long chatId,
                                                                   @RequestParam("lastMessageId") Long lastMessageId,
                                                                   @RequestParam("count") Integer count) {
        return Result.success(new ImGroupMessageQueryResponse());
    }

}
