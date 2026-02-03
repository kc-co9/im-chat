package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbUser;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbUserMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用户表(DbUser)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
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
}
