package com.co.kc.imchat.plugin.bolt.security;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLException;
import java.util.concurrent.TimeoutException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 本次 TLS 会话实际呈现证书的有效期交集，以及客户端认证得到的对端 URI。
 * <p>
 * 只记录 D10 证书数组事实，不代表完整 CertPath 或在线吊销状态；仅提取时间区间时身份为空。
 *
 * @param latestNotBefore  本地和远端证书中最晚的生效时间，包含此边界
 * @param earliestNotAfter 本地和远端证书中最早的失效时间，不包含此边界
 * @param peerIdentity     已校验的唯一对端 URI；仅提取区间时为 null
 */
public record BoltTlsSessionFacts(Instant latestNotBefore, Instant earliestNotAfter, String peerIdentity) {
    /**
     * 判断墙钟时间是否落在左闭右开的证书交集中；不校验 URI 或重新握手。
     */
    public boolean allows(Instant instant) {
        return !instant.isBefore(latestNotBefore) && instant.isBefore(earliestNotAfter);
    }

    /**
     * 在共享连接创建预算内等待原生握手，校验唯一预期 URI 并提取会话事实。
     * 不创建替代 TLS 连接，也不将身份不符降级为普通传输错误。
     *
     * @param channel       已创建且预期包含原生 SslHandler 的连接通道
     * @param identity      调用方要求精确匹配的完整 URI
     * @param prefix        对端 URI 的允许命名空间前缀
     * @param timeoutMillis 等待握手的剩余预算，单位毫秒
     * @return 有效期交集及已校验对端身份
     * @throws Exception 握手失败、超时、中断、证书无效或身份缺失/歧义/不匹配
     */
    public static BoltTlsSessionFacts authenticate(Channel channel, String identity, String prefix, int timeoutMillis) throws Exception {
        /* 1. 取得原生 TLS handler，在剩余创建预算内等待握手并保留握手失败原因。 */
        SslHandler ssl = channel.pipeline().get(SslHandler.class);
        if (ssl == null) {
            throw new SSLException("Bolt native TLS handler missing");
        }
        if (!ssl.handshakeFuture().await(timeoutMillis)) {
            throw new TimeoutException("Bolt creation TLS budget");
        }
        if (!ssl.handshakeFuture().isSuccess()) {
            throw new SSLException("Bolt native TLS handshake failed", ssl.handshakeFuture().cause());
        }
        /* 2. 提取呈现证书的有效期交集，再从远端叶证书收集允许前缀下的 URI SAN。 */
        BoltTlsSessionFacts interval = sessionInterval(ssl);
        X509Certificate leaf = (X509Certificate) ssl.engine().getSession().getPeerCertificates()[0];
        List<String> matching = new ArrayList<>();
        Collection<List<?>> names = leaf.getSubjectAlternativeNames();
        if (names != null) {
            for (List<?> name : names) {
                if (name.size() >= 2 && Integer.valueOf(6).equals(name.get(0)) && name.get(1) instanceof String uri && uri.startsWith(prefix)) {
                    matching.add(uri);
                }
            }
        }
        /* 3. 允许前缀下必须恰有一个 URI，且与预期完整身份精确一致。 */
        if (matching.size() != 1 || !identity.equals(matching.getFirst())) {
            throw new SSLException("Bolt peer URI missing, ambiguous or unexpected");
        }
        /* 4. 绑定已认证身份，并在返回前按当前墙钟重验有效期交集。 */
        BoltTlsSessionFacts validity = new BoltTlsSessionFacts(interval.latestNotBefore(), interval.earliestNotAfter(), matching.getFirst());
        if (!validity.allows(Instant.now())) {
            throw new SSLException("Bolt presented certificate interval expired or empty");
        }
        return validity;
    }

    /**
     * 从已成功握手的本地和远端呈现数组计算有效期交集；不解析或猜测隐藏信任路径。
     *
     * @param ssl 已装配的原生 TLS handler
     * @return 当前有效的时间交集，peerIdentity 为 null
     * @throws SSLException 握手未成功、数组缺失/非 X509 或有效期交集不可用
     */
    public static BoltTlsSessionFacts sessionInterval(SslHandler ssl) throws SSLException {
        /* 1. 仅接受握手成功的会话，不从尚未验证的证书数组提取事实。 */
        if (!ssl.handshakeFuture().isSuccess()) {
            throw new SSLException("TLS handshake is not successful");
        }
        /* 2. 收集本地和远端实际呈现的 X509 数组，不补全隐藏信任路径。 */
        List<X509Certificate> certificates = new ArrayList<>();
        add(certificates, ssl.engine().getSession().getLocalCertificates());
        add(certificates, ssl.engine().getSession().getPeerCertificates());
        /* 3. 取最晚生效和最早失效时间，形成左闭右开的共同有效区间。 */
        Instant latest = certificates.stream().map(value -> value.getNotBefore().toInstant()).max(Instant::compareTo).orElseThrow();
        Instant earliest = certificates.stream().map(value -> value.getNotAfter().toInstant()).min(Instant::compareTo).orElseThrow();
        BoltTlsSessionFacts interval = new BoltTlsSessionFacts(latest, earliest, null);
        /* 4. 当前时间不在交集内即拒绝，包括尚未生效、已过期或交集为空。 */
        if (!interval.allows(Instant.now())) {
            throw new SSLException("Bolt presented certificate interval expired or empty");
        }
        return interval;
    }

    /**
     * 将会话呈现数组追加到收集列表；数组缺失、为空或包含非 X509 证书时拒绝。
     */
    private static void add(List<X509Certificate> values, Certificate[] certificates) throws SSLException {
        /* 1. 要求会话呈现非空证书数组，不能把材料缺失当作无限有效期。 */
        if (certificates == null || certificates.length == 0) {
            throw new SSLException("Bolt session certificate array missing");
        }
        /* 2. 逐项要求 X509 后追加；任一类型不符即终止本次事实提取。 */
        for (Certificate certificate : certificates) {
            if (!(certificate instanceof X509Certificate x509)) {
                throw new SSLException("Bolt session non X509 certificate");
            }
            values.add(x509);
        }
    }
}
