package com.co.kc.imchat.service.message.infrastructure.config;

import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupInboxMessageMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImPrivateInboxMessageMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 消息数据源配置。
 */
@Configuration
@MapperScan(basePackageClasses = {
        DbImPrivateChatMapper.class,
        DbImGroupChatMapper.class,
        DbImPrivateInboxMessageMapper.class,
        DbImGroupInboxMessageMapper.class
})
public class DatasourceConfig {
}
