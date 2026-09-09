package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.application.service.OAuthClientSecretService;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientAccessUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientSecretRotateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAdministratorTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthApplicationTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAdministratorTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthApplicationTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientRegistrationQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientPageQuery;
import com.co.kc.imchat.management.iam.transformer.application.OAuthClientAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IAM OAuth 客户端应用服务。
 */
@RequiredArgsConstructor
public class OAuthClientAppService {
    private final ApplicationRepository applicationRepository;
    private final OAuthClientRepository oauthClientRepository;
    private final OAuthClientSecretService oAuthClientSecretService;
    private final AdministratorRepository administratorRepository;
    private final AdministratorAuthorizationService administratorAuthorizationService;
    private final OAuthSessionRepository oauthSessionRepository;

    /**
     * 查询指定所属应用的 OAuth 客户端。
     */
    public PagingResult<OAuthClientListDTO> page(OAuthClientPageQuery query) {
        AppId ownerAppId = new AppId(query.appId());
        applicationRepository.find(ownerAppId)
                .orElseThrow(() -> new NotFoundException("所属应用不存在"));
        PagingResult<OAuthClient> clients = oauthClientRepository.page(ownerAppId, query.paging());
        Set<AppId> audienceAppIds = clients.records().stream()
                .map(OAuthClient::getAudienceAppId)
                .collect(Collectors.toUnmodifiableSet());
        Map<AppId, Application> audienceApplications = applicationRepository
                .find(audienceAppIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(Application::getAppId, Function.identity()));
        return clients.map(client -> {
            Application audienceApplication = Optional.ofNullable(
                            audienceApplications.get(client.getAudienceAppId()))
                    .orElseThrow(() -> new NotFoundException("目标应用不存在"));
            return OAuthClientAppTransformer.INSTANCE.oauthClientListDtoFrom(
                    client,
                    audienceApplication.getAppKey());
        });
    }

    /**
     * 查询启用的 OAuth Client 注册信息。
     */
    public Optional<OAuthClientRegistrationDTO> queryRegistration(
            OAuthClientRegistrationQuery query
    ) {
        OAuthClientId clientId = new OAuthClientId(query.clientId());
        return oauthClientRepository.find(clientId)
                .filter(OAuthClient::isActive)
                .filter(client -> applicationRepository.find(client.getAppId()).filter(Application::isActive).isPresent())
                .filter(client -> applicationRepository.find(client.getAudienceAppId()).filter(Application::isActive).isPresent())
                .map(client -> new OAuthClientRegistrationDTO(
                        client.getClientId().value(),
                        client.getClientSecret().value(),
                        client.getName().value(),
                        client.getGrantTypes(),
                        FunctionUtils.mappingSet(client.getScopes(), OAuthScope::value),
                        FunctionUtils.mappingSet(
                                client.getRedirectUris(), RedirectUri::stringValue),
                        FunctionUtils.mappingSet(
                                client.getPostLogoutRedirectUris(),
                                RedirectUri::stringValue)));
    }

    /**
     * 解析应用 Token 签发所需的可信 Claims。
     */
    public OAuthApplicationTokenClaimsDTO getApplicationTokenClaims(
            OAuthApplicationTokenClaimsQuery query
    ) {
        OAuthClientId clientId = new OAuthClientId(query.clientId());

        OAuthClient client = oauthClientRepository.find(clientId)
                .orElseThrow(() -> new NotFoundException("OAuth 客户端不存在"));
        Application source = applicationRepository.find(client.getAppId())
                .orElseThrow(() -> new NotFoundException("所属应用不存在"));
        Application audience = applicationRepository.find(client.getAudienceAppId())
                .orElseThrow(() -> new NotFoundException("目标应用不存在"));
        return new OAuthApplicationTokenClaimsDTO(
                client.getClientId().value(),
                source.getAppKey().value(),
                audience.getAppKey().value());
    }

    /**
     * 解析管理员 Token 签发所需的可信 Claims。
     */
    public OAuthAdministratorTokenClaimsDTO getAdministratorTokenClaims(
            OAuthAdministratorTokenClaimsQuery query
    ) {
        OAuthClientId clientId = new OAuthClientId(query.clientId());
        AdministratorId administratorId = new AdministratorId(query.administratorId());

        OAuthClient client = oauthClientRepository.find(clientId)
                .orElseThrow(() -> new NotFoundException("OAuth 客户端不存在"));
        Application source = applicationRepository.find(client.getAppId())
                .orElseThrow(() -> new NotFoundException("所属应用不存在"));
        Application audience = applicationRepository.find(client.getAudienceAppId())
                .orElseThrow(() -> new NotFoundException("目标应用不存在"));
        Administrator administrator = administratorRepository.find(administratorId)
                .filter(Administrator::isActive)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        List<String> authorities = administratorAuthorizationService
                .getPermissions(administratorId, audience.getAppId())
                .stream()
                .map(ApplicationPermissionCode::value)
                .sorted()
                .toList();
        return new OAuthAdministratorTokenClaimsDTO(
                administrator.getId().value().toString(),
                administrator.getUsername().value(),
                source.getAppKey().value(),
                audience.getAppKey().value(),
                authorities);
    }

    /**
     * 注册浏览器或机器 OAuth 客户端。
     */
    public OAuthClientDTO register(OAuthClientRegisterCmd command) {
        AppKey appKey = new AppKey(command.appKey());
        AppId audienceAppId = new AppId(command.audienceAppId());
        OAuthClientId clientId = new OAuthClientId(command.clientId());
        OAuthClientName clientName = new OAuthClientName(command.name());
        OAuthRawClientSecret rawClientSecret = new OAuthRawClientSecret(command.clientSecret());
        Set<OAuthScope> oAuthScopes = FunctionUtils.mappingSet(command.scopes(), OAuthScope::new);
        Set<OAuthGrantType> grantTypes = command.grantTypes();

        if (oauthClientRepository.contains(clientId)) {
            throw new RepeatException("clientId 已存在");
        }

        Application ownerApplication = applicationRepository.find(appKey)
                .orElseThrow(() -> new NotFoundException("所属应用不存在"));
        Application audienceApplication = applicationRepository.find(audienceAppId)
                .orElseThrow(() -> new NotFoundException("目标应用不存在"));
        if (ownerApplication.getStatus() != AppStatus.ACTIVE || audienceApplication.getStatus() != AppStatus.ACTIVE) {
            throw new NotFoundException("所属应用或目标应用未启用");
        }

        OAuthClient oauthClient = OAuthClient.builder()
                .clientId(clientId)
                .appId(ownerApplication.getAppId())
                .audienceAppId(audienceApplication.getAppId())
                .name(clientName)
                .grantTypes(grantTypes)
                .scopes(oAuthScopes)
                .clientSecret(oAuthClientSecretService.encode(rawClientSecret))
                .redirectUris(OAuthClientAppTransformer.INSTANCE.redirectUrisFrom(command.redirectUris()))
                .postLogoutRedirectUris(OAuthClientAppTransformer.INSTANCE.redirectUrisFrom(command.postLogoutRedirectUris()))
                .status(OAuthClientStatus.ACTIVE)
                .build();
        oauthClientRepository.save(oauthClient);

        return OAuthClientAppTransformer.INSTANCE.oauthClientDtoFrom(oauthClient);
    }

    /**
     * 轮换 OAuth 客户端密钥，原始密钥不会进入持久化对象。
     */
    public void rotateSecret(OAuthClientSecretRotateCmd command) {
        OAuthClientId oAuthClientId = new OAuthClientId(command.clientId());
        OAuthRawClientSecret oAuthRawClientSecret = new OAuthRawClientSecret(command.clientSecret());

        OAuthClient oauthClient = oauthClientRepository.find(oAuthClientId)
                .orElseThrow(() -> new NotFoundException("OAuth 客户端不存在"));
        oauthClient.rotateSecret(oAuthClientSecretService.encode(oAuthRawClientSecret));
        oauthClientRepository.save(oauthClient);
    }

    /** 更新 OAuth 客户端访问配置并撤销旧授权。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateAccess(OAuthClientAccessUpdateCmd command) {
        OAuthClientId clientId = new OAuthClientId(command.clientId());
        Set<OAuthScope> scopes = FunctionUtils.mappingSet(command.scopes(), OAuthScope::new);
        Set<RedirectUri> redirectUris = OAuthClientAppTransformer.INSTANCE
                .redirectUrisFrom(command.redirectUris());
        Set<RedirectUri> postLogoutRedirectUris = OAuthClientAppTransformer.INSTANCE
                .redirectUrisFrom(command.postLogoutRedirectUris());

        OAuthClient client = oauthClientRepository.find(clientId)
                .orElseThrow(() -> new NotFoundException("OAuth 客户端不存在"));
        client.reviseAccess(scopes, redirectUris, postLogoutRedirectUris);
        oauthClientRepository.save(client);
        oauthSessionRepository.revoke(clientId, Instant.now());
    }

    /**
     * 停用 OAuth 客户端。
     */
    public void disable(OAuthClientDisableCmd command) {
        OAuthClientId oAuthClientId = new OAuthClientId(command.clientId());

        OAuthClient oauthClient = oauthClientRepository.find(oAuthClientId)
                .orElseThrow(() -> new NotFoundException("OAuth 客户端不存在"));
        oauthClient.disable();
        oauthClientRepository.save(oauthClient);
    }
}
