package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbFriend;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbFriendMapper;
import org.springframework.stereotype.Service;

/**
 * 好友表(DbFriend)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbFriendService extends BaseMybatisService<DbFriendMapper, DbFriend> {
}
