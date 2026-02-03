package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbImGroupMessage;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbImGroupMessageMapper;
import org.springframework.stereotype.Service;

/**
 * 群聊消息表(DbImGroupMessage)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImGroupMessageService extends BaseMybatisService<DbImGroupMessageMapper, DbImGroupMessage> {

}
