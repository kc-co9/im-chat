package com.co.kc.imchat.plugin.identity.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Snowflake ID 生成配置。 */
@ConfigurationProperties("im.identity.snowflake")
public record SnowflakeProperties(
        SnowflakeMode mode,
        Long dataCenterId,
        Long machineId,
        String namespace,
        Duration leaseDuration,
        Duration heartbeatInterval
) {
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(10);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    public SnowflakeProperties {
        leaseDuration = leaseDuration == null ? DEFAULT_LEASE_DURATION : leaseDuration;
        heartbeatInterval = heartbeatInterval == null
                ? DEFAULT_HEARTBEAT_INTERVAL
                : heartbeatInterval;

        AssertUtils.argNotNull(
                "im.identity.snowflake.mode must not be null",
                mode);
        AssertUtils.argNotNull(
                "im.identity.snowflake.data-center-id must not be null",
                dataCenterId);
        AssertUtils.argTrue(
                "im.identity.snowflake.data-center-id must be between 0 and 31",
                dataCenterId >= 0 && dataCenterId <= SnowflakeIdConstant.MAX_DATACENTER);
        if (mode == SnowflakeMode.STATIC) {
            AssertUtils.argNotNull(
                    "im.identity.snowflake.machine-id must not be null in STATIC mode",
                    machineId);
            AssertUtils.argTrue(
                    "im.identity.snowflake.machine-id must be between 0 and 31",
                    machineId >= 0 && machineId <= SnowflakeIdConstant.MAX_MACHINE);
        } else {
            if (namespace != null) {
                AssertUtils.argNotBlank(
                        "im.identity.snowflake.namespace must not be blank",
                        namespace);
            }
            AssertUtils.argTrue(
                    "im.identity.snowflake.lease-duration must be at least one second",
                    leaseDuration.compareTo(Duration.ofSeconds(1)) >= 0);
            AssertUtils.argTrue(
                    "im.identity.snowflake.heartbeat-interval must be positive and shorter than lease-duration",
                    !heartbeatInterval.isZero()
                            && !heartbeatInterval.isNegative()
                            && heartbeatInterval.compareTo(leaseDuration) < 0);
        }
    }

    /**
     * 解析机器租约命名空间。
     *
     * @param applicationName 当前应用名称
     * @return 显式命名空间或应用名称
     */
    public String resolveNamespace(String applicationName) {
        if (namespace != null) {
            return namespace;
        }
        AssertUtils.argNotBlank(
                "spring.application.name must not be blank when Snowflake namespace is absent",
                applicationName);
        return applicationName;
    }
}
