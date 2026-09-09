package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDeleteCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorEnableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorPasswordResetCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorRoleChangeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSessionsRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.query.AdministratorPageQuery;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorManagementUseCaseTest {

    @Test
    void preservesRepositoryPagingFactsWhenMappingAdministrators() {
        AdministratorRepository repository = mock(AdministratorRepository.class);
        Paging paging = new Paging(2, 20);
        when(repository.page(paging)).thenReturn(new PagingResult<>(
                paging, List.of(administrator()), 41L));
        AdministratorAppService service = new AdministratorAppService(
                repository,
                mock(OAuthSessionRepository.class),
                mock(PasswordService.class),
                mock(AdministratorService.class),
                mock(AuthenticationRestriction.class),
                mock(IamAdministratorRoleRepository.class));

        PagingResult<?> result = service.page(new AdministratorPageQuery(paging));

        assertThat(result.paging()).isEqualTo(paging);
        assertThat(result.records()).hasSize(1);
        assertThat(result.total()).isEqualTo(41L);
    }

    @ParameterizedTest
    @MethodSource("administratorMutationMethods")
    void administratorMutationAndAuthorizationRevocationShareOneTransaction(Method method) {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    void superAdministratorDecrementOperationsShareOneGlobalLock() throws NoSuchMethodException {
        List<Method> methods = List.of(
                AdministratorAppService.class.getMethod(
                        "disable", AdministratorDisableCmd.class),
                AdministratorAppService.class.getMethod(
                        "delete", AdministratorDeleteCmd.class),
                AdministratorAppService.class.getMethod(
                        "changeIamRoles", AdministratorRoleChangeCmd.class));

        assertThat(methods)
                .extracting(method -> AnnotatedElementUtils.findMergedAnnotation(
                        method, DistributeLock.class))
                .allSatisfy(lock -> {
                    assertThat(lock).isNotNull();
                    assertThat(lock.scene()).isEqualTo("im:iam:super-admin:write");
                    assertThat(lock.key()).isEqualTo("'global'");
                    assertThat(lock.waitTime()).isEqualTo(LockConstants.DEFAULT_WAIT);
                });
    }

    @Test
    void resetsPasswordToADigestAndRevokesActiveAuthorizations() {
        PasswordService codec = mock(PasswordService.class);
        Administrator administrator = mock(Administrator.class);
        AdministratorRepository repository = mock(AdministratorRepository.class);
        OAuthSessionRepository sessions = mock(OAuthSessionRepository.class);
        when(repository.find(new AdministratorId(7L))).thenReturn(Optional.of(administrator));
        when(codec.encrypt(any())).thenReturn(new AdministratorPassword("password-digest"));
        AuthenticationRestriction authenticationRestriction = mock(AuthenticationRestriction.class);
        AdministratorAppService service = new AdministratorAppService(
                repository,
                sessions,
                codec,
                mock(AdministratorService.class),
                authenticationRestriction,
                mock(IamAdministratorRoleRepository.class));

        service.resetPassword(new AdministratorPasswordResetCmd(7L, "new-password"));

        verify(administrator).changePassword(new AdministratorPassword("password-digest"));
        verify(repository).save(administrator);
        verify(sessions).revoke(any(AdministratorId.class), any());
        verify(authenticationRestriction).reset(new AdministratorId(7L));
    }

    @Test
    void revokesAllSessionsOwnedByTheSpecifiedAdministrator() {
        AdministratorRepository repository = mock(AdministratorRepository.class);
        OAuthSessionRepository sessions = mock(OAuthSessionRepository.class);
        when(repository.find(new AdministratorId(7L)))
                .thenReturn(Optional.of(administrator()));
        AdministratorAppService service = new AdministratorAppService(
                repository,
                sessions,
                mock(PasswordService.class),
                mock(AdministratorService.class),
                mock(AuthenticationRestriction.class),
                mock(IamAdministratorRoleRepository.class));

        service.revokeSessions(new AdministratorSessionsRevokeCmd(7L));

        verify(sessions).revoke(any(AdministratorId.class), any());
    }

    private static Stream<Method> administratorMutationMethods() throws NoSuchMethodException {
        return Stream.of(
                AdministratorAppService.class.getMethod(
                        "disable", AdministratorDisableCmd.class),
                AdministratorAppService.class.getMethod(
                        "enable", AdministratorEnableCmd.class),
                AdministratorAppService.class.getMethod(
                        "delete", AdministratorDeleteCmd.class),
                AdministratorAppService.class.getMethod(
                        "resetPassword", AdministratorPasswordResetCmd.class),
                AdministratorAppService.class.getMethod(
                        "revokeSessions", AdministratorSessionsRevokeCmd.class));
    }

    private Administrator administrator() {
        return Administrator.builder()
                .id(new AdministratorId(7L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("digest"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }
}
