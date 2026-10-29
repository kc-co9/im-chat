package com.co.kc.imchat.plugin.bolt.server;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.security.BoltTlsSessionFacts;
import com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 原生 Bolt 请求入口：提取连接认证事实，再按 service/operation 分发到业务处理器。
 * 这里只提供传输身份上下文，成员资格和业务授权仍由消费方检查。
 */
public class BoltRequestProcessor extends SyncUserProcessor<BoltRequest> {
    /**
     * 按 service#operation 索引的处理器；构造时重复键会拒绝装配。
     */
    private final Map<String, BoltRequestHandler> handlers;
    /**
     * 入站 TLS/DEV 准入配置，不以请求自报字段替代连接事实。
     */
    private final ImBoltProperties.Server policy;

    /**
     * 建立处理器索引并保留入站策略，不启动或替换网络连接。
     *
     * @param handlers 按服务和操作唯一注册的处理器
     * @param policy   入站连接事实校验使用的服务端配置
     * @throws IllegalStateException 存在重复 service/operation 键
     */
    public BoltRequestProcessor(List<BoltRequestHandler> handlers, ImBoltProperties.Server policy) {
        /* 1. 保留入站策略，按 service/operation 建立索引；重复键由收集器拒绝。 */
        this.policy = policy;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(handler -> key(handler.service(), handler.operation()),
                        Function.identity()));
    }

    /**
     * 按操作分发请求，将处理器返回值编码为成功响应。
     * 未注册操作返回 NOT_FOUND；上下文校验或处理器异常转为错误响应，不执行认证降级。
     *
     * @param bizContext 原生连接上下文，身份事实只从其中的 TLS 会话提取
     * @param request    包含服务、操作及序列化载荷的请求
     * @return 统一 BoltResponse；处理器异常不会作为成功业务结果返回
     */
    @Override
    public Object handleRequest(BizContext bizContext, BoltRequest request) {
        /* 1. 按服务与操作查找处理器，未注册时直接返回 NOT_FOUND。 */
        BoltRequestHandler handler = handlers.get(key(request.getService(), request.getOperation()));
        if (handler == null) {
            return BoltResponse.failed("BOLT_HANDLER_NOT_FOUND", "Bolt handler not found");
        }
        try {
            /* 2. 先校验并提取连接事实，再将上下文及原始载荷交给业务处理器。 */
            Object response = handler.handle(requestContext(bizContext), request.getPayload());
            /* 3. 处理结果编码为成功响应；上下文、处理器或编码异常统一转为错误响应。 */
            return BoltResponse.ok(JsonUtils.toJson(response));
        } catch (Exception ex) {
            return BoltResponse.failed("BOLT_HANDLER_ERROR", ex.getMessage());
        }
    }

    /**
     * 提取入站连接事实；TLS 会话检查失败即拒绝，只有明确的无 TLS DEV 装配可使用开发标记。
     *
     * @return 地址、唯一身份（可为空）、完整 URI 集合及 TLS/DEV 标记
     * @throws SecurityException 缺连接、会话证书无效或不满足开发准入条件
     */
    private BoltRequestContext requestContext(BizContext bizContext) {
        /* 1. 要求真实入站连接，身份不能由请求字段自行声明。 */
        if (bizContext == null || bizContext.getConnection() == null) {
            throw new SecurityException("Bolt inbound connection is required");
        }
        String peerIdentity = null;
        boolean tlsVerified = false;
        Set<String> identities = Set.of();
        SslHandler ssl = bizContext.getConnection().getChannel().pipeline().get(SslHandler.class);
        /* 2. 握手成功时提取 URI 集合并重验证书交集，只有单一 URI 才填充唯一身份。 */
        if (ssl != null && ssl.handshakeFuture().isSuccess()) {
            try {
                identities = verifiedUriIdentities(ssl);
                BoltTlsSessionFacts.sessionInterval(ssl);
                peerIdentity = identities.size() == 1 ? identities.iterator().next() : null;
                tlsVerified = true;
            } catch (CertificateException | SSLException failure) {
                throw new SecurityException("Bolt inbound session certificate facts rejected", failure);
            }
        }
        /* 3. 无已验证 TLS 时只接受显式开发明文模式；存在 TLS handler 也禁止降级。 */
        if (!tlsVerified) {
            if (policy.getMode() != BoltTransportMode.DEVELOPMENT_PLAINTEXT || ssl != null
                    || !policy.getDevelopment().isSyntheticData()) {
                throw new SecurityException("Bolt inbound verified TLS required");
            }
            BoltDevelopmentValidator.validateAddress(bizContext.getRemoteHost(), policy.getDevelopment());
        }
        /* 4. 将地址、完整身份集合及 TLS/DEV 事实交给业务侧继续授权。 */
        return new BoltRequestContext(bizContext.getRemoteAddress(), bizContext.getRemoteHost(),
                peerIdentity, tlsVerified, identities, !tlsVerified);
    }

    /**
     * 读取已成功握手的远端叶证书 URI SAN，拒绝重复或非字符串 URI，结果不可变。
     * 不要求集合只有一个身份；具体 namespace 与唯一性由业务准入规则进一步约束。
     *
     * @throws SSLException                握手未成功或缺少 X509 远端叶证书
     * @throws CertificateParsingException URI SAN 无法解析或存在歧义
     */
    static Set<String> verifiedUriIdentities(SslHandler ssl)
            throws SSLException, CertificateParsingException {
        /* 1. 确认握手成功且远端叶证书为 X509，再读取其中的 SAN。 */
        if (!ssl.handshakeFuture().isSuccess()) {
            throw new SSLException("TLS handshake is not successful");
        }
        Certificate[] certificates = ssl.engine().getSession().getPeerCertificates();
        if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate peer)) {
            throw new SSLPeerUnverifiedException("X509 peer certificate required");
        }
        /* 2. 只收集 URI 类型 SAN，拒绝非字符串值或重复 URI，不在此限制业务命名空间。 */
        Set<String> identities = new LinkedHashSet<>();
        Collection<List<?>> names = peer.getSubjectAlternativeNames();
        if (names != null) {
            for (List<?> name : names) {
                if (name.size() >= 2 && Integer.valueOf(6).equals(name.get(0))) {
                    if (!(name.get(1) instanceof String identity) || !identities.add(identity)) {
                        throw new CertificateParsingException("Ambiguous URI SAN");
                    }
                }
            }
        }
        /* 3. 返回不可变身份集合，防止后续处理篡改已提取的连接事实。 */
        return Collections.unmodifiableSet(identities);
    }

    /**
     * 声明原生处理器只接收统一 BoltRequest 类型。
     */
    @Override
    public String interest() {
        return BoltRequest.class.getName();
    }

    /**
     * 生成注册与查找共用的 service#operation 索引键，不执行远端调用。
     */
    private String key(String service, String operation) {
        return service + "#" + operation;
    }
}
