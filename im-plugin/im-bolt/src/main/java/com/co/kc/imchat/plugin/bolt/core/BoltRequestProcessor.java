package com.co.kc.imchat.plugin.bolt.core;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
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

public class BoltRequestProcessor extends SyncUserProcessor<BoltRequest> {
    private final Map<String, BoltRequestHandler> handlers;

    public BoltRequestProcessor(List<BoltRequestHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(handler -> key(handler.service(), handler.operation()),
                        Function.identity()));
    }

    @Override
    public Object handleRequest(BizContext bizContext, BoltRequest request) {
        BoltRequestHandler handler = handlers.get(key(request.getService(), request.getOperation()));
        if (handler == null) {
            return BoltResponse.failed("BOLT_HANDLER_NOT_FOUND", "Bolt handler not found");
        }
        try {
            Object response = handler.handle(requestContext(bizContext), request.getPayload());
            return BoltResponse.ok(JsonUtils.toJson(response));
        } catch (Exception ex) {
            return BoltResponse.failed("BOLT_HANDLER_ERROR", ex.getMessage());
        }
    }

    private BoltRequestContext requestContext(BizContext bizContext) {
        if (bizContext == null || bizContext.getConnection() == null) {
            return new BoltRequestContext(null, null, null, false, Set.of());
        }
        String peerIdentity = null;
        boolean tlsVerified = false;
        Set<String> identities = Set.of();
        SslHandler ssl = bizContext.getConnection().getChannel().pipeline().get(SslHandler.class);
        if (ssl != null && ssl.handshakeFuture().isSuccess()) {
            try {
                identities = verifiedUriIdentities(ssl);
                Certificate[] certificates = ssl.engine().getSession().getPeerCertificates();
                X509Certificate peer = (X509Certificate) certificates[0];
                peerIdentity = identities.stream().findFirst().orElse(peer.getSubjectX500Principal().getName());
                tlsVerified = true;
            } catch (CertificateException | SSLException ignored) {
                // 解析或验证失败不提供严格认证来源。
            }
        }
        return new BoltRequestContext(bizContext.getRemoteAddress(), bizContext.getRemoteHost(),
                peerIdentity, tlsVerified, identities);
    }

    static Set<String> verifiedUriIdentities(SslHandler ssl)
            throws SSLException, CertificateParsingException {
        if (!ssl.handshakeFuture().isSuccess()) {
            throw new SSLException("TLS handshake is not successful");
        }
        Certificate[] certificates = ssl.engine().getSession().getPeerCertificates();
        if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate peer)) {
            throw new SSLPeerUnverifiedException("X509 peer certificate required");
        }
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
        return Collections.unmodifiableSet(identities);
    }

    @Override
    public String interest() {
        return BoltRequest.class.getName();
    }

    private String key(String service, String operation) {
        return service + "#" + operation;
    }
}
