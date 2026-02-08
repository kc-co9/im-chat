package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImPrivateMessage;
import com.co.kc.imchat.domain.message.ImPrivateMessageRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
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
    public List<ImPrivateMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, int count) {
        IPage<DbImPrivateMessage> dbImPrivateMessagePage = dbImPrivateMessageService.page(new Page<>(1, count), dbImPrivateMessageService.getQueryWrapper()
                .eq(DbImPrivateMessage::getChatId, imChatId.getValue())
                .lt(imLastMessageId != null, DbImPrivateMessage::getMessageId, FunctionUtils.mappingOrNull(imLastMessageId, ImMessageId::getValue))
                .orderByDesc(DbImPrivateMessage::getMessageId));
        return ImMessageDomainTransformer.INSTANCE.imPrivateMessageListFrom(dbImPrivateMessagePage.getRecords());
    }

    @Override
    public ImPrivateMessage queryDetail(ImChatId chatId, ImMessageToken messageToken) {
        Optional<DbImPrivateMessage> dbImPrivateMessage = dbImPrivateMessageService.getFirst(dbImPrivateMessageService.getQueryWrapper()
                .eq(DbImPrivateMessage::getChatId, chatId.getValue())
                .eq(DbImPrivateMessage::getToken, messageToken.getValue()));
        return dbImPrivateMessage.map(ImMessageDomainTransformer.INSTANCE::imPrivateMessageFrom).orElse(null);
    }
}
