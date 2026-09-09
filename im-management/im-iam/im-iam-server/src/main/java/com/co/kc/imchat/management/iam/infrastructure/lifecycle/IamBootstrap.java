package com.co.kc.imchat.management.iam.infrastructure.lifecycle;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleName;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamBootstrapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.co.kc.imchat.management.iam.support.security.IamPermission;

/**
 * 受控创建首个 IAM 超级管理员。
 */
@Slf4j
@RequiredArgsConstructor
public class IamBootstrap implements ApplicationRunner {
    private final IamBootstrapProperties properties;
    private final AdministratorRepository administratorRepository;
    private final IamRoleRepository roleRepository;
    private final IamAdministratorRoleRepository administratorRoleRepository;
    private final PasswordService passwordService;
    private final SnowflakeId snowflakeId;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments arguments) {
        if (Boolean.FALSE.equals(properties.enabled())) {
            return;
        }
        if (administratorRepository.exists()) {
            log.warn("IAM 初始化配置已忽略：管理员已存在，请移除初始化凭据并关闭开关");
            return;
        }
        IamRole superRole = roleRepository.find(IamRoleType.SUPER_ADMIN)
                .orElseGet(() -> {
                    IamRole role = new IamRole(
                            new IamRoleId(snowflakeId.next()),
                            new IamRoleCode("SUPER_ADMIN"),
                            new IamRoleName("IAM 超级管理员"),
                            IamRoleType.SUPER_ADMIN,
                            IamRoleStatus.ACTIVE,
                            Arrays.stream(IamPermission.values())
                                    .map(IamPermission::getCode)
                                    .map(IamPermissionCode::new)
                                    .collect(Collectors.toUnmodifiableSet()));
                    roleRepository.save(role);
                    return role;
                });
        AdministratorId administratorId = new AdministratorId(snowflakeId.next());
        AdministratorUsername administratorUsername = new AdministratorUsername(properties.username());
        AdministratorEmail administratorEmail = new AdministratorEmail(properties.email());
        AdministratorPassword administratorPassword = passwordService.encrypt(new AdministratorRawPassword(properties.password()));

        Administrator administrator = Administrator.builder()
                .id(administratorId)
                .username(administratorUsername)
                .email(administratorEmail)
                .password(administratorPassword)
                .status(AdministratorStatus.ACTIVE)
                .build();
        administratorRepository.save(administrator);

        administratorRoleRepository.assign(administratorId, superRole.getId());
        log.warn("已创建首个 IAM 超级管理员，请立即移除初始化密码并关闭 im.iam.bootstrap.enabled");
    }
}
