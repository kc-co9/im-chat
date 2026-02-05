package com.co.kc.imchat.support.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * @author kc
 */
public class DateUtils {

    public static final String FORMAT_COMMON_TIME = "HH:mm:ss";
    public static final String FORMAT_COMMON_DATE = "yyyy-MM-dd";
    public static final String FORMAT_COMMON_DATETIME = "yyyy-MM-dd HH:mm:ss";

    private DateUtils() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDateTime after(long time, ChronoUnit timeUnit) {
        return now().plus(time, timeUnit);
    }

    public static long valueOfSecond(LocalTime time) {
        return time.toSecondOfDay();
    }

    public static long valueOfMilliSecond(LocalTime time) {
        return time.toNanoOfDay() / 1000;
    }

    public static long valueOfSecond(LocalDate time) {
        return time.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
    }

    public static long valueOfMilliSecond(LocalDate time) {
        return time.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long valueOfSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    public static long valueOfMilliSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String commonFormat(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern(FORMAT_COMMON_DATETIME));
    }

    public static LocalDateTime commonParse(String dateTime) {
        return LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(FORMAT_COMMON_DATETIME));
    }

    public static boolean equals(LocalDateTime t1, LocalDateTime t2) {
        if (t1 == t2) {
            return true;
        }
        if (t1 == null || t2 == null) {
            return false;
        }
        return t1.truncatedTo(ChronoUnit.SECONDS).compareTo(t2.truncatedTo(ChronoUnit.SECONDS)) == 0;
    }


}
