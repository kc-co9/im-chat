package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.service.message.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupChatRepository implements ImGroupChatRepository {
    private final DbImGroupChatService dbImGroupChatService;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    @Override
    public Optional<ImGroupChat> find(ImChatId chatId) {
        return dbImGroupChatService.getByChatId(chatId.value())
                .map(ImChatDomainTransformer.INSTANCE::imGroupChatFrom);
    }

    @Override
    public Optional<ImGroupChat> find(GroupId groupId, UserId userId) {
        return dbImGroupChatService.getByGroupIdAndUserId(groupId.value(), userId.value())
                .map(ImChatDomainTransformer.INSTANCE::imGroupChatFrom);
    }

    @Override
    public List<ImGroupChat> find(GroupId groupId) {
        List<DbImGroupChat> rows = dbImGroupChatService.getByGroupId(groupId.value());
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> find(Collection<GroupId> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, GroupId::value);
        List<DbImGroupChat> rows = dbImGroupChatService.getByGroupIds(groupIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> find(UserId userId) {
        List<DbImGroupChat> rows = dbImGroupChatService.getListByUserId(userId.value());
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> find(UserId userId, Collection<GroupId> groupIds) {
        if (userId == null || CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> groupIdValues = FunctionUtils.mappingList(groupIds, GroupId::value);
        List<DbImGroupChat> rows = dbImGroupChatService.getListByUserIdAndGroupIds(userId.value(), groupIdValues);
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(rows);
    }

    @Override
    public List<ImGroupChat> find(GroupId groupId, List<UserId> memberIds) {
        if (CollectionUtils.isEmpty(memberIds)) {
            return Collections.emptyList();
        }
        List<Long> userIdValues = FunctionUtils.mappingList(memberIds, UserId::value);
        List<DbImGroupChat> rows = dbImGroupChatService.getListByGroupIdAndUserIds(groupId.value(), userIdValues);
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
    public void save(List<ImGroupChat> groupChats) {
        List<DbImGroupChat> rows = ImChatDbTransformer.INSTANCE.dbImGroupChatListFrom(groupChats);
        dbImGroupChatService.saveOrUpdateBatch(rows);
    }

    @Override
    public boolean contain(ImChatId chatId, UserId userId) {
        return dbImGroupChatService.isExist(dbImGroupChatService.getQueryWrapper()
                .select(DbImGroupChat::getId)
                .eq(DbImGroupChat::getChatId, chatId.value())
                .eq(DbImGroupChat::getUserId, userId.value()));
    }

    @Override
    public void remove(GroupId groupId, UserId userId) {
        dbImGroupChatService.removeByGroupIdAndUserId(groupId.value(), userId.value());
    }
}
