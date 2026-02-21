package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChatLastMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImChatLastMessageService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMessageService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivatePair;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateChatService;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImChatRepository implements ImChatRepository {
    private final DbImChatLastMessageService dbImChatLastMessageService;

    private final DbImGroupMessageService dbImGroupMessageService;
    private final DbImPrivateMessageService dbImPrivateMessageService;

    private final DbImPrivateChatService dbImPrivateChatService;
    private final DbImGroupChatService dbImGroupChatService;
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public List<ImMessage> findLastMessageList(ImChatType chatType, List<ImChatId> chatIds) {
        if (Objects.isNull(chatType) || CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        DbImChatType dbImChatType = ImChatDbTransformer.INSTANCE.dbImChatTypeFrom(chatType);
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImChatLastMessage> dbImChatLastMessageList = dbImChatLastMessageService.getListByChatIds(dbImChatType, chatIdValueList);

        // TODO 优化查询
        if (DbImChatType.PRIVATE.equals(dbImChatType)) {
            return dbImChatLastMessageList.stream()
                    .map(dbImChatLastMessage -> {
                        Optional<DbImPrivateMessage> dbImPrivateMessage = dbImPrivateMessageService.getLastMessageByChatId(dbImChatLastMessage.getChatId(), dbImChatLastMessage.getMessageId());
                        return dbImPrivateMessage.map(ImMessageDomainTransformer.INSTANCE::imPrivateMessageFrom).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            return dbImChatLastMessageList.stream()
                    .map(dbImChatLastMessage -> {
                        Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getLastMessageByChatId(dbImChatLastMessage.getChatId(), dbImChatLastMessage.getMessageId());
                        return dbImGroupMessage.map(ImMessageDomainTransformer.INSTANCE::imGroupMessageFrom).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<ImPrivateChat> findPrivateChatList(UserId userId) {
        List<DbImPrivateChat> dbImPrivateChatList = dbImPrivateChatService.getListByUserId(userId.getValue());
        return ImChatDomainTransformer.INSTANCE.imPrivateChatListFrom(dbImPrivateChatList);
    }

    @Override
    public ImPrivateChat findPrivateChat(ImChatId chatId) {
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImPrivateChat);
    }

    @Override
    public ImPrivateChat findPrivateChat(ImPrivatePair pair) {
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService.getByMember1AndMember2(pair.getMember1().getValue(), pair.getMember2().getValue()).orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImPrivateChat);
    }

    @Override
    public List<ImGroupChat> findGroupChatList(UserId userId) {
        List<DbImGroupChat> dbImGroupChatList = dbImGroupChatService.getListByUserId(userId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupChatListFrom(dbImGroupChatList);
    }

    @Override
    public List<ImGroupMember> findGroupMemberList(ImChatId chatId) {
        List<DbImGroupMember> dbImGroupMemberList = dbImGroupMemberService.getByChatId(chatId.getValue());
        return ImChatDomainTransformer.INSTANCE.imGroupMemberListFrom(dbImGroupMemberList);
    }

    @Override
    public List<ImGroupMember> findUserGroupMemberList(UserId userId, List<ImChatId> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImGroupMember> dbImGroupMemberList = dbImGroupMemberService.getListByUserIdAndChatIds(userId, chatIdValueList);
        return ImChatDomainTransformer.INSTANCE.imGroupMemberListFrom(dbImGroupMemberList);
    }

    @Override
    public ImGroupChat findGroupChat(ImChatId chatId) {
        DbImGroupChat dbImGroupChat = dbImGroupChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImGroupChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imGroupChatFrom(dbImGroupChat);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImPrivateChat imPrivateChat) {
        DbImPrivateChat dbImPrivateChat = ImChatDbTransformer.INSTANCE.dbImPrivateChatFrom(imPrivateChat);
        dbImPrivateChatService.save(dbImPrivateChat);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImGroupChat imGroupChat) {
        DbImGroupChat dbImGroupChat = ImChatDbTransformer.INSTANCE.dbImGroupChatFrom(imGroupChat);
        if (dbImGroupChat.getId() == null) {
            dbImGroupChatService.save(dbImGroupChat);
        } else {
            // TODO 更新群组
        }
    }

    @Override
    public void saveGroupMembers(List<ImGroupMember> imGroupMembers) {
        // TODO 判断更新
        List<DbImGroupMember> dbImGroupMemberList = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(imGroupMembers);
        dbImGroupMemberService.saveBatchIgnoreEmpty(dbImGroupMemberList);
    }

    @Override
    public boolean containGroupMember(ImChatId chatId, UserId userId) {
        return dbImGroupMemberService.getFirst(dbImGroupMemberService.getQueryWrapper()
                .eq(DbImGroupMember::getChatId, chatId.getValue())
                .eq(DbImGroupMember::getUserId, userId.getValue())).isPresent();
    }
}
