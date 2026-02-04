package com.co.kc.imchat.common.identity.constant;

/**
 * 雪花ID常量
 *
 * @author kc
 */
public class SnowflakeIdConstant {

    private SnowflakeIdConstant() {
    }

    /**
     * 雪花ID每一部分占用的位数
     */
    public static final int TIMESTAMP_BIT = 41;
    public static final int DATACENTER_BIT = 5;
    public static final int MACHINE_BIT = 5;
    public static final int SEQUENCE_BIT = 12;

    /**
     * 雪花ID每一部分的最大值
     */
    public static final long MAX_TIMESTAMP = ~(-1L << TIMESTAMP_BIT);
    public static final long MAX_DATACENTER = ~(-1L << DATACENTER_BIT);
    public static final long MAX_MACHINE = ~(-1L << MACHINE_BIT);
    public static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    /**
     * 雪花ID每一部分的位偏移量
     */
    public static final int TIMESTAMP_BIT_SHIFT = DATACENTER_BIT + MACHINE_BIT + SEQUENCE_BIT;
    public static final int DATACENTER_BIT_SHIFT = MACHINE_BIT + SEQUENCE_BIT;
    public static final int MACHINE_BIT_SHIFT = SEQUENCE_BIT;
    public static final int SEQUENCE_BIT_SHIFT = 0;

    /**
     * 雪花机器ID失活最大时间
     */
    public static final long MACHINE_ID_EXPIRED_TIME = 600 * 1000L;

    /**
     * 雪花机器ID维护相关常量
     */
    private static final String SNOWFLAKE_PATH = "snowflake";
    private static final String SNOWFLAKE_DATA_CENTER_PATH = SNOWFLAKE_PATH + ":" + "datacenter:%d";


    public static String getDataCenterPath(long dataCenterId) {
        return String.format(SNOWFLAKE_DATA_CENTER_PATH, dataCenterId);
    }

}
