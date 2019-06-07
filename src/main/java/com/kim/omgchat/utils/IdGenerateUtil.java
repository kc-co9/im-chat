package com.kim.omgchat.utils;

/**
 * Id生成器，参考Twitter的snowflake算法 生成的Id是一个64bit的长整型数字， 其中1位符号位，41位毫秒数，10位实例Id，12位流水号
 */
public class IdGenerateUtil {

    /**
     * 实例Id，及其占位数和最大值
     */
    private static long instanceId = -1;
    private static long instanceIdBits = 10L;
    private static long maxInstanceId = (1L << instanceIdBits) - 1L;

    /**
     * 流水号，及其占位数和最大值
     */
    private static long sequence = 0L;
    private static long sequenceBits = 12L;
    private static long maxSequence = (1L << sequenceBits) - 1L;

    /**
     * 标识是否已经初始化
     */
    private static boolean inited = false;

    /**
     * 实例Id偏移位，毫秒数偏移位
     */
    private static long instanceIdShift = sequenceBits;
    private static long timestampLeftShift = sequenceBits + instanceIdBits;

    /**
     * 时间基线 2010-01-01
     */
    private static long baseline = 1262275200000L;

    /**
     * 最后一次获取Id值的时间戳
     */
    private static long lastTimestamp = -1L;

    /**
     * 初始化
     *
     * @param instanceId
     *            实例Id
     */
    public static void init(int instanceId) {
        if (instanceId > maxInstanceId || instanceId < 0) {
            throw new IllegalArgumentException(
                String.format("instanceId can't be greater than %d or less than 0", maxInstanceId));
        }
        IdGenerateUtil.instanceId = instanceId;
        IdGenerateUtil.inited = true;
    }

    /**
     * 获取Id值
     *
     * @return Id值
     */
    public synchronized static Long nextId() {
        return generateNumber();
    }

    /**
     * 生成序列
     *
     * @return 64位数字
     */
    private synchronized static long generateNumber() {
        if (!inited) {
            throw new RuntimeException("it's uninitialized");
        }

        long timestamp = currentMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format(
                "clock moved backwards, refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - baseline) << timestampLeftShift) | (instanceId << instanceIdShift) | sequence;
    }

    /**
     * 获取下一个时间戳
     *
     * @param lastTimestamp
     *            时间戳
     * @return 时间戳
     */
    private static long tilNextMillis(long lastTimestamp) {
        long timestamp = currentMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentMillis();
        }
        return timestamp;
    }

    /**
     * 获取时间戳
     *
     * @return 时间戳
     */
    private static long currentMillis() {
        return System.currentTimeMillis();
    }

}
