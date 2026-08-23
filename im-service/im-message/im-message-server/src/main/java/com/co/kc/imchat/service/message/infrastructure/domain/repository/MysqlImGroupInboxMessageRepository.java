package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImGroupInboxMessageService;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.service.message.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupInboxMessageRepository implements ImGroupInboxMessageRepository {
    private final DbImGroupChatService dbImGroupChatService;
    private final DbImGroupInboxMessageService dbImGroupInboxMessageService;

    @Override
    public void save(ImGroupInboxMessage message) {
        dbImGroupInboxMessageService.saveOrUpdate(ImMessageDbTransformer.INSTANCE.dbImGroupInboxMessageFrom(message));
    }

    @Override
    public void save(List<ImGroupInboxMessage> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        List<DbImGroupInboxMessage> rows = messages.stream()
                .map(ImMessageDbTransformer.INSTANCE::dbImGroupInboxMessageFrom)
                .collect(Collectors.toList());
        dbImGroupInboxMessageService.saveOrUpdateBatch(rows);
    }

    @Override
    public boolean contain(ImChatId chatId, UserId userId, ImMessageToken token) {
        return dbImGroupInboxMessageService.isExist(dbImGroupInboxMessageService.getQueryWrapper()
                .select(DbImGroupInboxMessage::getId)
                .eq(DbImGroupInboxMessage::getChatId, chatId.value())
                .eq(DbImGroupInboxMessage::getUserId, userId.value())
                .eq(DbImGroupInboxMessage::getToken, token.value()));
    }

    @Override
    public Optional<ImGroupInboxMessage> find(ImChatId chatId, UserId userId, ImMessageId messageId) {
        return dbImGroupInboxMessageService
                .getByChatUserMessageId(chatId.value(), userId.value(), messageId.value())
                .map(ImMessageDomainTransformer.INSTANCE::imGroupInboxMessageFrom);
    }

    @Override
    public List<ImGroupInboxMessage> findByGroupIdAndMessageId(GroupId groupId, ImMessageId messageId) {
        List<DbImGroupInboxMessage> rows =
                dbImGroupInboxMessageService.listByGroupIdAndMessageId(groupId.value(), messageId.value());
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(rows);
    }

    @Override
    public List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId) {
        List<DbImGroupInboxMessage> rows = dbImGroupInboxMessageService.list(
                dbImGroupInboxMessageService.getQueryWrapper()
                        .eq(DbImGroupInboxMessage::getChatId, chatId.value())
                        .eq(DbImGroupInboxMessage::getUserId, userId.value())
                        .in(DbImGroupInboxMessage::getStatus,
                                Arrays.asList(
                                        ImMessageDbTransformer.INSTANCE.dbImMessageStatusFrom(ImGroupMessageStatus.SENT),
                                        ImMessageDbTransformer.INSTANCE.dbImMessageStatusFrom(ImGroupMessageStatus.RECEIVED))));
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(rows);
    }

    @Override
    public List<ImGroupInboxMessage> queryHistory(ImChatId chatId, UserId userId, ImMessageId lastMessageId, Integer count) {
        IPage<DbImGroupInboxMessage> page = dbImGroupInboxMessageService.page(new Page<>(1, count),
                dbImGroupInboxMessageService.getQueryWrapper()
                        .eq(DbImGroupInboxMessage::getChatId, chatId.value())
                        .eq(DbImGroupInboxMessage::getUserId, userId.value())
                        .lt(lastMessageId != null, DbImGroupInboxMessage::getMessageId,
                                FunctionUtils.mappingOrNull(lastMessageId, ImMessageId::value))
                        .orderByDesc(DbImGroupInboxMessage::getMessageId));
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(page.getRecords());
    }

    @Override
    public ImGroupInboxMessage queryDetail(ImChatId chatId, UserId userId, ImMessageToken token) {
        return dbImGroupInboxMessageService
                .getByChatUserToken(chatId.value(), userId.value(), token.value())
                .map(ImMessageDomainTransformer.INSTANCE::imGroupInboxMessageFrom)
                .orElse(null);
    }

    @Override
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        List<Long> chatIdValues = FunctionUtils.mappingList(chatIds, ImChatId::value);
        List<DbImGroupChat> dbImGroupChatList = dbImGroupChatService.getListByUserIdAndChatIds(userId.value(), chatIdValues);
        if (CollectionUtils.isEmpty(dbImGroupChatList)) {
            return Collections.emptyList();
        }
        List<Long> lastMessageIds = FunctionUtils.mappingNonNullList(dbImGroupChatList, DbImGroupChat::getLastMessageId);
        if (CollectionUtils.isEmpty(lastMessageIds)) {
            return Collections.emptyList();
        }
        List<DbImGroupInboxMessage> rows = dbImGroupInboxMessageService.list(
                dbImGroupInboxMessageService.getQueryWrapper()
                        .eq(DbImGroupInboxMessage::getUserId, userId.value())
                        .in(DbImGroupInboxMessage::getMessageId, lastMessageIds));
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(rows)
                .stream()
                .map(ImMessage.class::cast)
                .collect(Collectors.toList());
    }
}
