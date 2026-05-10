package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateInboxMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImPrivateChatRepository implements ImPrivateChatRepository {
    private final DbImPrivateChatService dbImPrivateChatService;
    private final DbImPrivateInboxMessageService dbImPrivateInboxMessageService;

    @Override
    public List<ImPrivateChat> find(UserId userId) {
        List<DbImPrivateChat> dbImPrivateChatList = dbImPrivateChatService.getListByUserId(userId.getValue());
        return ImChatDomainTransformer.INSTANCE.imPrivateChatListFrom(dbImPrivateChatList);
    }

    @Override
    public ImPrivateChat find(ImChatId chatId) {
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImPrivateChat);
    }

    @Override
    public ImPrivateChat find(UserId userId, UserId peerUserId) {
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService
                .getByUserIdAndPeerUserId(userId.getValue(), peerUserId.getValue())
                .orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImPrivateChat);
    }

    @Override
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
        if (CollectionUtils.isEmpty(chatIds) || viewer == null) {
            return Collections.emptyList();
        }
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImPrivateChat> privateChats =
                dbImPrivateChatService.listByChatIdsAndUserId(chatIdValueList, viewer.getValue());
        return privateChats.stream()
                .filter(c -> c.getLastMessageId() != null && c.getLastMessageId() > 0)
                .map(c -> dbImPrivateInboxMessageService
                        .getByChatIdAndMessageId(c.getChatId(), c.getLastMessageId())
                        .map(this::privateInboxRowToDomain)
                        .orElse(null))
                .filter(Objects::nonNull)
                .map(m -> (ImMessage) m)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImPrivateChat imPrivateChat) {
        DbImPrivateChat dbImPrivateChat = ImChatDbTransformer.INSTANCE.dbImPrivateChatFrom(imPrivateChat);
        // save() 仅为 INSERT；已持久化行需带主键并走 saveOrUpdate / updateById
        dbImPrivateChatService.saveOrUpdate(dbImPrivateChat);
    }

    /** 发送方会话内副本行 user_id == sender_id，需从会话解析真实接收方。 */
    private ImPrivateInboxMessage privateInboxRowToDomain(DbImPrivateInboxMessage row) {
        return ImMessageDomainTransformer.INSTANCE.imPrivateInboxMessageFrom(row);
    }
}
