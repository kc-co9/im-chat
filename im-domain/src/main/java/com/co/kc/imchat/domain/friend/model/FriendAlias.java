package com.co.kc.imchat.domain.friend.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 值对象：好友备注。
 */
public record FriendAlias(String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public FriendAlias {
        AssertUtils.domainPropNotBlank("好友备注不能为空", value);
    }}
