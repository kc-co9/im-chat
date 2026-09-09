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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthAuthorizationAppServiceBoundaryTest {

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
                null);
    }
}
