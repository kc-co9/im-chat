package com.co.kc.imchat.support.utils;

import com.google.common.collect.Maps;
import io.micrometer.core.instrument.util.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Slf4j
public class HttpServletUtils {

    private HttpServletUtils() {
    }

    public static Map<String, String> getHeader(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();

        Map<String, String> result = new HashMap<>(16);
        for (; headerNames.hasMoreElements(); ) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValue = request.getHeaders(headerName);
            StringJoiner headerValueJoiner = new StringJoiner(",");
            for (; headerValue.hasMoreElements(); ) {
                headerValueJoiner.add(headerValue.nextElement());
            }
            result.put(headerName, headerValueJoiner.toString());
        }
        return result;
    }

    public static Map<String, String> getHeader(HttpServletResponse response) {
        Collection<String> headerNames = response.getHeaderNames();

        Map<String, String> result = Maps.newHashMapWithExpectedSize(headerNames.size());
        for (String headerName : headerNames) {
            Collection<String> headerValue = response.getHeaders(headerName);
            result.put(headerName, String.join(",", headerValue));
        }
        return result;
    }

    public static Map<String, String> getParameter(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Arrays.toString(e.getValue())));
    }

    public static Map<String, Object> getBody(HttpServletRequest request) {
        Enumeration<String> enumeration = request.getParameterNames();

        Map<String, Object> parameterMap = Maps.newHashMapWithExpectedSize(16);
        while (enumeration.hasMoreElements()) {
            String parameter = enumeration.nextElement();
            String[] value = request.getParameterValues(parameter);
            parameterMap.put(parameter, value);
        }

        return parameterMap;
    }

    public static String getRequestBody(HttpServletRequest request) {
        String requestBody = "{}";
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            try {
                requestBody = IOUtils.toString(wrapper.getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                log.error("获取请求Body时发生异常", ex);
            }
        }
        return requestBody;
    }

    public static String getResponseBody(HttpServletResponse response) {
        String responseBody = "{}";
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            try {
                responseBody = IOUtils.toString(wrapper.getContentInputStream(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                log.error("获取响应Body时发生异常", ex);
            }
        }
        return responseBody;
    }

    public static int getResponseStatus(HttpServletResponse response) {
        int status = response.getStatus();
        return status > 0 ? status : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }


    /**
     * 获取有意义参数
     */
    public static Object[] getAvailableArgs(Object[] args) {
        int slow = 0;
        int fast = 0;
        for (; fast < args.length; ) {
            if (!checkObjectAvailable(args[fast])) {
                fast++;
                continue;
            }

            if (slow != fast) {
                args[slow] = args[fast];
            }
            slow++;
            fast++;
        }

        return ArrayUtils.subarray(args, 0, slow);
    }

    public static boolean checkObjectAvailable(Object object) {
        return null != object &&
                !(object instanceof MultipartFile ||
                        object instanceof ServletRequest ||
                        object instanceof ServletResponse);
    }
}
