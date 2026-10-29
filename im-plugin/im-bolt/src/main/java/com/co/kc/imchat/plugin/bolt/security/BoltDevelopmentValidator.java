package com.co.kc.imchat.plugin.bolt.security;

import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;

import java.net.URI;
import java.util.List;

/**
 * 显式 DEV 装配的启动条件与端点校验，不为生产 TLS 提供降级路径。
 * <p>
 * 字面地址检查不能独自证明网络隔离；部署 internal 网络及附着检查仍须成立。
 */
public final class BoltDevelopmentValidator {
    /** 仅提供无状态校验入口，不允许创建实例。 */
    private BoltDevelopmentValidator() {
    }

    /**
     * 仅允许显式 bolt-dev、非生产 profile 且声明合成数据的装配。
     *
     * @param development 开发模式约束声明
     * @param activeProfiles 启动时启用的 profile 集合
     * @throws IllegalArgumentException 未启用 bolt-dev、混入生产 profile 或未声明合成数据
     */
    public static void validateProfiles(ImBoltProperties.Development development, List<String> activeProfiles) {
        if (!activeProfiles.contains("bolt-dev") || activeProfiles.stream().anyMatch(profile -> profile.equals("production") || profile.equals("prod"))) {
            throw new IllegalArgumentException("Bolt development requires bolt-dev without production profile");
        }
        if (!development.isSyntheticData()) {
            throw new IllegalArgumentException("Bolt development requires synthetic data");
        }
    }

    /**
     * 只接受字面回环地址，或已声明隔离的 172.30.88.0/24 容器地址；不进行 DNS 解析。
     *
     * @param host 不含端口的主机地址
     * @param development 是否声明隔离容器网络；声明本身不证明部署隔离
     * @throws IllegalArgumentException 地址不在允许范围内
     */
    public static void validateAddress(String host, ImBoltProperties.Development development) {
        if ("127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) {
            return;
        }
        if (development.isIsolatedContainerNetwork() && host != null && host.matches("172\\.30\\.88\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])")) {
            return;
        }
        throw new IllegalArgumentException("Bolt development address requires literal loopback or declared isolated 172.30.88.0/24 network");
    }

    /**
     * 校验 address:port 结构后再检查字面地址，不接受带用户信息、路径、查询参数或片段的端点。
     *
     * @param address 带端口的目标地址，IPv6 地址须使用方括号
     * @param development 开发网络约束声明
     * @throws IllegalArgumentException 端点无法解析、结构不符或地址不在允许范围内
     */
    public static void validateTarget(String address, ImBoltProperties.Development development) {
        URI endpoint = URI.create("bolt://" + address);
        if (endpoint.getPort() <= 0 || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null || (endpoint.getRawPath() != null && !endpoint.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("Invalid Bolt development endpoint");
        }
        validateAddress(endpoint.getHost(), development);
    }
}
