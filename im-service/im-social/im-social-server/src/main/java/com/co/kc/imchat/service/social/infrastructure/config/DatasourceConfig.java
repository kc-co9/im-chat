package com.co.kc.imchat.service.social.infrastructure.config;

import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbFriendMapper;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMapper;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 社交数据源配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = {
        DbFriendMapper.class,
        DbImGroupMapper.class,
        DbImGroupMemberMapper.class
})
public class DatasourceConfig {
}
