package com.co.kc.imchat.common.domain.time.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * 绝对时刻的闭区间。
 *
 * @param start 起始时刻，包含
 * @param end 结束时刻，包含
 */
public record TimeRange(Instant start, Instant end) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public TimeRange {
        AssertUtils.allDomainPropNotNull(
                "time range bounds must not be null",
                start,
                end);
        AssertUtils.domainPropTrue(
                "time range end must not be before start",
                !end.isBefore(start));
    }

    /**
     * 计算起止时刻之间的持续时间。
     *
     * @return 非负持续时间
     */
    public Duration duration() {
        return Duration.between(start, end);
    }
}
