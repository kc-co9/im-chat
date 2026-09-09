package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDeleteCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorEnableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorPasswordResetCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorRoleChangeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSessionsRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AdministratorDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.RoleAssignmentDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.IamRoleDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.AdministratorPageQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.AdministratorRoleAssignmentQuery;
import com.co.kc.imchat.management.iam.support.lock.IamLockScene;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import com.co.kc.imchat.management.iam.transformer.application.AdministratorAppTransformer;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IAM 管理员管理与角色分配应用服务。
 */
@RequiredArgsConstructor
public class AdministratorAppService {
    private final AdministratorRepository administratorRepository;
    private final OAuthSessionRepository oauthSessionRepository;
    private final PasswordService passwordService;
    private final AdministratorService administratorService;
    private final AuthenticationRestriction authenticationRestriction;
    private final IamAdministratorRoleRepository administratorRoleRepository;

    public PagingResult<AdministratorDTO> page(AdministratorPageQuery query) {
        return administratorRepository.page(query.paging())
                .map(AdministratorAppTransformer.INSTANCE::iamAdministratorDtoFrom);
    }

    public RoleAssignmentDTO getIamRoles(AdministratorRoleAssignmentQuery query) {
        AdministratorId administratorId = new AdministratorId(query.administratorId());
        administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        Set<Long> roleIds = administratorRoleRepository.findRoles(administratorId).stream()
                .map(IamRoleId::value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RoleAssignmentDTO(roleIds);
    }

    public List<IamRoleDTO> listIamRoles() {
        return administratorService.getRoles().stream()
                .map(role -> new IamRoleDTO(
                        role.getId().value(),
                        role.getCode().value(),
                        role.getName().value(),
                        role.getType().name(),
                        role.getStatus().name(),
                        role.getPermissions().stream()
                                .map(IamPermissionCode::value)
                                .collect(Collectors.toUnmodifiableSet())))
                .toList();
    }



    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = IamLockScene.SUPER_ADMIN_WRITE, key = "'global'", waitTime = LockConstants.DEFAULT_WAIT)
    public void disable(AdministratorDisableCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());

        Administrator administrator = administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        administratorService.ensureRemovable(administrator);

        administrator.disable();
        administratorRepository.save(administrator);

        oauthSessionRepository.revoke(administratorId, Instant.now());
        authenticationRestriction.reset(administratorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(AdministratorEnableCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());

        Administrator administrator = administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        administrator.enable();
        administratorRepository.save(administrator);

        authenticationRestriction.reset(administratorId);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = IamLockScene.SUPER_ADMIN_WRITE, key = "'global'", waitTime = LockConstants.DEFAULT_WAIT)
    public void delete(AdministratorDeleteCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());

        Administrator administrator = administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        administratorService.ensureRemovable(administrator);
        administratorRepository.remove(administrator);

        oauthSessionRepository.revoke(administratorId, Instant.now());
        authenticationRestriction.reset(administratorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AdministratorPasswordResetCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());
        AdministratorRawPassword rawPassword = new AdministratorRawPassword(command.password());

        Administrator administrator = administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        administrator.changePassword(passwordService.encrypt(rawPassword));
        administratorRepository.save(administrator);

        oauthSessionRepository.revoke(administratorId, Instant.now());
        authenticationRestriction.reset(administratorId);
    }

    @Observed(name = "im.iam.session.revoke.all")
    @Transactional(rollbackFor = Exception.class)
    public void revokeSessions(AdministratorSessionsRevokeCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());
        administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        oauthSessionRepository.revoke(administratorId, Instant.now());
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = IamLockScene.SUPER_ADMIN_WRITE, key = "'global'", waitTime = LockConstants.DEFAULT_WAIT)
    public void changeIamRoles(AdministratorRoleChangeCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());
        Set<IamRoleId> newRoleIds = FunctionUtils.mappingSet(command.roleIds(), IamRoleId::new);

        administratorService.ensureReplaceable(administratorId, newRoleIds);
        administratorRoleRepository.replace(administratorId, newRoleIds);
    }
}
