package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMessageService;
import com.co.kc.imchat.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupMessageRepository implements ImGroupMessageRepository {
    private final DbImGroupMessageService dbImGroupMessageService;

    @Override
    public void save(ImGroupMessage imMessage) {
        DbImGroupMessage dbMessage = ImMessageDbTransformer.INSTANCE.dbImGroupMessageFrom(imMessage);
        dbImGroupMessageService.saveOrUpdate(dbMessage);
    }

    @Override
    public ImGroupMessage find(ImChatId chatId, ImMessageId messageId) {
        Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getByChatIdAndMessageId(chatId.getValue(), messageId.getValue());
        return dbImGroupMessage.map(ImMessageDomainTransformer.INSTANCE::imGroupMessageFrom).orElse(null);
    }
}
