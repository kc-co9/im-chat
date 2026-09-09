package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSignInCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AuthenticatedAdministratorDTO;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import com.co.kc.imchat.management.iam.transformer.application.AdministratorAppTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Set;

/**
 * IAM 管理员认证应用服务。
 */
@RequiredArgsConstructor
public class AdministratorAuthenticationAppService {

    private final AdministratorRepository administratorRepository;
    private final PasswordService passwordService;
    private final AuthenticationRestriction authenticationRestriction;
    private final AdministratorAuthorizationService administratorAuthorizationService;

    public AuthenticatedAdministratorDTO authenticate(AdministratorSignInCmd command) {
        AdministratorEmail email = new AdministratorEmail(command.email());
        AdministratorRawPassword password = new AdministratorRawPassword(command.password());

        Optional<Administrator> administrator = administratorRepository.find(email);
        if (administrator.isEmpty() || !administrator.get().isActive()) {
            passwordService.verifyUnknown(password);
            throw new AuthException("账号或密码错误");
        }

        authenticationRestriction.ensureAllowed(administrator.get().getId());
        if (!passwordService.verify(password, administrator.get().getPassword())) {
            authenticationRestriction.failed(administrator.get().getId());
            throw new AuthException("账号或密码错误");
        }
        authenticationRestriction.reset(administrator.get().getId());

        Set<IamPermissionCode> permissionCodeSet = administratorAuthorizationService.getPermissions(administrator.get().getId());
        return AdministratorAppTransformer.INSTANCE.authenticatedAdministratorDtoFrom(administrator.get(), permissionCodeSet);
    }
}
