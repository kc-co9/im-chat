package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupChatRepository implements ImGroupChatRepository {
    private final DbImGroupChatService dbImGroupChatService;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    @Override
    public ImGroupChat find(ImChatId chatId) {
        return dbImGroupChatService.getByChatId(chatId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupChatFrom)
                .orElse(null);
    }

    @Override
    public ImGroupChat find(ImGroupId groupId, UserId userId) {
        return dbImGroupChatService.getByGroupIdAndUserId(groupId.getValue(), userId.getValue())
                .map(ImChatDomainTransformer.INSTANCE::imGroupChatFrom)
                .orElse(null);
    }

    @Override
    public List<ImGroupChat> findByGroupId(ImGroupId groupId) {
        List<DbImGroupChat> rows = dbImGroupChatService.getByGroupId(groupId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> findByUserId(UserId userId) {
        List<DbImGroupChat> rows = dbImGroupChatService.getListByUserId(userId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> findByUserIdAndChatIds(UserId userId, List<ImChatId> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        List<Long> chatIdValues = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImGroupChat> rows = dbImGroupChatService.getListByUserIdAndChatIds(userId.getValue(), chatIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> findByUserIdsAndGroupId(ImGroupId groupId, List<UserId> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        List<Long> userIdValues = FunctionUtils.mappingList(userIds, UserId::getValue);
        List<DbImGroupChat> rows = dbImGroupChatService.getListByGroupIdAndUserIds(groupId.getValue(), userIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
        return imGroupInboxMessageRepository.findLastMessageList(chatIds, viewer);
    }

    @Override
    public void save(ImGroupChat groupChat) {
        DbImGroupChat row = ImChatDbTransformer.INSTANCE.dbImGroupChatFrom(groupChat);
        dbImGroupChatService.saveOrUpdate(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void saveAll(List<ImGroupChat> groupChats) {
        List<DbImGroupChat> rows = ImChatDbTransformer.INSTANCE.dbImGroupChatListFrom(groupChats);
        dbImGroupChatService.saveOrUpdateBatch(rows);
    }

    @Override
    public boolean contain(ImChatId chatId, UserId userId) {
        return dbImGroupChatService.getFirst(dbImGroupChatService.getQueryWrapper()
                .eq(DbImGroupChat::getChatId, chatId.getValue())
                .eq(DbImGroupChat::getUserId, userId.getValue())).isPresent();
    }
}
