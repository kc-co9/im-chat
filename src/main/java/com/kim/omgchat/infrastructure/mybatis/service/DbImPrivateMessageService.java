package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbImPrivateMessageMapper;
import org.springframework.stereotype.Service;

/**
 * 私聊消息表(DbImPrivateMessage)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImPrivateMessageService extends BaseMybatisService<DbImPrivateMessageMapper, DbImPrivateMessage> {

}
