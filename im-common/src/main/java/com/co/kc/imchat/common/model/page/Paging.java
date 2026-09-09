package com.co.kc.imchat.common.model.page;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 页码分页边界。
 *
 * @param pageNo 页码，从 1 开始
 * @param pageSize   每页数量
 */
public record Paging(int pageNo, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Paging {
        AssertUtils.argTrue("pageNo must be positive", pageNo > 0);
        AssertUtils.argTrue("pageSize must be positive", pageSize > 0);
    }
}
