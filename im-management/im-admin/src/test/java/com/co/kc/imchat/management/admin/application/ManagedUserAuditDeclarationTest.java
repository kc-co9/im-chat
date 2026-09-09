package com.co.kc.imchat.management.admin.application;

import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserAuditDeclarationTest {

    @Test
    void declaresEveryPrivilegedWriteWithoutCapturingCommandPayload() throws Exception {
        Map<String, Class<?>> commands = Map.of(
                "update", ManagedUserUpdateCmd.class,
                "resetPassword", ManagedUserPasswordResetCmd.class,
                "ban", ManagedUserBanCmd.class,
                "unban", ManagedUserUnbanCmd.class,
                "delete", ManagedUserDeleteCmd.class);

        for (Map.Entry<String, Class<?>> entry : commands.entrySet()) {
            Method method = ManagedUserAppService.class.getMethod(
                    entry.getKey(), entry.getValue());
            Audited audited = method.getAnnotation(Audited.class);
            assertThat(audited).isNotNull();
            assertThat(audited.type()).isEqualTo(AuditType.BUSINESS);
            assertThat(audited.targetType()).isEqualTo("USER");
            assertThat(audited.targetId()).isEqualTo("#command.userId()");
            assertThat(audited.description().toLowerCase())
                    .doesNotContain("password", "email", "username");
        }
    }
}
