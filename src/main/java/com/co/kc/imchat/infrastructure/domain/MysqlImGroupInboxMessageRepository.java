package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupInboxMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
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
                .eq(DbImGroupInboxMessage::getChatId, chatId.getValue())
                .eq(DbImGroupInboxMessage::getUserId, userId.getValue())
                .eq(DbImGroupInboxMessage::getToken, token.getValue()));
    }

    @Override
    public Optional<ImGroupInboxMessage> find(ImChatId chatId, UserId userId, ImMessageId messageId) {
        return dbImGroupInboxMessageService
                .getByChatUserMessageId(chatId.getValue(), userId.getValue(), messageId.getValue())
                .map(ImMessageDomainTransformer.INSTANCE::imGroupInboxMessageFrom);
    }

    @Override
    public List<ImGroupInboxMessage> findByGroupIdAndMessageId(GroupId groupId, ImMessageId messageId) {
        List<DbImGroupInboxMessage> rows =
                dbImGroupInboxMessageService.listByGroupIdAndMessageId(groupId.getValue(), messageId.getValue());
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(rows);
    }

    @Override
    public List<ImGroupInboxMessage> findUnreadMessages(ImChatId chatId, UserId userId) {
        List<DbImGroupInboxMessage> rows = dbImGroupInboxMessageService.list(
                dbImGroupInboxMessageService.getQueryWrapper()
                        .eq(DbImGroupInboxMessage::getChatId, chatId.getValue())
                        .eq(DbImGroupInboxMessage::getUserId, userId.getValue())
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
                        .eq(DbImGroupInboxMessage::getChatId, chatId.getValue())
                        .eq(DbImGroupInboxMessage::getUserId, userId.getValue())
                        .lt(lastMessageId != null, DbImGroupInboxMessage::getMessageId,
                                FunctionUtils.mappingOrNull(lastMessageId, ImMessageId::getValue))
                        .orderByDesc(DbImGroupInboxMessage::getMessageId));
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(page.getRecords());
    }

    @Override
    public ImGroupInboxMessage queryDetail(ImChatId chatId, UserId userId, ImMessageToken token) {
        return dbImGroupInboxMessageService
                .getByChatUserToken(chatId.getValue(), userId.getValue(), token.getValue())
                .map(ImMessageDomainTransformer.INSTANCE::imGroupInboxMessageFrom)
                .orElse(null);
    }

    @Override
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId userId) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        List<Long> chatIdValues = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImGroupChat> dbImGroupChatList = dbImGroupChatService.getListByUserIdAndChatIds(userId.getValue(), chatIdValues);
        if (CollectionUtils.isEmpty(dbImGroupChatList)) {
            return Collections.emptyList();
        }
        List<Long> lastMessageIds = FunctionUtils.mappingNonNullList(dbImGroupChatList, DbImGroupChat::getLastMessageId);
        if (CollectionUtils.isEmpty(lastMessageIds)) {
            return Collections.emptyList();
        }
        List<DbImGroupInboxMessage> rows = dbImGroupInboxMessageService.list(
                dbImGroupInboxMessageService.getQueryWrapper()
                        .eq(DbImGroupInboxMessage::getUserId, userId.getValue())
                        .in(DbImGroupInboxMessage::getMessageId, lastMessageIds));
        return ImMessageDomainTransformer.INSTANCE.imGroupInboxMessageListFrom(rows)
                .stream()
                .map(ImMessage.class::cast)
                .collect(Collectors.toList());
    }
}
