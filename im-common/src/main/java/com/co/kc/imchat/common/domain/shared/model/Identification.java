package com.co.kc.imchat.common.domain.shared.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 委托ID-共享领域对象
 *
 * @author kc
 */
@Getter
@EqualsAndHashCode
public class Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库主键ID
     */
    @EqualsAndHashCode.Exclude
    private Long pkId;

    /**
     * 持久化并发版本；领域规则不解释其数值，只在仓储更新时用于冲突检测。
     */
    @EqualsAndHashCode.Exclude
    private Long rowVersion = 0L;

    public Identification() {
    }

    public void setPkId(Long pkId) {
        AssertUtils.domainPropNotNull("数据库主键ID不能为空", pkId);
        AssertUtils.domainPropTrue("数据库主键ID必须大于0", pkId > 0L);
        this.pkId = pkId;
    }

    public void setRowVersion(Long rowVersion) {
        AssertUtils.domainPropNotNull("持久化版本不能为空", rowVersion);
        AssertUtils.domainPropTrue("持久化版本不能小于0", rowVersion >= 0L);
        this.rowVersion = rowVersion;
    }
}
