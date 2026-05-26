package com.co.kc.imchat.domain.friend.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：好友备注。
 */
public record FriendAlias(String value) {
    public FriendAlias {
        AssertUtils.domainPropNotBlank("好友备注不能为空", value);
    }}
