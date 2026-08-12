package com.co.kc.imchat.service.account.infrastructure.config;

import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 账号数据源配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = DbUserMapper.class)
public class DatasourceConfig {
}
