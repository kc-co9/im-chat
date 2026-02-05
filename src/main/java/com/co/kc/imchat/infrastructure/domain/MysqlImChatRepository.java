package com.co.kc.imchat.infrastructure.domain;

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
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MysqlImChatRepository implements ImChatRepository {
    private final DbImChatService dbImChatService;
    private final DbImPrivateChatService dbImPrivateChatService;
    private final DbImGroupChatService dbImGroupChatService;
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    public ImChat find(ImChatId chatId) {
        DbImChat dbImChat = dbImChatService.getById(chatId.getValue());
        return dbImChat != null ? ImChatDomainTransformer.INSTANCE.imChatFrom(dbImChat) : null;
    }

    @Override
    public List<ImChat> find(UserId userId) {
        List<DbImPrivateChat> dbImPrivateChatList = dbImPrivateChatService.getListByUserId(userId.getValue());
        List<DbImGroupMember> dbImGroupMemberList = dbImGroupMemberService.getListByUserId(userId.getValue());

        List<Long> imPrivateChatIdList = FunctionUtils.mappingList(dbImPrivateChatList, DbImPrivateChat::getChatId);
        List<Long> imGroupChatIdList = FunctionUtils.mappingList(dbImGroupMemberList, DbImGroupMember::getChatId);

        List<Long> chatIds = ListUtils.union(imPrivateChatIdList, imGroupChatIdList);
        List<DbImChat> dbImChatList = dbImChatService.getListByChatIds(chatIds);
        return ImChatDomainTransformer.INSTANCE.imChatListFrom(dbImChatList);
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
    public void save(ImChat imChat) {
        DbImChat dbImChat = ImChatDbTransformer.INSTANCE.dbImChatFrom(imChat);
        dbImChatService.saveOrUpdate(dbImChat);
    }
}
