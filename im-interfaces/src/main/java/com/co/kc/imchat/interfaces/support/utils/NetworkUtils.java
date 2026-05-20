package com.co.kc.imchat.interfaces.support.utils;

import com.google.common.collect.Maps;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * @author kc
 */
public class NetworkUtils {
    private NetworkUtils() {
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

    public static Map<String, Object> getBody(HttpServletRequest request) {
        Enumeration<String> enumeration = request.getParameterNames();

        Map<String, Object> parameterMap = new HashMap<>(16);
        while (enumeration.hasMoreElements()) {
            String parameter = enumeration.nextElement();
            String[] value = request.getParameterValues(parameter);
            parameterMap.put(parameter, value);
        }

        return parameterMap;
    }

    /**
     * 获取本机所有非回环、非虚拟网卡的IPv4地址
     */
    public static Set<String> getLocalIpv4() {
        Set<String> ipAddresses = new HashSet<>();
        try {
            // 遍历所有网络接口
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // 过滤掉虚拟网卡和未启用的网卡
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }

                // 遍历接口下的所有IP地址
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // 只保留IPv4地址，排除回环地址
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        ipAddresses.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException("获取本机IP地址失败", e);
        }
        return ipAddresses;
    }

    /**
     * 获取第一个可用的IPv4地址（常用于单网卡场景）
     */
    public static String getFirstLocalIpv4() {
        Set<String> ipAddresses = getLocalIpv4();
        return ipAddresses.isEmpty() ? null : ipAddresses.iterator().next();
    }
}
