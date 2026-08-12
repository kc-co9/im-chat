package com.co.kc.imchat.service.social.domain.friend.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：好友展示名称。
 */
public record FriendDisplayName(String value) {
    public FriendDisplayName {
        AssertUtils.domainPropNotBlank("好友展示名称不能为空", value);
    }}
