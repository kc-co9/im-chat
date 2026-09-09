package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDefinition;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionDeleteCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPermissionPageQuery;
import com.co.kc.imchat.management.iam.transformer.application.ApplicationPermissionAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 应用权限目录全量同步服务。
 */
@RequiredArgsConstructor
public class ApplicationPermissionAppService {
    private final ApplicationRepository applicationRepository;
    private final OAuthClientRepository oauthClientRepository;
    private final ApplicationPermissionRepository permissionRepository;
    private final ApplicationPermissionService permissionService;

    public PagingResult<ApplicationPermissionDTO> page(ApplicationPermissionPageQuery query) {
        AppId appId = new AppId(query.appId());
        Application application = applicationRepository.find(appId)
                .orElseThrow(() -> new NotFoundException("IAM 应用不存在"));
        ApplicationPermissionQueryCondition condition = new ApplicationPermissionQueryCondition(
                Optional.ofNullable(query.keyword()));
        return permissionRepository.page(application.getAppId(), condition, query.paging())
                .map(ApplicationPermissionAppTransformer.INSTANCE::applicationPermissionDtoFrom);
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronize(ApplicationPermissionCatalogSyncCmd command) {
        OAuthClientId oAuthClientId = new OAuthClientId(command.clientId());

        OAuthClient oAuthClient = oauthClientRepository.find(oAuthClientId)
                .orElseThrow(() -> new NotFoundException("应用不存在"));
        Application application = applicationRepository.find(oAuthClient.getAppId())
                .orElseThrow(() -> new NotFoundException("应用不存在"));

        List<ApplicationPermissionDefinition> definitions =
                ApplicationPermissionAppTransformer.INSTANCE.permissionDefinitionsFrom(command.permissions());
        List<ApplicationPermission> synchronizedPermissions = permissionService.synchronize(application, definitions);
        permissionRepository.saveAll(synchronizedPermissions);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(ApplicationPermissionDeleteCmd command) {
        ApplicationPermissionId permissionId = new ApplicationPermissionId(command.permissionId());

        ApplicationPermission permission = permissionRepository.find(permissionId)
                .orElseThrow(() -> new NotFoundException("权限不存在"));
        permissionService.ensureRemovable(permission);
        permissionRepository.remove(permission);
    }
}
