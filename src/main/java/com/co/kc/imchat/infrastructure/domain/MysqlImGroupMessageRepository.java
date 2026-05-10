package com.co.kc.imchat.infrastructure.domain;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.message.ImGroupMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMember;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatSessionService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMemberService;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupMessageService;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.db.ImMessageDbTransformer;
import com.co.kc.imchat.transformer.domain.ImMessageDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MysqlImGroupMessageRepository implements ImGroupMessageRepository {
    private final DbImGroupMessageService dbImGroupMessageService;
    private final DbImGroupChatSessionService dbImGroupChatSessionService;
    private final DbImGroupMemberService dbImGroupMemberService;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void save(ImGroupMessage imMessage) {
        DbImGroupMessage dbMessage = ImMessageDbTransformer.INSTANCE.dbImGroupMessageFrom(imMessage);
        dbImGroupMessageService.saveOrUpdate(dbMessage);

        Long chatId = imMessage.getChatId().getValue();
        Long messageId = imMessage.getId().getValue();
        List<DbImGroupMember> members = dbImGroupMemberService.getByChatId(chatId);
        for (DbImGroupMember member : members) {
            dbImGroupChatSessionService.upsertLastMessageId(chatId, member.getUserId(), messageId);
        }
    }

    @Override
    public ImGroupMessage find(ImChatId chatId, ImMessageId messageId) {
        Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getByChatIdAndMessageId(chatId.getValue(), messageId.getValue());
        return dbImGroupMessage.map(ImMessageDomainTransformer.INSTANCE::imGroupMessageFrom).orElse(null);
    }

    @Override
    public List<ImGroupMessage> queryHistory(ImChatId imChatId, ImMessageId imLastMessageId, Integer count) {
        IPage<DbImGroupMessage> dbImGroupMessagePage = dbImGroupMessageService.page(new Page<>(1, count), dbImGroupMessageService.getQueryWrapper()
                .eq(DbImGroupMessage::getChatId, imChatId.getValue())
                .lt(imLastMessageId != null, DbImGroupMessage::getMessageId, FunctionUtils.mappingOrNull(imLastMessageId, ImMessageId::getValue))
                .orderByDesc(DbImGroupMessage::getMessageId));
        return ImMessageDomainTransformer.INSTANCE.imGroupMessageListFrom(dbImGroupMessagePage.getRecords());
    }

    @Override
    public ImGroupMessage queryDetail(ImChatId chatId, ImMessageToken messageToken) {
        Optional<DbImGroupMessage> dbImGroupMessage = dbImGroupMessageService.getFirst(dbImGroupMessageService.getQueryWrapper()
                .eq(DbImGroupMessage::getChatId, chatId.getValue())
                .eq(DbImGroupMessage::getToken, messageToken.getValue()));
        return dbImGroupMessage.map(ImMessageDomainTransformer.INSTANCE::imGroupMessageFrom).orElse(null);
    }
}
