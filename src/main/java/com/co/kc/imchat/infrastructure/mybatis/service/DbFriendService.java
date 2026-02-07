package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbFriend;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbFriendMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 好友表(DbFriend)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbFriendService extends BaseMybatisService<DbFriendMapper, DbFriend> {
    public List<DbFriend> getListByUserId(Long userId) {
        return this.list(this.getQueryWrapper().eq(DbFriend::getUserId, userId));
    }

    public Optional<DbFriend> getByUserIdAndFriendUserId(Long userId, Long friendUserId) {
        return this.getFirst(this.getQueryWrapper()
                .eq(DbFriend::getUserId, userId)
                .eq(DbFriend::getFriendUserId, friendUserId)
        );
    }

    public List<DbFriend> getListByUserIdAndFriendUserIds(Long userId, List<Long> friendUserIds) {
        return this.list(this.getQueryWrapper()
                .eq(DbFriend::getUserId, userId)
                .in(DbFriend::getFriendUserId, friendUserIds)
        );
    }

    public void removeByUserIdAndFriendUserId(Long userId, Long friendUserId) {
        this.remove(this.getQueryWrapper()
                .eq(DbFriend::getUserId, userId)
                .eq(DbFriend::getFriendUserId, friendUserId));
    }
}
