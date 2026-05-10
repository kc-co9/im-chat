package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChatSession;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatSessionService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMessageService;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupChatRepository implements ImGroupChatRepository {
    private final DbImGroupChatSessionService dbImGroupChatSessionService;
    private final DbImGroupMessageService dbImGroupMessageService;
    private final DbImGroupChatService dbImGroupChatService;
    private final DbImGroupMemberService dbImGroupMemberService;

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
        List<DbImGroupMember> dbImGroupMemberList =
                dbImGroupMemberService.getListByUserIdAndChatIds(userId, chatIdValueList);
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
    public List<ImMessage> findLastMessageList(List<ImChatId> chatIds, UserId viewer) {
        if (CollectionUtils.isEmpty(chatIds) || viewer == null) {
            return Collections.emptyList();
        }
        List<Long> chatIdValueList = FunctionUtils.mappingList(chatIds, ImChatId::getValue);
        List<DbImGroupChatSession> sessions =
                dbImGroupChatSessionService.listByChatIdsAndUserId(chatIdValueList, viewer.getValue());
        return sessions.stream()
                .filter(s -> s.getLastMessageId() != null && s.getLastMessageId() > 0)
                .map(s -> {
                    Optional<DbImGroupMessage> dbImGroupMessage =
                            dbImGroupMessageService.getByChatIdAndMessageId(s.getChatId(), s.getLastMessageId());
                    return dbImGroupMessage.map(ImMessageDomainTransformer.INSTANCE::imGroupMessageFrom).orElse(null);
                })
                .filter(java.util.Objects::nonNull)
                .map(m -> (ImMessage) m)
                .collect(Collectors.toList());
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
