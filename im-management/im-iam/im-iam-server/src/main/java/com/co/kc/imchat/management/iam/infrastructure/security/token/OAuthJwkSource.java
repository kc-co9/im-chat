package com.co.kc.imchat.management.iam.infrastructure.security.token;

import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/** 从 classpath 开发资源或部署环境外部 PKCS12 密钥库加载 OIDC 签名密钥。 */
public class OAuthJwkSource extends ImmutableJWKSet<SecurityContext> {

    public OAuthJwkSource(
            IamAuthorizationProperties properties,
            ResourceLoader resourceLoader
    ) {
        super(jwkSet(properties, resourceLoader));
    }

    private static JWKSet jwkSet(
            IamAuthorizationProperties properties,
            ResourceLoader resourceLoader
    ) {
        try {
            Resource resource = resourceLoader.getResource(properties.keyStoreLocation());
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = resource.getInputStream()) {
                keyStore.load(input, properties.keyStorePassword().toCharArray());
            }
            Key key = keyStore.getKey(
                    properties.keyAlias(), properties.keyPassword().toCharArray());
            Certificate certificate = keyStore.getCertificate(properties.keyAlias());
            if (!(key instanceof RSAPrivateKey privateKey)
                    || certificate == null
                    || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
                throw new IllegalStateException("IAM signing key must be an RSA key pair");
            }
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(properties.keyAlias())
                    .build();
            return new JWKSet(rsaKey);
        } catch (IOException
                 | KeyStoreException
                 | CertificateException
                 | NoSuchAlgorithmException
                 | UnrecoverableKeyException exception) {
            throw new IllegalStateException(
                    "IAM signing key store cannot be loaded", exception);
        }
    }
}
