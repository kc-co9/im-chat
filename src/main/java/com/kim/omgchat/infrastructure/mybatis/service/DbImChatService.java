package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbImChat;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbImChatMapper;
import org.springframework.stereotype.Service;

/**
 * 聊天表(DbImChat)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImChatService extends BaseMybatisService<DbImChatMapper, DbImChat> {

}
