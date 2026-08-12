package com.co.kc.imchat.plugin.web.logging;

import com.co.kc.imchat.plugin.web.contants.WebConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

public class LoggingUtils {
    private LoggingUtils() {
    }

    public static String getTraceId() {
        String traceId = MDC.get(WebConstants.TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            traceId = "N/A";
        }
        return traceId;
    }

}
