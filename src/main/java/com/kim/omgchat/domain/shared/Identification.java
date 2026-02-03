package com.kim.omgchat.domain.shared;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

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
    private Long incrId;

    public Identification() {
    }

    public void setIncrId(Long incrId) {
        if (Objects.isNull(incrId)) {
            throw new IllegalArgumentException("id is null");
        }
        if (incrId <= 0L) {
            throw new IllegalArgumentException("id is less than or equal to 0");
        }
        this.incrId = incrId;
    }
}
