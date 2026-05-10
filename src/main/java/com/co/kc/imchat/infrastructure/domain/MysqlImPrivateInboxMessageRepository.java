package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImMessageType;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbPrivateImMessageStatus;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateInboxMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImPrivateInboxMessageRepository implements ImPrivateInboxMessageRepository {
    private final DbImPrivateInboxMessageService dbImPrivateInboxMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void saveBatch(List<ImPrivateInboxMessage> messages) {
        for (ImPrivateInboxMessage message : messages) {
            this.save(message);
        }
    }

    @Override
    public void save(ImPrivateInboxMessage message) {
        Long userId = message.getUserId().getValue();
        Long chatId = message.getChatId().getValue();
        Long messageId = message.getId().getValue();
        DbImMessageType messageType =
                ImMessageDbTransformer.INSTANCE.dbImMessageTypeFrom(message.getContent().getType());
        DbPrivateImMessageStatus messageStatus =
                ImMessageDbTransformer.INSTANCE.dbImMessageStatusFrom(message.getStatus());

        DbImPrivateInboxMessage dbMessage =
                dbImPrivateInboxMessageService.getByChatIdAndMessageId(chatId, messageId).orElse(null);
        if (dbMessage == null) {
            dbMessage = new DbImPrivateInboxMessage();
            dbMessage.setMessageId(messageId);
            dbMessage.setChatId(chatId);
            dbMessage.setUserId(userId);
            dbMessage.setToken(message.getToken().getValue());
            dbMessage.setSenderId(message.getSenderId().getValue());
            dbMessage.setType(messageType);
            dbMessage.setContent(message.getContent().getValue());
            dbMessage.setStatus(messageStatus);
            dbMessage.setSendTime(message.getSendTime());
            dbMessage.setReceiveTime(message.getReceivedTime());
            dbMessage.setRevokeTime(message.getRevokeTime());
            dbImPrivateInboxMessageService.save(dbMessage);
        } else {
            dbMessage.setStatus(messageStatus);
            dbMessage.setReceiveTime(message.getReceivedTime());
            dbMessage.setRevokeTime(message.getRevokeTime());
            dbMessage.setContent(message.getContent().getValue());
            dbImPrivateInboxMessageService.updateById(dbMessage);
        }
    }

    @Override
    public ImPrivateInboxMessage find(ImChatId chatId, ImMessageId messageId) {
        Optional<DbImPrivateInboxMessage> row = dbImPrivateInboxMessageService.getByChatIdAndMessageId(
                chatId.getValue(), messageId.getValue());
        return row.map(this::toDomain).orElse(null);
    }

    @Override
    public ImPrivateInboxMessage find(ImChatId chatId, ImMessageToken messageToken) {
        Optional<DbImPrivateInboxMessage> row = dbImPrivateInboxMessageService.getByChatIdAndMessageToken(
                chatId.getValue(), messageToken.getValue());
        return row.map(this::toDomain).orElse(null);
    }

    @Override
    public List<ImPrivateInboxMessage> queryHistory(ImChatId chatId, ImMessageId imLastMessageId, int count, UserId userId) {
        IPage<DbImPrivateInboxMessage> page = dbImPrivateInboxMessageService.page(
                new Page<>(1, count),
                dbImPrivateInboxMessageService.getQueryWrapper()
                        .eq(DbImPrivateInboxMessage::getChatId, chatId.getValue())
                        .eq(DbImPrivateInboxMessage::getUserId, userId.getValue())
                        .lt(imLastMessageId != null, DbImPrivateInboxMessage::getMessageId,
                                FunctionUtils.mappingOrNull(imLastMessageId, ImMessageId::getValue))
                        .orderByDesc(DbImPrivateInboxMessage::getMessageId));
        return page.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public ImPrivateInboxMessage queryDetail(ImChatId chatId, ImMessageToken messageToken, UserId userId) {
        Optional<DbImPrivateInboxMessage> row = dbImPrivateInboxMessageService.getFirst(
                dbImPrivateInboxMessageService.getQueryWrapper()
                        .eq(DbImPrivateInboxMessage::getChatId, chatId.getValue())
                        .eq(DbImPrivateInboxMessage::getUserId, userId.getValue())
                        .eq(DbImPrivateInboxMessage::getToken, messageToken.getValue()));
        return row.map(this::toDomain).orElse(null);
    }

    @Override
    public boolean contain(ImChatId chatId, ImMessageToken messageToken) {
        return dbImPrivateInboxMessageService.isExist(
                dbImPrivateInboxMessageService.getQueryWrapper()
                        .eq(DbImPrivateInboxMessage::getChatId, chatId.getValue())
                        .eq(DbImPrivateInboxMessage::getToken, messageToken.getValue()));
    }

    private ImPrivateInboxMessage toDomain(DbImPrivateInboxMessage db) {
        return ImMessageDomainTransformer.INSTANCE.imPrivateInboxMessageFrom(db);
    }
}
