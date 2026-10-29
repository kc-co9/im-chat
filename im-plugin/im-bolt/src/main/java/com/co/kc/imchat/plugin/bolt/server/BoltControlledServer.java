package com.co.kc.imchat.plugin.bolt.server;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator;

import com.alipay.remoting.config.BoltOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.config.Configuration;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;

/**
 * 拥有固定服务端传输策略，避免可变option与已经初始化的原生SSL Context产生不一致。
 */
public final class BoltControlledServer extends RpcServer {
    /**
     * 构造时固定的传输模式，后续配置对象变更不会切换已装配服务端。
     */
    private final BoltTransportMode mode;
    /**
     * 构造完成后禁止修改原生 option，避免与已创建的 SSL Context 不一致。
     */
    private boolean frozen;

    /**
     * 校验传输模式并装配原生服务端选项；不启动监听端口。
     *
     * @param configured 服务端地址、模式及 TLS/DEV 条件
     * @throws IllegalArgumentException 模式或必需材料不满足装配约束
     */
    public BoltControlledServer(ImBoltProperties.Server configured) {
        super(configured.getHost(), configured.getPort());
        /* 1. 固定传输模式，MTLS 必须同时启用 TLS 与客户端认证。 */
        mode = configured.getMode();
        ImBoltProperties.Tls tls = configured.getTls();
        if (mode == null || (mode == BoltTransportMode.MTLS && (!tls.isEnabled() || !tls.isNeedClientAuth()))) {
            throw new IllegalArgumentException("Bolt MTLS server requires TLS and client authentication");
        }
        /* 2. 开发明文模式必须禁用 TLS、声明合成数据，并通过监听地址限制。 */
        if (mode == BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
            if (tls.isEnabled() || !configured.getDevelopment().isSyntheticData()) {
                throw new IllegalArgumentException("Bolt development server requires synthetic data without TLS");
            }
            BoltDevelopmentValidator.validateAddress(configured.getHost(), configured.getDevelopment());
        }
        /* 3. 按已校验模式装配原生 TLS 选项，MTLS 同时校验并配置密钥材料。 */
        super.option(BoltServerOption.SRV_SSL_ENABLE, mode == BoltTransportMode.MTLS);
        super.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
        if (mode == BoltTransportMode.MTLS) {
            if (tls.getKeyStore() == null || tls.getKeyStore().isBlank() || tls.getKeyStorePassword() == null) {
                throw new IllegalArgumentException("Bolt TLS key-store and key-store-password are required when TLS is enabled");
            }
            super.option(BoltServerOption.SRV_SSL_KEYSTORE, tls.getKeyStore());
            super.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, tls.getKeyStorePassword());
            super.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, tls.getKeyStoreType());
        }
        /* 4. 装配完成后冻结 option，防止传输配置与原生 SSL Context 不一致。 */
        frozen = true;
    }

    /**
     * 仅在构造未冻结阶段委托原生选项设置；构造后的调用一律拒绝。
     *
     * @param key   原生选项键
     * @param value 与键类型匹配的选项值
     * @return 原生配置对象，仅未冻结时可返回
     * @throws IllegalArgumentException 构造已完成，不能替换传输配置
     */
    @Override
    public <T> Configuration option(BoltOption<T> key, T value) {
        /* 1. 构造完成后拒绝变更，保留装配时固定的传输策略。 */
        if (frozen) {
            throw new IllegalArgumentException("Controlled Bolt server options are fixed at construction");
        }
        /* 2. 仅未冻结阶段将选项交给原生配置实现。 */
        return super.option(key, value);
    }

    /**
     * 返回装配时固定的模式，供自动配置核对客户端与服务端策略是否一致。
     */
    public BoltTransportMode configuredMode() {
        return mode;
    }
}
