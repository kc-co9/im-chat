package com.co.kc.imchat.plugin.identity.snowflake.impl;

import com.co.kc.imchat.common.exception.ExhaustionException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant;
import com.co.kc.imchat.plugin.identity.snowflake.ISnowflakeMachineId;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Redis 租约驱动的 Snowflake 机器 ID 分配器。 */
@Slf4j
public class RedisSnowflakeMachineId implements ISnowflakeMachineId, AutoCloseable {
    private static final long INVALID_MACHINE_ID = -1L;
    private static final int OWNER_ID_BYTES = 24;

    private static final String ALLOCATE_SCRIPT_TEMPLATE = """
            local now = redis.call('TIME')[1]
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then
                redis.call('HSET', KEYS[1], ARGV[1],
                    cjson.encode({ownerId=ARGV[2], heartbeat=now}))
                return true
            end
            local lease = cjson.decode(current)
            if now - lease.heartbeat >= %d then
                redis.call('HSET', KEYS[1], ARGV[1],
                    cjson.encode({ownerId=ARGV[2], heartbeat=now}))
                return true
            end
            return false
            """;

    private static final String RENEW_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then
                return false
            end
            local lease = cjson.decode(current)
            if lease.ownerId ~= ARGV[2] then
                return false
            end
            local now = redis.call('TIME')[1]
            redis.call('HSET', KEYS[1], ARGV[1],
                cjson.encode({ownerId=ARGV[2], heartbeat=now}))
            return true
            """;

    private static final String RELEASE_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current then
                return true
            end
            local lease = cjson.decode(current)
            if lease.ownerId ~= ARGV[2] then
                return false
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
            return true
            """;

    private final RedissonClient redissonClient;
    private final long dataCenterId;
    private final String namespace;
    private final long leaseSeconds;
    private final long leaseNanos;
    private final Duration heartbeatInterval;
    private final String ownerId = GeneratorUtils.nextRandomId(OWNER_ID_BYTES);
    private final ScheduledExecutorService heartbeatExecutor;
    private final String allocationScript;

    private volatile long machineId = INVALID_MACHINE_ID;
    private volatile long leaseDeadlineNanos = Long.MIN_VALUE;
    private boolean started;
    private boolean closed;

    public RedisSnowflakeMachineId(
            RedissonClient redissonClient,
            long dataCenterId,
            String namespace,
            Duration leaseDuration,
            Duration heartbeatInterval
    ) {
        AssertUtils.argNotNull("redissonClient must not be null", redissonClient);
        AssertUtils.argTrue(
                "dataCenterId must be between 0 and 31",
                dataCenterId >= 0 && dataCenterId <= SnowflakeIdConstant.MAX_DATACENTER);
        AssertUtils.argNotBlank("namespace must not be blank", namespace);
        AssertUtils.allArgNotNull(
                "Snowflake lease durations must not be null",
                leaseDuration,
                heartbeatInterval);
        AssertUtils.argTrue(
                "leaseDuration must be at least one second",
                leaseDuration.toSeconds() > 0);
        AssertUtils.argTrue(
                "heartbeatInterval must be positive and shorter than leaseDuration",
                !heartbeatInterval.isZero()
                        && !heartbeatInterval.isNegative()
                        && heartbeatInterval.compareTo(leaseDuration) < 0);
        this.redissonClient = redissonClient;
        this.dataCenterId = dataCenterId;
        this.namespace = namespace;
        this.leaseSeconds = leaseDuration.toSeconds();
        this.leaseNanos = leaseDuration.toNanos();
        this.heartbeatInterval = heartbeatInterval;
        this.allocationScript = ALLOCATE_SCRIPT_TEMPLATE.formatted(leaseSeconds);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "im-snowflake-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 分配机器 ID 并启动续租。 */
    public synchronized void start() {
        AssertUtils.argTrue("Snowflake machine ID allocator has already started", !started);
        AssertUtils.argTrue("Snowflake machine ID allocator has already closed", !closed);
        machineId = allocateMachineId();
        started = true;
        heartbeatExecutor.scheduleWithFixedDelay(
                this::renewLease,
                heartbeatInterval.toNanos(),
                heartbeatInterval.toNanos(),
                TimeUnit.NANOSECONDS);
    }

    @Override
    public long getDataCenterId() {
        return dataCenterId;
    }

    @Override
    public long getMachineId() {
        long currentMachineId = machineId;
        if (currentMachineId == INVALID_MACHINE_ID
                || System.nanoTime() - leaseDeadlineNanos >= 0) {
            throw new IllegalStateException("Snowflake machine ID is unavailable");
        }
        return currentMachineId;
    }

    synchronized void renewLease() {
        if (closed) {
            return;
        }
        try {
            long currentMachineId = machineId;
            long leaseStartedAt = System.nanoTime();
            if (currentMachineId != INVALID_MACHINE_ID && renewMachineId(currentMachineId)) {
                leaseDeadlineNanos = leaseStartedAt + leaseNanos;
                return;
            }
            machineId = INVALID_MACHINE_ID;
            leaseDeadlineNanos = Long.MIN_VALUE;
            machineId = allocateMachineId();
        } catch (RuntimeException exception) {
            machineId = INVALID_MACHINE_ID;
            leaseDeadlineNanos = Long.MIN_VALUE;
            log.error("Snowflake 机器 ID 续租失败，暂停生成业务 ID", exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        heartbeatExecutor.shutdownNow();
        long currentMachineId = machineId;
        machineId = INVALID_MACHINE_ID;
        leaseDeadlineNanos = Long.MIN_VALUE;
        if (currentMachineId == INVALID_MACHINE_ID) {
            return;
        }
        try {
            releaseMachineId(currentMachineId);
        } catch (RuntimeException exception) {
            log.warn("Snowflake 机器 ID 释放失败，等待租约自然过期，machineId: {}",
                    currentMachineId, exception);
        }
    }

    private long allocateMachineId() {
        for (long candidate = 0; candidate <= SnowflakeIdConstant.MAX_MACHINE; candidate++) {
            long leaseStartedAt = System.nanoTime();
            if (tryAllocate(candidate)) {
                leaseDeadlineNanos = leaseStartedAt + leaseNanos;
                return candidate;
            }
        }
        throw new ExhaustionException("Snowflake machine ID 已耗尽");
    }

    private boolean tryAllocate(long candidate) {
        return execute(
                allocationScript,
                machineField(candidate),
                ownerId);
    }

    private boolean renewMachineId(long currentMachineId) {
        return execute(RENEW_SCRIPT, machineField(currentMachineId), ownerId);
    }

    private boolean releaseMachineId(long currentMachineId) {
        return execute(RELEASE_SCRIPT, machineField(currentMachineId), ownerId);
    }

    private boolean execute(String script, Object... arguments) {
        Boolean result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.BOOLEAN,
                List.of(SnowflakeIdConstant.getDataCenterPath(dataCenterId)),
                arguments);
        return Boolean.TRUE.equals(result);
    }

    private String machineField(long currentMachineId) {
        return namespace + ":" + currentMachineId;
    }
}
