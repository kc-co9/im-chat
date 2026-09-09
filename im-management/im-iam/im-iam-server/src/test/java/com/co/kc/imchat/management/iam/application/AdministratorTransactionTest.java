package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDeleteCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorPasswordResetCmd;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorTransactionTest {

    @Test
    void rollsBackDisableWhenAuthorizationRevocationFails() {
        verifyRollback(service -> service.disable(new AdministratorDisableCmd(7L)), false);
    }

    @Test
    void rollsBackDeleteWhenAuthorizationRevocationFails() {
        verifyRollback(service -> service.delete(new AdministratorDeleteCmd(7L)), true);
    }

    @Test
    void rollsBackPasswordResetWhenAuthorizationRevocationFails() {
        verifyRollback(
                service -> service.resetPassword(
                        new AdministratorPasswordResetCmd(7L, "new-password")),
                false);
    }

    private void verifyRollback(Consumer<AdministratorAppService> operation, boolean removes) {
        AdministratorRepository administratorRepository = mock(AdministratorRepository.class);
        OAuthSessionRepository sessionRepository = mock(OAuthSessionRepository.class);
        PasswordService passwordService = mock(PasswordService.class);
        when(administratorRepository.find(any(AdministratorId.class)))
                .thenReturn(Optional.of(administrator()));
        when(passwordService.encrypt(any()))
                .thenReturn(new AdministratorPassword("new-password-digest"));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("authorization revocation failed");
        }).when(sessionRepository).revoke(any(AdministratorId.class), any());
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        AdministratorAppService service = transactional(
                new AdministratorAppService(
                        administratorRepository,
                        sessionRepository,
                        passwordService,
                        mock(AdministratorService.class),
                        mock(AuthenticationRestriction.class),
                        mock(IamAdministratorRoleRepository.class)),
                transactionManager);

        assertThatThrownBy(() -> operation.accept(service))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authorization revocation failed");

        assertThat(transactionManager.rollbackCount).isEqualTo(1);
        assertThat(transactionManager.commitCount).isZero();
        if (removes) {
            verify(administratorRepository).remove(any(Administrator.class));
        } else {
            verify(administratorRepository).save(any(Administrator.class));
        }
    }

    private AdministratorAppService transactional(
            AdministratorAppService target,
            RecordingTransactionManager transactionManager
    ) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        return (AdministratorAppService) proxyFactory.getProxy();
    }

    private Administrator administrator() {
        return Administrator.builder()
                .id(new AdministratorId(7L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("password-digest"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
