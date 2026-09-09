package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.CachedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.CachedManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.MysqlUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.MysqlManagedUserRepository;
import com.co.kc.imchat.service.account.infrastructure.domain.repository.RedisSessionRepository;
import com.co.kc.imchat.service.account.infrastructure.mybatis.service.DbUserService;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 账号仓储 Bean 装配。
 */
@Configuration
public class RepositoryBeans {

    @Bean
    public DbUserService dbUserService() {
        return new DbUserService();
    }

    @Bean
    public MysqlUserRepository mysqlUserRepository(DbUserService dbUserService) {
        return new MysqlUserRepository(dbUserService);
    }

    @Bean
    public MysqlManagedUserRepository mysqlManagedUserRepository(DbUserService dbUserService) {
        return new MysqlManagedUserRepository(dbUserService);
    }

    @Bean
    public ManagedUserRepository managedUserRepository(
            MysqlManagedUserRepository mysqlManagedUserRepository,
            Cache<Long, User> userIdCache,
            Cache<String, User> userEmailCache,
            Cache<String, Boolean> userEmailContainCache
    ) {
        return new CachedManagedUserRepository(
                mysqlManagedUserRepository,
                userIdCache,
                userEmailCache,
                userEmailContainCache);
    }

    @Bean
    @Primary
    public UserRepository userRepository(MysqlUserRepository mysqlUserRepository,
                                         Cache<Long, User> userIdCache,
                                         Cache<String, User> userEmailCache,
                                         Cache<String, Boolean> userEmailContainCache) {
        return new CachedUserRepository(mysqlUserRepository, userIdCache, userEmailCache, userEmailContainCache);
    }

    @Bean
    public SessionRepository sessionRepository(Cache<Long, SessionDTO> userSessionCache) {
        return new RedisSessionRepository(userSessionCache);
    }
}
