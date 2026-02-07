package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatLastMessage;
import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChatLastMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImChatLastMessageService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMessageService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateMessageService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.chat.ImChat;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivatePair;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImPrivateChatService;
import com.co.kc.imchat.transformer.db.ImChatDbTransformer;
import com.co.kc.imchat.transformer.domain.ImChatDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImChatRepository implements ImChatRepository {
    private final DbUserService dbUserService;
    private final DbFriendService dbFriendService;

    private final DbImChatService dbImChatService;
    private final DbImChatLastMessageService dbImChatLastMessageService;

    private final DbImGroupMessageService dbImGroupMessageService;
    private final DbImPrivateMessageService dbImPrivateMessageService;

    private final DbImPrivateChatService dbImPrivateChatService;
    private final DbImGroupChatService dbImGroupChatService;
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public ImChat find(ImChatId chatId) {
        DbImChat dbImChat = dbImChatService.getById(chatId.getValue());
        return dbImChat != null ? ImChatDomainTransformer.INSTANCE.imChatFrom(dbImChat) : null;
    }

    @Override
    public ImChatLastMessage findLastMessage(ImChatId chatId) {
        DbImChatLastMessage dbImChatLastMessage = dbImChatLastMessageService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImChatLastMessage == null) {
            return null;
        }
        DbImChat dbImChat = dbImChatService.getByChatId(dbImChatLastMessage.getChatId()).orElse(null);
        if (dbImChat == null) {
            return null;
        }
        if (DbImChatType.PRIVATE.equals(dbImChat.getType())) {
            Optional<DbImPrivateMessage> dbImPrivateMessage = dbImPrivateMessageService.getLastMessageByChatId(dbImChat.getChatId(), dbImChatLastMessage.getMessageId());
            return dbImPrivateMessage.map(ImChatDomainTransformer.INSTANCE::imChatLastMessageFrom).orElse(null);
        } else {
            Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getLastMessageByChatId(dbImChat.getChatId(), dbImChatLastMessage.getMessageId());
            return dbImGroupMessage.map(ImChatDomainTransformer.INSTANCE::imChatLastMessageFrom).orElse(null);
        }
    }

    @Override
    public List<ImChatLastMessage> findLastMessageList(List<ImChatId> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImChatLastMessage> dbImChatLastMessageList = dbImChatLastMessageService.getListByChatIds(chatIdValueList);

        List<DbImChat> dbImChatList = dbImChatService.getListByChatIds(chatIdValueList);
        Map<Long, DbImChatType> chatIdTypeMap = FunctionUtils.mappingMap(dbImChatList, DbImChat::getChatId, DbImChat::getType);

        List<DbImChatLastMessage> dbImPrivateChatLastMessageList = dbImChatLastMessageList.stream()
                .filter(dbImChatLastMessage -> DbImChatType.PRIVATE.equals(chatIdTypeMap.get(dbImChatLastMessage.getChatId())))
                .collect(Collectors.toList());
        List<DbImChatLastMessage> dbImGroupChatLastMessageList = dbImChatLastMessageList.stream()
                .filter(dbImChatLastMessage -> DbImChatType.GROUP.equals(chatIdTypeMap.get(dbImChatLastMessage.getChatId())))
                .collect(Collectors.toList());

        // TODO 优化查询
        List<ImChatLastMessage> imChatLastMessageList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dbImPrivateChatLastMessageList)) {
            List<ImChatLastMessage> imPrivateChatLastMessageList = dbImPrivateChatLastMessageList.stream()
                    .map(dbImChatLastMessage -> {
                        Optional<DbImPrivateMessage> dbImPrivateMessage = dbImPrivateMessageService.getLastMessageByChatId(dbImChatLastMessage.getChatId(), dbImChatLastMessage.getMessageId());
                        return dbImPrivateMessage.map(ImChatDomainTransformer.INSTANCE::imChatLastMessageFrom).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            imChatLastMessageList.addAll(imPrivateChatLastMessageList);
        }
        if (CollectionUtils.isNotEmpty(dbImGroupChatLastMessageList)) {
            List<ImChatLastMessage> imGroupChatLastMessageList = dbImGroupChatLastMessageList.stream()
                    .map(dbImChatLastMessage -> {
                        Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getLastMessageByChatId(dbImChatLastMessage.getChatId(), dbImChatLastMessage.getMessageId());
                        return dbImGroupMessage.map(ImChatDomainTransformer.INSTANCE::imChatLastMessageFrom).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            imChatLastMessageList.addAll(imGroupChatLastMessageList);
        }
        return imChatLastMessageList;
    }

    @Override
    public List<ImChat> find(UserId userId) {
        // 获取当前用户的所有私聊和群聊关系
        List<DbImPrivateChat> dbImPrivateChatList = dbImPrivateChatService.getListByUserId(userId.getValue());
        List<DbImGroupMember> dbImGroupMemberList = dbImGroupMemberService.getListByUserId(userId.getValue());

        // 提取私聊中对方的用户ID
        List<Long> friendUserIds = dbImPrivateChatList.stream()
                .flatMap(chat -> Stream.of(chat.getMember1(), chat.getMember2()))
                .filter(memberId -> !memberId.equals(userId.getValue()))
                .collect(Collectors.toList());

        // 获取所有相关用户信息
        List<DbUser> dbFriendUserList = dbUserService.getListByUserIds(friendUserIds);
        Map<Long, DbUser> idUserMap = FunctionUtils.mappingMap(dbFriendUserList, DbUser::getUserId, Function.identity());

        // 获取好友关系信息
        List<DbFriend> dbFriendList = dbFriendService.getListByUserIdAndFriendUserIds(userId.getValue(), friendUserIds);
        Map<Long, DbFriend> friendIdMap = FunctionUtils.mappingMap(dbFriendList, DbFriend::getFriendUserId, Function.identity());

        // 提取所有聊天ID
        List<Long> chatIds = new ArrayList<>();
        chatIds.addAll(FunctionUtils.mappingList(dbImPrivateChatList, DbImPrivateChat::getChatId));
        chatIds.addAll(FunctionUtils.mappingList(dbImGroupMemberList, DbImGroupMember::getChatId));

        // 获取所有聊天信息
        List<DbImChat> dbImChatList = dbImChatService.getListByChatIds(chatIds);

        // 创建私聊ID到对方用户ID的映射
        Map<Long, Long> chatIdToFriendIdMap = new HashMap<>();
        for (DbImPrivateChat dbImPrivateChat : dbImPrivateChatList) {
            long chatId = dbImPrivateChat.getChatId();
            long friendId = dbImPrivateChat.getMember1().equals(userId.getValue())
                    ? dbImPrivateChat.getMember2()
                    : dbImPrivateChat.getMember1();
            chatIdToFriendIdMap.put(chatId, friendId);
        }

        List<ImChat> imChatList = new ArrayList<>();
        for (DbImChat dbImChat : dbImChatList) {
            ImChat imChat = ImChatDomainTransformer.INSTANCE.imChatFrom(dbImChat);

            // 确保incrId统一使用对应imChat的id
            imChat.setIncrId(dbImChat.getId());

            if (dbImChat.getType() == DbImChatType.PRIVATE) {
                // 获取对方用户ID
                Long friendId = chatIdToFriendIdMap.get(dbImChat.getChatId());
                if (friendId != null) {
                    // 查找朋友关系获取别名
                    DbFriend dbFriend = friendIdMap.get(friendId);
                    if (dbFriend != null && StringUtils.isNotBlank(dbFriend.getFriendAlias())) {
                        // 1. 使用朋友别名
                        imChat.setName(new ImChatName(dbFriend.getFriendAlias()));
                    } else {
                        // 2. 使用用户名
                        DbUser dbUser = idUserMap.get(friendId);
                        if (dbUser != null && StringUtils.isNotBlank(dbUser.getUsername())) {
                            imChat.setName(new ImChatName(dbUser.getUsername()));
                        } else {
                            // 默认名称
                            imChat.setName(new ImChatName("未知用户"));
                        }
                    }
                }
            }

            imChatList.add(imChat);
        }
        return imChatList;
    }

    @Override
    public ImPrivateChat findPrivateChat(ImChatId chatId) {
        DbImChat dbImChat = dbImChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImChat == null) {
            return null;
        }
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImChat, dbImPrivateChat);
    }

    @Override
    public ImPrivateChat findPrivateChat(ImPrivatePair pair) {
        DbImPrivateChat dbImPrivateChat = dbImPrivateChatService.getByMember1AndMember2(pair.getMember1().getValue(), pair.getMember2().getValue()).orElse(null);
        if (dbImPrivateChat == null) {
            return null;
        }
        DbImChat dbImChat = dbImChatService.getByChatId(dbImPrivateChat.getChatId()).orElse(null);
        if (dbImChat == null) {
            return null;
        }
        return ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(dbImChat, dbImPrivateChat);
    }

    @Override
    public ImGroupChat findGroupChat(ImChatId chatId) {
        DbImGroupChat dbImGroupChat = dbImGroupChatService.getByChatId(chatId.getValue()).orElse(null);
        if (dbImGroupChat == null) {
            return null;
        }
        DbImChat dbImChat = dbImChatService.getByChatId(dbImGroupChat.getChatId()).orElse(null);
        if (dbImChat == null) {
            return null;
        }
        List<DbImGroupMember> dbImGroupMemberList = dbImGroupMemberService.getByChatId(dbImGroupChat.getChatId());
        return ImChatDomainTransformer.INSTANCE.imGroupChatFrom(dbImChat, dbImGroupChat, dbImGroupMemberList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImPrivateChat imPrivateChat) {
        DbImChat dbImChat = ImChatDbTransformer.INSTANCE.dbImChatFrom(imPrivateChat);
        if (dbImChat.getId() != null) {
            throw new IllegalArgumentException("不能保存已存在的聊天");
        }
        DbImPrivateChat dbImPrivateChat =
                ImChatDbTransformer.INSTANCE.dbImPrivateChatFrom(imPrivateChat);
        dbImChatService.save(dbImChat);
        dbImPrivateChatService.save(dbImPrivateChat);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImGroupChat imGroupChat) {
        DbImChat dbImChat = ImChatDbTransformer.INSTANCE.dbImChatFrom(imGroupChat);
        DbImGroupChat dbImGroupChat = ImChatDbTransformer.INSTANCE.dbImGroupChatFrom(imGroupChat);
        List<DbImGroupMember> dbImGroupMemberList = ImChatDbTransformer.INSTANCE.dbImGroupMemberListFrom(imGroupChat, imGroupChat.getMembers());
        if (dbImChat.getId() == null) {
            dbImChatService.save(dbImChat);
            dbImGroupChatService.save(dbImGroupChat);
            dbImGroupMemberService.saveBatchIgnoreEmpty(dbImGroupMemberList);
        } else {
            // TODO 更新群组
        }
    }
}
