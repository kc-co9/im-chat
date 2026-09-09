package com.co.kc.imchat.service.account.model.cqrs;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserInputBoundaryTest {
    private static final Set<Class<?>> BOUNDARY_TYPES = Set.of(
            String.class, Long.class, Integer.class, Boolean.class, Paging.class);

    @Test
    void commandsAndQueriesContainOnlyBoundaryValues() {
        assertBoundaryValues(ManagedUserBanCmd.class);
        assertBoundaryValues(ManagedUserDeleteCmd.class);
        assertBoundaryValues(ManagedUserPasswordResetCmd.class);
        assertBoundaryValues(ManagedUserUnbanCmd.class);
        assertBoundaryValues(ManagedUserUpdateCmd.class);
        assertBoundaryValues(ManagedUserGetQuery.class);
        assertBoundaryValues(ManagedUserPageQuery.class);
    }

    private static void assertBoundaryValues(Class<?> type) {
        assertThat(type.getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType())
                        .as("%s.%s", type.getSimpleName(), component.getName())
                        .isIn(BOUNDARY_TYPES));
    }
}
