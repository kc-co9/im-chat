package com.co.kc.imchat.management.iam.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthClientRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlAdministratorRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlIamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlIamRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthSessionRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationPermissionDomainTransformer;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationRoleDomainTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationAdministratorRoleService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAppService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthClientService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRoleService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalAdministratorRoleService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalRoleService;

/** IAM 仓储 Bean 配置。 */
@Configuration
public class IamRepositoryBeans {

    @Bean
    public AdministratorRepository administratorRepository(
            DbIamAdministratorService administratorService
    ) {
        return new MysqlAdministratorRepository(administratorService);
    }

    @Bean
    public ApplicationAdministratorRoleRepository applicationAdministratorRoleRepository(
            DbIamApplicationAdministratorRoleService administratorRoleService
    ) {
        return new MysqlApplicationAdministratorRoleRepository(administratorRoleService);
    }

    @Bean
    public IamAdministratorRoleRepository iamAdministratorRoleRepository(
            DbIamInternalAdministratorRoleService administratorRoleService
    ) {
        return new MysqlIamAdministratorRoleRepository(administratorRoleService);
    }

    @Bean
    public IamRoleRepository iamRoleRepository(
            DbIamInternalRoleService roleService
    ) {
        return new MysqlIamRoleRepository(roleService);
    }

    @Bean
    public ApplicationRepository applicationRepository(
            DbIamAppService applicationService
    ) {
        return new MysqlApplicationRepository(applicationService);
    }

    @Bean
    public OAuthClientRepository oauthClientRepository(
            DbIamOAuthClientService oauthClientService
    ) {
        return new MysqlOAuthClientRepository(oauthClientService);
    }

    @Bean
    public ApplicationPermissionRepository permissionRepository(
            DbIamApplicationPermissionService permissionService,
            DbIamApplicationRolePermissionService rolePermissionService
    ) {
        return new MysqlApplicationPermissionRepository(
                permissionService,
                ApplicationPermissionDomainTransformer.INSTANCE,
                rolePermissionService);
    }

    @Bean
    public ApplicationRoleRepository roleRepository(
            DbIamApplicationRoleService roleService,
            DbIamApplicationRolePermissionService rolePermissionService
    ) {
        return new MysqlApplicationRoleRepository(
                roleService,
                rolePermissionService,
                ApplicationRoleDomainTransformer.INSTANCE);
    }

    @Bean
    public OAuthSessionRepository oauthSessionRepository(
            DbIamOAuthAuthorizationService authorizationService,
            DbIamAdministratorService administratorService
    ) {
        return new MysqlOAuthSessionRepository(authorizationService, administratorService);
    }

    @Bean
    public OAuthAuthorizationRepository oauthAuthorizationRepository(
            DbIamOAuthAuthorizationService authorizationService
    ) {
        return new MysqlOAuthAuthorizationRepository(authorizationService);
    }

}
