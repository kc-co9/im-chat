package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationCode;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationRequest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.domain.session.model.OidcIdentityToken;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAuthorizationIdQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthTokenQuery;
import com.co.kc.imchat.management.iam.transformer.application.OAuthAuthorizationAppTransformer;
import com.co.kc.imchat.management.iam.transformer.domain.OAuthAuthorizationDomainTransformer;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * OAuth 协议授权生命周期应用服务。
 */
@RequiredArgsConstructor
public class OAuthAuthorizationAppService {
    private final OAuthAuthorizationRepository authorizationRepository;
    private final OAuthClientRepository oauthClientRepository;
    private final OAuthAuthorizationService oauthAuthorizationService;

    public Optional<OAuthAuthorizationDTO> queryById(OAuthAuthorizationIdQuery query) {
        OAuthAuthorizationId authorizationId = new OAuthAuthorizationId(query.authorizationId());
        return authorizationRepository.find(authorizationId).map(OAuthAuthorizationAppTransformer.INSTANCE::oauthAuthorizationDtoFrom);
    }

    @Observed(name = "im.iam.credential.query")
    public Optional<OAuthAuthorizationDTO> queryByToken(OAuthTokenQuery query) {
        OAuthCredentialDigest tokenDigest = new OAuthCredentialDigest(query.tokenDigest());

        OAuthAuthorization authorization = query.credentialType() == null
                ? authorizationRepository.find(tokenDigest).orElse(null)
                : authorizationRepository.find(tokenDigest, query.credentialType()).orElse(null);
        if (authorization == null
                || !authorization.acceptsCredential(tokenDigest, query.credentialType())) {
            return Optional.empty();
        }
        return Optional.of(OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(authorization));
    }

    @Observed(name = "im.iam.authorization.persist")
    @Transactional(rollbackFor = Exception.class)
    public void save(OAuthAuthorizationSaveCmd command) {
        OAuthAuthorizationId authorizationId = new OAuthAuthorizationId(command.authorizationId());
        OAuthClientId clientId = new OAuthClientId(command.oauthClientId());
        OAuthGrantType grantType = command.grantType();
        OAuthPrincipal requestedPrincipal = new OAuthPrincipal(command.principalType(), command.principal());
        OAuthAuthorizationRequest request = OAuthAuthorizationDomainTransformer.INSTANCE.requestFrom(command.request());
        OAuthAuthorizationCode authorizationCode = OAuthAuthorizationDomainTransformer.INSTANCE.authorizationCodeFrom(command.authorizationCode());
        OAuthAccessToken accessToken = OAuthAuthorizationDomainTransformer.INSTANCE.accessTokenFrom(command.accessToken());
        OAuthRefreshToken refreshToken = OAuthAuthorizationDomainTransformer.INSTANCE.refreshTokenFrom(command.refreshToken());
        OidcIdentityToken idToken = OAuthAuthorizationDomainTransformer.INSTANCE.idTokenFrom(command.idToken());
        Set<OAuthScope> scopes = OAuthAuthorizationDomainTransformer.INSTANCE.scopesFrom(command.scopes());
        OAuthAuthorizationStatus status = command.status();
        Instant revokedAt = command.revokedAt();

        OAuthAuthorization authorization = authorizationRepository.findForUpdate(authorizationId).orElse(null);
        if (authorization == null) {
            if (command.expectedRowVersion() != null) {
                throw new AuthException("OAuth 授权已不存在");
            }
            OAuthClient client = oauthClientRepository.find(clientId)
                    .filter(OAuthClient::isActive)
                    .orElseThrow(() -> new AuthException("OAuth 客户端无效"));
            OAuthPrincipal principal = oauthAuthorizationService.principal(requestedPrincipal, client);
            authorization = OAuthAuthorization.builder()
                    .id(authorizationId)
                    .appId(client.getAppId())
                    .clientId(client.getClientId())
                    .principal(principal)
                    .grantType(grantType)
                    .request(request)
                    .authorizationCode(authorizationCode)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .idToken(idToken)
                    .scopes(scopes)
                    .status(status)
                    .revokedAt(revokedAt)
                    .build();
        } else {
            if (authorization.isRevoked()) {
                if (status == OAuthAuthorizationStatus.REVOKED) {
                    return;
                }
                throw new AuthException("OAuth 授权已撤销");
            }
            if (command.expectedRowVersion() == null
                    || !command.expectedRowVersion().equals(authorization.getRowVersion())) {
                throw new AuthException("OAuth 授权版本已失效");
            }
            if (status == OAuthAuthorizationStatus.REVOKED) {
                authorization.revoke(revokedAt);
            }
            authorization.changeRequest(request);
            authorization.changeAuthorizationCode(authorizationCode);
            authorization.changeAccessToken(accessToken);
            authorization.changeRefreshToken(refreshToken);
            authorization.changeIdToken(idToken);
            authorization.changeScopes(scopes);
        }

        authorizationRepository.save(authorization);
    }

    @Observed(name = "im.iam.authorization.revoke")
    @Transactional(rollbackFor = Exception.class)
    public void revoke(OAuthAuthorizationRevokeCmd command) {
        OAuthAuthorizationId authorizationId = new OAuthAuthorizationId(command.authorizationId());
        OAuthAuthorization oAuthAuthorization = authorizationRepository.findForUpdate(authorizationId).orElse(null);
        if (oAuthAuthorization != null) {
            oAuthAuthorization.revoke(Instant.now());
            authorizationRepository.save(oAuthAuthorization);
        }
    }

}
