package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import org.springframework.stereotype.Service;

/**
 * 私聊表(DbImPrivateChat)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImPrivateChatService extends BaseMybatisService<DbImPrivateChatMapper, DbImPrivateChat> {

}
