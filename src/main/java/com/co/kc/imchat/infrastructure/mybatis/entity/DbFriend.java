package com.co.kc.imchat.infrastructure.mybatis.entity;

import com.co.kc.imchat.infrastructure.mybatis.enums.DbFriendStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友表(DbFriend)表实体类
 *
 * @author kc
 * @since 2026-02-03 11:12:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbFriend extends BaseEntity {
    //用户ID
    private Long userId;
    //好友ID
    private Long friendUserId;
    //好友别名
    private String friendAlias;
    //好友状态 0-未知, 1-正常, 2-拉黑 3-删除
    private DbFriendStatus friendStatus;
}

