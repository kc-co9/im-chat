package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.domain.message.ImPrivateMessageRepository;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateMessageService;
import com.co.kc.imchat.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImPrivateMessageRepository implements ImPrivateMessageRepository {
    private final DbImPrivateMessageService dbImPrivateMessageService;

    @Override
    public void save(ImPrivateMessage message) {
        DbImPrivateMessage dbMessage = ImMessageDbTransformer.INSTANCE.dbImPrivateMessageFrom(message);
        dbImPrivateMessageService.saveOrUpdate(dbMessage);
    }

    @Override
    public ImPrivateMessage find(ImChatId chatId, ImMessageId messageId) {
        Optional<DbImPrivateMessage> dbImPrivateMessage =
                dbImPrivateMessageService.getByChatIdAndMessageId(chatId.getValue(), messageId.getValue());
        return dbImPrivateMessage.map(ImMessageDomainTransformer.INSTANCE::imPrivateMessageFrom).orElse(null);
    }

    @Override
    public List<ImPrivateMessage> queryHistory(ImChatId imChatId, ImMessageId lastMessageId, int count) {
        IPage<DbImPrivateMessage> dbImPrivateMessagePage = dbImPrivateMessageService.page(new Page<>(1, count), dbImPrivateMessageService.getQueryWrapper()
                .eq(DbImPrivateMessage::getChatId, imChatId.getValue())
                .lt(DbImPrivateMessage::getMessageId, lastMessageId.getValue())
                .orderByDesc(DbImPrivateMessage::getMessageId));
        return ImMessageDomainTransformer.INSTANCE.imPrivateMessageListFrom(dbImPrivateMessagePage.getRecords());
    }
}
