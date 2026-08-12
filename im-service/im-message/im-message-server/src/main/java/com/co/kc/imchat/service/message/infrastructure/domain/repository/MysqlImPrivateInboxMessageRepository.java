package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImPrivateInboxMessageService;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.service.message.transformer.domain.ImMessageDomainTransformer;
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
        if (message.getPkId() == null) {
            DbImPrivateInboxMessage dbMessage = ImMessageDbTransformer.INSTANCE.dbImPrivateInboxMessageFrom(message);
            dbImPrivateInboxMessageService.save(dbMessage);
        } else {
            dbImPrivateInboxMessageService.update(dbImPrivateInboxMessageService.getUpdateWrapper()
                    .set(DbImPrivateInboxMessage::getStatus, ImMessageDbTransformer.INSTANCE.dbImMessageStatusFrom(message.getStatus()))
                    .set(DbImPrivateInboxMessage::getReceiveTime, message.getReceivedTime())
                    .set(DbImPrivateInboxMessage::getReadTime, message.getReadTime())
                    .set(DbImPrivateInboxMessage::getRevokeTime, message.getRevokeTime())
                    .set(DbImPrivateInboxMessage::getContent, message.getContent().value())
                    .set(DbImPrivateInboxMessage::getType, ImMessageDbTransformer.INSTANCE.dbImMessageTypeFrom(message.getContent().type()))
                    .eq(DbImPrivateInboxMessage::getId, message.getPkId())
            );
        }
    }

    @Override
    public Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageId messageId) {
        return dbImPrivateInboxMessageService.getByChatIdAndMessageId(
                        chatId.value(), messageId.value())
                .map(ImMessageDomainTransformer.INSTANCE::imPrivateInboxMessageFrom);
    }

    @Override
    public Optional<ImPrivateInboxMessage> find(ImChatId chatId, ImMessageToken messageToken) {
        return dbImPrivateInboxMessageService.getByChatIdAndMessageToken(
                        chatId.value(), messageToken.value())
                .map(ImMessageDomainTransformer.INSTANCE::imPrivateInboxMessageFrom);
    }

    @Override
    public List<ImPrivateInboxMessage> queryHistory(ImChatId chatId, ImMessageId imLastMessageId, int count, UserId userId) {
        IPage<DbImPrivateInboxMessage> page = dbImPrivateInboxMessageService.page(
                new Page<>(1, count),
                dbImPrivateInboxMessageService.getQueryWrapper()
                        .eq(DbImPrivateInboxMessage::getChatId, chatId.value())
                        .eq(DbImPrivateInboxMessage::getUserId, userId.value())
                        .lt(imLastMessageId != null, DbImPrivateInboxMessage::getMessageId,
                                FunctionUtils.mappingOrNull(imLastMessageId, ImMessageId::value))
                        .orderByDesc(DbImPrivateInboxMessage::getMessageId));
        return page.getRecords().stream().map(ImMessageDomainTransformer.INSTANCE::imPrivateInboxMessageFrom).collect(Collectors.toList());
    }

    @Override
    public Optional<ImPrivateInboxMessage> queryDetail(ImChatId chatId, ImMessageToken messageToken, UserId userId) {
        return dbImPrivateInboxMessageService.getFirst(
                        dbImPrivateInboxMessageService.getQueryWrapper()
                                .eq(DbImPrivateInboxMessage::getChatId, chatId.value())
                                .eq(DbImPrivateInboxMessage::getUserId, userId.value())
                                .eq(DbImPrivateInboxMessage::getToken, messageToken.value()))
                .map(ImMessageDomainTransformer.INSTANCE::imPrivateInboxMessageFrom);
    }

    @Override
    public boolean contain(ImChatId chatId, ImMessageToken messageToken) {
        return dbImPrivateInboxMessageService.isExist(
                dbImPrivateInboxMessageService.getQueryWrapper()
                        .select(DbImPrivateInboxMessage::getId)
                        .eq(DbImPrivateInboxMessage::getChatId, chatId.value())
                        .eq(DbImPrivateInboxMessage::getToken, messageToken.value()));
    }
}
