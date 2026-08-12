package com.co.kc.imchat.service.message.domain.sticker.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：表情ID。
 */
public record ImStickerId(String value) {
    public ImStickerId {
        AssertUtils.domainPropNotBlank("表情ID不能为空", value);
    }}
