package com.co.kc.imchat.management.iam.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.application.AdministratorAppService;
import com.co.kc.imchat.management.iam.application.AdministratorAuthenticationAppService;
import com.co.kc.imchat.management.iam.application.ApplicationAppService;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.application.ApplicationRoleAppService;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationAdministratorRoleService;
import com.co.kc.imchat.management.iam.application.OAuthSessionAppService;
import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamLoginProperties;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamBootstrapProperties;
import com.co.kc.imchat.management.iam.infrastructure.lifecycle.IamBootstrap;
import com.co.kc.imchat.management.iam.infrastructure.domain.service.BcryptPasswordService;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.application.service.OAuthClientSecretService;
import com.co.kc.imchat.management.iam.infrastructure.domain.service.BcryptOAuthClientSecretService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.co.kc.imchat.management.audit.sdk.support.AuditTemplate;
import com.co.kc.imchat.management.iam.support.audit.IamAuthenticationAuditListener;
import com.co.kc.imchat.management.iam.support.audit.IamAuditPublisher;
import com.co.kc.imchat.management.iam.support.audit.IamProtocolAuditListener;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import org.springframework.data.redis.core.StringRedisTemplate;

/** IAM 领域能力与应用服务 Bean 配置。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        IamLoginProperties.class,
        IamBootstrapProperties.class
})
public class IamServiceBeans {

    @Bean
    public IamAuditPublisher iamAuditPublisher(
            AuditTemplate auditTemplate
    ) {
        return new IamAuditPublisher(
                auditTemplate);
    }

    @Bean
    public IamAuthenticationAuditListener iamAuthenticationAuditListener(
            IamAuditPublisher auditPublisher,
            AdministratorRepository administratorRepository,
            AuthenticationRestriction authenticationRestriction
    ) {
        return new IamAuthenticationAuditListener(
                auditPublisher,
                administratorRepository,
                authenticationRestriction);
    }

    @Bean
    public IamProtocolAuditListener iamProtocolAuditListener(
            IamAuditPublisher auditPublisher
    ) {
        return new IamProtocolAuditListener(auditPublisher);
    }

    @Bean
    public OAuthSessionAppService oauthSessionAppService(
            OAuthSessionRepository oauthSessionRepository
    ) {
        return new OAuthSessionAppService(oauthSessionRepository);
    }

    @Bean
    public OAuthAuthorizationAppService oauthAuthorizationAppService(
            OAuthAuthorizationRepository oauthAuthorizationRepository,
            OAuthClientRepository oauthClientRepository,
            OAuthAuthorizationService oauthAuthorizationService
    ) {
        return new OAuthAuthorizationAppService(
                oauthAuthorizationRepository,
                oauthClientRepository,
                oauthAuthorizationService);
    }

    @Bean
    public OAuthAuthorizationService oauthAuthorizationService(
            AdministratorRepository administratorRepository
    ) {
        return new OAuthAuthorizationService(administratorRepository);
    }

    @Bean
    public PasswordService passwordService() {
        return new BcryptPasswordService();
    }

    @Bean
    public OAuthClientSecretService oauthClientSecretCodec() {
        return new BcryptOAuthClientSecretService();
    }

    @Bean
    public AuthenticationRestriction authenticationRestriction(
            StringRedisTemplate redisTemplate,
            IamLoginProperties properties
    ) {
        return new AuthenticationRestriction(redisTemplate, properties);
    }

    @Bean
    public AdministratorAuthenticationAppService administratorAuthenticationAppService(
            AdministratorRepository administratorRepository,
            PasswordService passwordService,
            AuthenticationRestriction authenticationRestriction,
            AdministratorAuthorizationService administratorAuthorizationService
    ) {
        return new AdministratorAuthenticationAppService(
                administratorRepository,
                passwordService,
                authenticationRestriction,
                administratorAuthorizationService);
    }

    @Bean
    public AdministratorAppService administratorAppService(
            AdministratorRepository administratorRepository,
            OAuthSessionRepository oauthSessionRepository,
            PasswordService passwordService,
            AdministratorService administratorService,
            AuthenticationRestriction authenticationRestriction,
            IamAdministratorRoleRepository administratorRoleRepository
    ) {
        return new AdministratorAppService(
                administratorRepository,
                oauthSessionRepository,
                passwordService,
                administratorService,
                authenticationRestriction,
                administratorRoleRepository);
    }

    @Bean
    public ApplicationAppService applicationAppService(
            ApplicationRepository applicationRepository,
            SnowflakeId snowflakeId
    ) {
        return new ApplicationAppService(
                applicationRepository,
                snowflakeId);
    }

    @Bean
    public OAuthClientAppService oauthClientAppService(
            ApplicationRepository applicationRepository,
            OAuthClientRepository oauthClientRepository,
            OAuthClientSecretService oauthClientSecretCodec,
            AdministratorRepository administratorRepository,
            AdministratorAuthorizationService administratorAuthorizationService,
            OAuthSessionRepository oauthSessionRepository
    ) {
        return new OAuthClientAppService(
                applicationRepository,
                oauthClientRepository,
                oauthClientSecretCodec,
                administratorRepository,
                administratorAuthorizationService,
                oauthSessionRepository);
    }

    @Bean
    public ApplicationPermissionAppService applicationPermissionAppService(
            ApplicationRepository applicationRepository,
            OAuthClientRepository oauthClientRepository,
            ApplicationPermissionRepository permissionRepository,
            ApplicationPermissionService permissionService
    ) {
        return new ApplicationPermissionAppService(
                applicationRepository,
                oauthClientRepository,
                permissionRepository,
                permissionService);
    }

    @Bean
    public ApplicationPermissionService permissionService(
            ApplicationPermissionRepository permissionRepository,
            SnowflakeId snowflakeId
    ) {
        return new ApplicationPermissionService(permissionRepository, snowflakeId);
    }

    @Bean
    public ApplicationRoleAppService roleAppService(
            ApplicationRepository applicationRepository,
            ApplicationRoleRepository roleRepository,
            SnowflakeId snowflakeId,
            ApplicationPermissionService permissionService,
            ApplicationAdministratorRoleRepository administratorRoleRepository,
            OAuthSessionRepository oauthSessionRepository,
            ApplicationAdministratorRoleService applicationRoleService
    ) {
        return new ApplicationRoleAppService(
                applicationRepository,
                permissionService,
                roleRepository,
                snowflakeId,
                administratorRoleRepository,
                oauthSessionRepository,
                applicationRoleService);
    }

    @Bean
    public ApplicationAdministratorRoleService applicationAdministratorRoleService(
            ApplicationRepository applicationRepository,
            AdministratorRepository administratorRepository,
            ApplicationRoleRepository roleRepository,
            ApplicationAdministratorRoleRepository assignmentRepository
    ) {
        return new ApplicationAdministratorRoleService(
                applicationRepository,
                administratorRepository,
                roleRepository,
                assignmentRepository);
    }

    @Bean
    public IamBootstrap iamBootstrap(
            IamBootstrapProperties properties,
            AdministratorRepository administratorRepository,
            IamRoleRepository roleRepository,
            IamAdministratorRoleRepository administratorRoleRepository,
            PasswordService passwordService,
            SnowflakeId snowflakeId
    ) {
        return new IamBootstrap(
                properties,
                administratorRepository,
                roleRepository,
                administratorRoleRepository,
                passwordService,
                snowflakeId);
    }

    @Bean
    public AdministratorService administratorService(
            IamRoleRepository roleRepository,
            IamAdministratorRoleRepository administratorRoleRepository
    ) {
        return new AdministratorService(roleRepository, administratorRoleRepository);
    }

    @Bean
    public AdministratorAuthorizationService administratorAuthorizationService(
            IamAdministratorRoleRepository iamAdministratorRoleRepository,
            IamRoleRepository iamRoleRepository,
            ApplicationAdministratorRoleRepository applicationAdministratorRoleRepository,
            ApplicationRoleRepository roleRepository,
            ApplicationPermissionRepository permissionRepository
    ) {
        return new AdministratorAuthorizationService(
                iamAdministratorRoleRepository,
                iamRoleRepository,
                applicationAdministratorRoleRepository,
                roleRepository,
                permissionRepository);
    }
}
