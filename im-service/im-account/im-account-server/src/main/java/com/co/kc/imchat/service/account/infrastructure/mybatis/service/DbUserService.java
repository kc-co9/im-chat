package com.co.kc.imchat.service.account.infrastructure.mybatis.service;

import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用户表(DbUser)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
public class DbUserService extends BaseMybatisService<DbUserMapper, DbUser> {

    public Optional<DbUser> getByUserId(Long userId) {
        return getFirst(this.getQueryWrapper().eq(DbUser::getUserId, userId));
    }

    public List<DbUser> getListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return list(this.getQueryWrapper().in(DbUser::getUserId, userIds));
    }

    public Optional<DbUser> getByEmail(String email) {
        return getFirst(this.getQueryWrapper().eq(DbUser::getEmail, email));
    }

    public void removeByUserId(Long userId) {
        this.remove(this.getQueryWrapper().eq(DbUser::getUserId, userId));
    }
}
