package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.transformer.application.OAuthAuthorizationAppTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthCredentialDTO;
import org.mockito.ArgumentCaptor;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthAuthorizationAppServiceBoundaryTest {

    @Test
    void revokedSaveCommandRevokesExistingAuthorization() {
        OAuthAuthorization existing = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-id"))
                .appId(new AppId(1L)).clientId(new OAuthClientId("oauth-client-id"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "principal"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS)
                .scopes(Set.of(new OAuthScope("audit:ingest")))
                .status(OAuthAuthorizationStatus.ACTIVE).build();
        existing.setRowVersion(3L);
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(any(OAuthAuthorizationId.class))).thenReturn(Optional.of(existing));
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));
        Instant revokedAt = Instant.parse("2026-10-30T12:00:00Z");
        service.save(new OAuthAuthorizationSaveCmd("authorization-id", "oauth-client-id", OAuthPrincipalType.CLIENT,
                "principal", OAuthGrantType.CLIENT_CREDENTIALS, null, null, null, null, null,
                Set.of("audit:ingest"), OAuthAuthorizationStatus.REVOKED, revokedAt, 3L));
        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getRevokedAt()).isEqualTo(revokedAt);
        verify(repository).save(existing);
    }

    @Test
    void saveCommandDoesNotCarryDomainAggregate() {
        assertThat(Arrays.stream(OAuthAuthorizationSaveCmd.class.getRecordComponents())
                .noneMatch(component -> component.getType() == OAuthAuthorization.class))
                .isTrue();
    }

    @Test
    void saveCommandCarriesWriteStateDirectly() {
        assertThat(Arrays.stream(OAuthAuthorizationSaveCmd.class.getRecordComponents())
                .map(component -> component.getName()))
                .contains("authorizationId", "oauthClientId")
                .doesNotContain("authorization", "appId", "credentialType", "credentialDigest");
        assertThat(Arrays.stream(OAuthAuthorizationSaveCmd.class.getRecordComponents())
                .noneMatch(component -> component.getType() == OAuthAuthorizationDTO.class))
                .isTrue();
    }

    @Test
    void applicationServiceUsesOneFinalStateSaveUseCase() {
        assertThat(Arrays.stream(OAuthAuthorizationAppService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .contains("save")
                .doesNotContain("exchange", "refresh");
    }

    @Test
    void authorizationRepositoryDoesNotExposeCredentialBusinessActions() {
        assertThat(Arrays.stream(OAuthAuthorizationRepository.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain(
                        "consumeAuthorizationCode",
                        "rotateRefreshToken",
                        "findByRotatedRefreshToken",
                        "revoke");
    }

    @Test
    void saveCommandDeterminesPrincipalTypeFromGrantType() {
        OAuthAuthorizationSaveCmd clientCommand = saveCommand(OAuthGrantType.CLIENT_CREDENTIALS);
        OAuthAuthorizationSaveCmd administratorCommand = saveCommand(OAuthGrantType.AUTHORIZATION_CODE);

        assertThat(clientCommand.principalType()).isEqualTo(OAuthPrincipalType.CLIENT);
        assertThat(administratorCommand.principalType()).isEqualTo(OAuthPrincipalType.ADMINISTRATOR);
    }

    @Test
    void publicUseCasesDoNotReturnDomainAggregate() {
        assertThat(Arrays.stream(OAuthAuthorizationAppService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getGenericReturnType)
                .noneMatch(this::containsOAuthAuthorization))
                .isTrue();
    }

    @Test
    void credentialLookupUsesQueriesAndSpringOwnsCredentialValidation() {
        assertThat(Arrays.stream(OAuthAuthorizationAppService.class.getDeclaredMethods())
                .map(Method::getName))
                .contains(
                        "queryById",
                        "queryByToken")
                .doesNotContain(
                        "revokeIfReplayed",
                        "revokeIfCodeReused",
                        "revokeIfRefreshTokenReused")
                .doesNotContain("authenticate", "findByToken");
    }

    @Test
    void applicationTransformerDoesNotProduceDomainObjects() {
        assertThat(Arrays.stream(OAuthAuthorizationAppTransformer.class.getDeclaredMethods())
                .map(Method::getGenericReturnType)
                .noneMatch(this::containsDomainType))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidExistingVersions")
    void invalidExistingSnapshotRejectsWithoutMutation(
            OAuthAuthorizationStatus currentStatus, OAuthAuthorizationStatus requestedStatus, Long expectedVersion
    ) {
        OAuthAuthorization existing = existingAuthorization(currentStatus);
        OAuthAuthorizationDTO before = OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(existing);
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));

        assertThatThrownBy(() -> service.save(versionedCommand(requestedStatus, expectedVersion)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining(currentStatus == OAuthAuthorizationStatus.REVOKED
                        ? "OAuth 授权已撤销" : "OAuth 授权版本已失效");

        assertThat(OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(existing)).isEqualTo(before);
        verify(repository, never()).save(any());
    }

    private static Stream<Arguments> invalidExistingVersions() {
        return Stream.of(
                Arguments.of(OAuthAuthorizationStatus.ACTIVE, OAuthAuthorizationStatus.ACTIVE, null),
                Arguments.of(OAuthAuthorizationStatus.ACTIVE, OAuthAuthorizationStatus.ACTIVE, 2L),
                Arguments.of(OAuthAuthorizationStatus.ACTIVE, OAuthAuthorizationStatus.REVOKED, null),
                Arguments.of(OAuthAuthorizationStatus.ACTIVE, OAuthAuthorizationStatus.REVOKED, 2L),
                Arguments.of(OAuthAuthorizationStatus.REVOKED, OAuthAuthorizationStatus.ACTIVE, 3L),
                Arguments.of(OAuthAuthorizationStatus.REVOKED, OAuthAuthorizationStatus.ACTIVE, 2L));
    }

    @Test
    void versionedMissingRowRejectsInsteadOfInserting() {
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(new OAuthAuthorizationId("authorization-id"))).thenReturn(Optional.empty());
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository, clients,
                mock(OAuthAuthorizationService.class));

        assertThatThrownBy(() -> service.save(versionedCommand(OAuthAuthorizationStatus.ACTIVE, 3L)))
                .isInstanceOf(AuthException.class).hasMessageContaining("OAuth 授权已不存在");

        verify(repository, never()).save(any());
        verifyNoInteractions(clients);
    }

    @Test
    void matchingVersionSavesReplacementAndPreservesOriginalCommandVersion() {
        OAuthAuthorization existing = existingAuthorization(OAuthAuthorizationStatus.ACTIVE);
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));
        OAuthAuthorizationSaveCmd command = versionedCommand(OAuthAuthorizationStatus.ACTIVE, 3L);

        service.save(command);

        assertThat(existing.getAccessToken().digest().value()).isEqualTo("replacement-digest");
        assertThat(existing.isRevoked()).isFalse();
        assertThat(command.expectedRowVersion()).isEqualTo(3L);
        verify(repository).save(existing);
    }

    @ParameterizedTest
    @MethodSource("duplicateRevokeVersions")
    void duplicateTerminalRevokeIsReadOnly(Long expectedVersion) {
        OAuthAuthorization existing = existingAuthorization(OAuthAuthorizationStatus.REVOKED);
        OAuthAuthorizationDTO before = OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(existing);
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));

        service.save(versionedCommand(OAuthAuthorizationStatus.REVOKED, expectedVersion));

        assertThat(OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(existing)).isEqualTo(before);
        assertThat(existing.getRowVersion()).isEqualTo(3L);
        assertThat(existing.getAccessToken().digest().value()).isEqualTo("original-digest");
        assertThat(existing.getRevokedAt()).isEqualTo(Instant.parse("2026-10-30T11:00:00Z"));
        verify(repository, never()).save(any());
    }

    private static Stream<Arguments> duplicateRevokeVersions() {
        return Stream.of(Arguments.of((Long) null), Arguments.of(2L), Arguments.of(3L));
    }

    @Test
    void newAuthorizationWithNullVersionIsInserted() {
        OAuthAuthorizationRepository repository = mock(OAuthAuthorizationRepository.class);
        when(repository.findForUpdate(new OAuthAuthorizationId("authorization-id"))).thenReturn(Optional.empty());
        OAuthClient client = OAuthClient.builder()
                .clientId(new OAuthClientId("oauth-client-id")).appId(new AppId(1L)).audienceAppId(new AppId(1L))
                .name(new OAuthClientName("test-client")).clientSecret(new OAuthClientSecret("secret-digest"))
                .grantTypes(Set.of(OAuthGrantType.CLIENT_CREDENTIALS)).scopes(Set.of(new OAuthScope("audit:ingest")))
                .redirectUris(Set.of()).postLogoutRedirectUris(Set.of()).status(OAuthClientStatus.ACTIVE).build();
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        when(clients.find(client.getClientId())).thenReturn(Optional.of(client));
        OAuthAuthorizationService domainService = mock(OAuthAuthorizationService.class);
        OAuthPrincipal principal = new OAuthPrincipal(OAuthPrincipalType.CLIENT, "principal");
        when(domainService.principal(principal, client)).thenReturn(principal);
        OAuthAuthorizationAppService service = new OAuthAuthorizationAppService(repository, clients, domainService);

        service.save(versionedCommand(OAuthAuthorizationStatus.ACTIVE, null));

        ArgumentCaptor<OAuthAuthorization> saved = ArgumentCaptor.forClass(OAuthAuthorization.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new OAuthAuthorizationId("authorization-id"));
        assertThat(saved.getValue().getRowVersion()).isZero();
        assertThat(saved.getValue().getAccessToken().digest().value()).isEqualTo("replacement-digest");
        assertThat(saved.getValue().getStatus()).isEqualTo(OAuthAuthorizationStatus.ACTIVE);
    }

    private OAuthAuthorization existingAuthorization(OAuthAuthorizationStatus status) {
        Instant issuedAt = Instant.parse("2026-10-30T10:00:00Z");
        OAuthAuthorization authorization = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-id"))
                .appId(new AppId(1L)).clientId(new OAuthClientId("oauth-client-id"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "principal"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS)
                .accessToken(new OAuthAccessToken(new OAuthCredentialDigest("original-digest"),
                        new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(3600)), null))
                .scopes(Set.of(new OAuthScope("audit:ingest"))).status(status)
                .revokedAt(status == OAuthAuthorizationStatus.REVOKED ? issuedAt.plusSeconds(3600) : null).build();
        authorization.setRowVersion(3L);
        return authorization;
    }

    private OAuthAuthorizationSaveCmd versionedCommand(OAuthAuthorizationStatus status, Long version) {
        Instant issuedAt = Instant.parse("2026-10-30T12:00:00Z");
        return new OAuthAuthorizationSaveCmd("authorization-id", "oauth-client-id", OAuthPrincipalType.CLIENT,
                "principal", OAuthGrantType.CLIENT_CREDENTIALS, null, null,
                new OAuthCredentialDTO("replacement-digest", issuedAt, issuedAt.plusSeconds(3600), null, null),
                null, null, Set.of("audit:ingest"), status,
                status == OAuthAuthorizationStatus.REVOKED ? issuedAt : null, version);
    }

    private boolean containsOAuthAuthorization(Type type) {
        if (type == OAuthAuthorization.class) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(this::containsOAuthAuthorization);
        }
        return false;
    }

    private boolean containsDomainType(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass.getPackageName().startsWith("com.co.kc.imchat.management.iam.domain");
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(this::containsDomainType);
        }
        return false;
    }

    private OAuthAuthorizationSaveCmd saveCommand(OAuthGrantType grantType) {
        return new OAuthAuthorizationSaveCmd(
                "authorization-id",
                "oauth-client-id",
                null,
                "principal",
                grantType,
                null,
                null,
                null,
                null,
                null,
                null,
                OAuthAuthorizationStatus.ACTIVE,
                null,
                null);
    }
}
