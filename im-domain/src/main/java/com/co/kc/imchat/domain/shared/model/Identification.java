package com.co.kc.imchat.domain.shared.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 委托ID-共享领域对象
 *
 * @author kc
 */
@Getter
@EqualsAndHashCode
public class Identification {
    /**
     * 数据库主键ID
     */
    private Long pkId;

    public Identification() {
    }

    public void setPkId(Long pkId) {
        AssertUtils.domainPropNotNull("数据库主键ID不能为空", pkId);
        AssertUtils.domainPropTrue("数据库主键ID必须大于0", pkId > 0L);
        this.pkId = pkId;
    }
}
