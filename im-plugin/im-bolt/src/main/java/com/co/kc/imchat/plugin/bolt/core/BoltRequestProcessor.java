package com.co.kc.imchat.plugin.bolt.core;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.net.ssl.SSLPeerUnverifiedException;
import io.netty.handler.ssl.SslHandler;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateParsingException;
import java.util.Collection;

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
            return new BoltRequestContext(null, null, null);
        }
        String peerIdentity = null;
        SslHandler sslHandler = bizContext.getConnection().getChannel()
                .pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            try {
                Certificate[] certificates = sslHandler.engine().getSession()
                        .getPeerCertificates();
                if (certificates.length > 0 && certificates[0] instanceof X509Certificate peer) {
                    peerIdentity = uriIdentity(peer);
                    if (peerIdentity == null) {
                        peerIdentity = peer.getSubjectX500Principal().getName();
                    }
                }
            } catch (SSLPeerUnverifiedException ignored) {
                // 未通过双向 TLS 验证的连接没有可用 peer identity。
            }
        }
        return new BoltRequestContext(
                bizContext.getRemoteAddress(), bizContext.getRemoteHost(), peerIdentity);
    }

    private String uriIdentity(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) {
                return null;
            }
            return names.stream()
                    .filter(name -> name.size() >= 2 && Integer.valueOf(6).equals(name.get(0)))
                    .map(name -> String.valueOf(name.get(1)))
                    .findFirst()
                    .orElse(null);
        } catch (CertificateParsingException ignored) {
            return null;
        }
    }

    @Override
    public String interest() {
        return BoltRequest.class.getName();
    }

    private String key(String service, String operation) {
        return service + "#" + operation;
    }
}
