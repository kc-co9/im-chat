package com.co.kc.imchat.common.utils;

import com.co.kc.imchat.model.enums.ParamsConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

public class LoggingUtils {
    private LoggingUtils() {
    }

    public static String getTraceId() {
        String traceId = MDC.get(ParamsConstants.TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            traceId = "N/A";
        }
        return traceId;
    }

}
