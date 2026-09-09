package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSignInCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AuthenticatedAdministratorDTO;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamLoginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AdministratorAuthenticationAppServiceTest {
    private final MemoryAdministratorRepository repository = new MemoryAdministratorRepository();
    private final PasswordService passwordService = new PlainPasswordCodec();
    private final AuthenticationRestriction authenticationRestriction = mock(AuthenticationRestriction.class);
    private final AdministratorAuthorizationService administratorAuthorizationService =
            mock(AdministratorAuthorizationService.class);
    private final AdministratorAuthenticationAppService service = new AdministratorAuthenticationAppService(
            repository,
            passwordService,
            authenticationRestriction,
            administratorAuthorizationService);

    @BeforeEach
    void setUp() {
        repository.clear();
        repository.save(activeAdministrator());
        reset(authenticationRestriction);
        reset(administratorAuthorizationService);
        when(administratorAuthorizationService.getPermissions(new AdministratorId(1L)))
                .thenReturn(Set.of(new IamPermissionCode("iam:administrator:read")));
    }

    @Test
    void authenticatesByEmail() {
        AuthenticatedAdministratorDTO administrator = service.authenticate(
                new AdministratorSignInCmd("admin@example.com", "strong-password"));

        assertThat(administrator.administratorId()).isEqualTo(1L);
        assertThat(administrator.username()).isEqualTo("admin");
        assertThat(administrator.permissions())
                .containsExactly("iam:administrator:read");
    }

    @Test
    void hidesMissingWrongDisabledAndRestrictedAccountDetails() {
        assertAuthenticationFailed("missing@example.com", "strong-password");
        assertAuthenticationFailed("admin@example.com", "wrong-password");
        verify(authenticationRestriction).failed(new AdministratorId(1L));

        Administrator administrator = repository.find(
                new AdministratorUsername("admin")).orElseThrow();
        administrator.disable();
        repository.save(administrator);
        assertAuthenticationFailed("admin@example.com", "strong-password");

        administrator.enable();
        repository.save(administrator);
        doThrow(new AuthException("账号或密码错误"))
                .when(authenticationRestriction).ensureAllowed(new AdministratorId(1L));
        assertAuthenticationFailed("admin@example.com", "strong-password");
    }

    @Test
    void successfulLoginClearsTemporaryProtection() {
        service.authenticate(new AdministratorSignInCmd(
                "admin@example.com",
                "strong-password"));

        verify(authenticationRestriction).reset(new AdministratorId(1L));
    }

    @Test
    void authenticationChecksTemporaryProtectionBeforePassword() {
        service.authenticate(new AdministratorSignInCmd(
                "admin@example.com",
                "strong-password"));

        verify(authenticationRestriction).ensureAllowed(new AdministratorId(1L));
    }

    @Test
    void verifiesPasswordWorkForAnUnknownEmail() {
        AdministratorRepository administrators = mock(AdministratorRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        when(administrators.find(new AdministratorEmail("missing@example.com")))
                .thenReturn(Optional.empty());
        AdministratorAuthenticationAppService authentication =
                new AdministratorAuthenticationAppService(
                        administrators,
                        passwords,
                        mock(AuthenticationRestriction.class),
                        mock(AdministratorAuthorizationService.class));

        assertThatThrownBy(() -> authentication.authenticate(
                new AdministratorSignInCmd("missing@example.com", "strong-password")))
                .isInstanceOf(AuthException.class);
        verify(passwords).verifyUnknown(
                new AdministratorRawPassword("strong-password"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsFiveSimultaneousInvalidAttemptsWithoutLosingFailures() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AtomicInteger failures = new AtomicInteger();
        when(redisTemplate.hasKey("im:iam:login:restricted:1")).thenReturn(false);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()))
                .thenAnswer(invocation -> failures.incrementAndGet() >= 5 ? 1L : 0L);
        AuthenticationRestriction restriction = new AuthenticationRestriction(
                redisTemplate,
                new IamLoginProperties(5, Duration.ofMinutes(15)));
        AdministratorAuthenticationAppService concurrentService =
                new AdministratorAuthenticationAppService(
                        repository,
                        passwordService,
                        restriction,
                        administratorAuthorizationService);
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            List<Future<Boolean>> attempts = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            concurrentService.authenticate(new AdministratorSignInCmd(
                                    "admin@example.com",
                                    "wrong-password"));
                            return false;
                        } catch (AuthException exception) {
                            return true;
                        }
                    }))
                    .toList();
            assertThat(ready.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.get()).isTrue());
        }

        assertThat(failures).hasValue(5);
        verify(redisTemplate, times(5)).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
    }

    private void assertAuthenticationFailed(String login, String password) {
        assertThatThrownBy(() -> service.authenticate(new AdministratorSignInCmd(login, password)))
                .isInstanceOf(AuthException.class)
                .extracting("reason")
                .isEqualTo("账号或密码错误");
    }

    private static Administrator activeAdministrator() {
        return Administrator.builder()
                .id(new AdministratorId(1L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("strong-password"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }

    private static final class PlainPasswordCodec implements PasswordService {
        @Override
        public AdministratorPassword encrypt(AdministratorRawPassword password) {
            return new AdministratorPassword(password.value());
        }

        @Override
        public boolean verify(
                AdministratorRawPassword password,
                AdministratorPassword encryptedPassword
        ) {
            return password.value().equals(encryptedPassword.value());
        }

        @Override
        public void verifyUnknown(AdministratorRawPassword password) {
            // The test codec has no costly hash, but preserves the production contract.
        }
    }

    private static final class MemoryAdministratorRepository implements AdministratorRepository {
        private final Map<AdministratorId, Administrator> administrators = new LinkedHashMap<>();

        @Override
        public Optional<Administrator> find(AdministratorUsername username) {
            return administrators.values().stream()
                    .filter(administrator -> administrator.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public Optional<Administrator> find(AdministratorEmail email) {
            return administrators.values().stream()
                    .filter(administrator -> administrator.getEmail().equals(email))
                    .findFirst();
        }

        @Override
        public Optional<Administrator> find(AdministratorId administratorId) {
            return Optional.ofNullable(administrators.get(administratorId));
        }

        @Override
        public void save(Administrator administrator) {
            administrators.put(administrator.getId(), administrator);
        }

        @Override
        public void remove(Administrator administrator) {
            administrators.remove(administrator.getId());
        }

        @Override
        public com.co.kc.imchat.common.model.page.PagingResult<Administrator> page(
                com.co.kc.imchat.common.model.page.Paging paging
        ) {
            return new com.co.kc.imchat.common.model.page.PagingResult<>(
                    paging, List.copyOf(administrators.values()), (long) administrators.size());
        }

        @Override
        public boolean exists() {
            return !administrators.isEmpty();
        }

        void clear() {
            administrators.clear();
        }
    }
}
