package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImPrivateChatService;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImPrivateInboxMessageService;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.message.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.service.message.transformer.domain.ImChatDomainTransformer;
import com.co.kc.imchat.service.message.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImPrivateChatRepository implements ImPrivateChatRepository {
    private final DbImPrivateChatService dbImPrivateChatService;
    private final DbImPrivateInboxMessageService dbImPrivateInboxMessageService;

    @Override
    public List<ImPrivateChat> find(UserId userId) {
        List<DbImPrivateChat> dbImPrivateChatList = dbImPrivateChatService.getListByUserId(userId.value());
        return ImChatDomainTransformer.INSTANCE.imPrivateChatListFrom(dbImPrivateChatList);
    }

    @Override
    public Optional<ImPrivateChat> find(ImChatId chatId) {
        return dbImPrivateChatService.getByChatId(chatId.value())
                .map(ImChatDomainTransformer.INSTANCE::imPrivateChatFrom);
    }

    @Override
    public Optional<ImPrivateChat> find(UserId userId, UserId peerUserId) {
        return dbImPrivateChatService
                .getByUserIdAndPeerUserId(userId.value(), peerUserId.value())
                .map(ImChatDomainTransformer.INSTANCE::imPrivateChatFrom);
    }

    @Override
    public boolean contain(UserId userId, UserId peerUserId) {
        return dbImPrivateChatService.contain(userId.value(), peerUserId.value());
    }

    @Override
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
        if (CollectionUtils.isEmpty(chatIds) || viewer == null) {
            return Collections.emptyList();
        }
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::value);
        List<DbImPrivateChat> privateChats =
                dbImPrivateChatService.listByChatIdsAndUserId(chatIdValueList, viewer.value());
        return privateChats.stream()
                .filter(c -> c.getLastMessageId() != null && c.getLastMessageId() > 0)
                .map(c -> dbImPrivateInboxMessageService
                        .getByChatIdAndMessageId(c.getChatId(), c.getLastMessageId())
                        .map(ImMessageDomainTransformer.INSTANCE::imPrivateInboxMessageFrom)
                        .orElse(null))
                .filter(Objects::nonNull)
                .map(m -> (ImMessage) m)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImPrivateChat imPrivateChat) {
        DbImPrivateChat dbImPrivateChat = ImChatDbTransformer.INSTANCE.dbImPrivateChatFrom(imPrivateChat);
        boolean persisted;
        if (dbImPrivateChat.getId() == null) {
            persisted = dbImPrivateChatService.saveOrUpdate(dbImPrivateChat);
        } else {
            persisted = dbImPrivateChatService.update(dbImPrivateChat,
                    dbImPrivateChatService.getUpdateWrapper()
                            .eq(DbImPrivateChat::getId, dbImPrivateChat.getId())
                            .eq(DbImPrivateChat::getUserId, dbImPrivateChat.getUserId())
                            .eq(DbImPrivateChat::getVersion, imPrivateChat.getRowVersion()));
        }
        if (!persisted) {
            if (imPrivateChat.getPkId() != null) {
                throw new OptimisticLockingFailureException(
                        "Private chat was modified concurrently: " + imPrivateChat.getId().value());
            }
            throw new DataAccessResourceFailureException(
                    "Private chat was not inserted: " + imPrivateChat.getId().value());
        }
        if (persisted) {
            if (dbImPrivateChat.getId() != null) {
                imPrivateChat.setPkId(dbImPrivateChat.getId());
            }
            imPrivateChat.setRowVersion(dbImPrivateChat.getVersion());
        }
    }

    @Override
    public void remove(UserId userId, UserId peerUserId) {
        dbImPrivateChatService.removeByUserIdAndPeerUserId(userId.value(), peerUserId.value());
    }

}
