package com.co.kc.imchat.service.account.admin.facade;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.params.UserBanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserDeleteParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPasswordResetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUnbanParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserUpdateParams;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;
import com.co.kc.imchat.service.account.admin.facade.params.UserGetParams;
import com.co.kc.imchat.service.account.admin.facade.params.UserPageParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccountAdminContractTest {

    @Test
    void pageQueryReusesPagingAndNormalizesOptionalText() {
        Paging paging = new Paging(2, 20);
        UserPageParams query = new UserPageParams(
                paging, null, "  alice  ", "  ", null);

        assertThat(query.paging()).isSameAs(paging);
        assertThat(query.username()).isEqualTo("alice");
        assertThat(query.email()).isNull();
    }

    @Test
    void pageQueryRejectsMissingPaging() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserPageParams(null, null, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserPageParams(
                        new Paging(1, 101), null, null, null, null));
    }

    @Test
    void userStateContainsManagementProjectionStates() {
        assertThat(AccountUserStatus.values())
                .containsExactly(AccountUserStatus.NORMAL, AccountUserStatus.BANNED);
    }

    @Test
    void publicContractsAreSerializable() throws Exception {
        AccountUserDTO user = new AccountUserDTO(
                1001L, "alice", "alice@example.com", AccountUserStatus.NORMAL,
                false,
                Instant.parse("2026-08-24T00:00:00Z"),
                Instant.parse("2026-08-24T01:00:00Z"));
        AccountUserListDTO listUser = new AccountUserListDTO(
                1001L, "alice", "alice@example.com", AccountUserStatus.NORMAL,
                false,
                Instant.parse("2026-08-24T00:00:00Z"),
                Instant.parse("2026-08-24T01:00:00Z"));
        List<Serializable> contracts = List.of(
                new UserPageParams(new Paging(1, 20), 1001L,
                        null, null, AccountUserStatus.NORMAL),
                new UserGetParams(1001L),
                new UserUpdateParams(1001L, "alice", "alice@example.com"),
                new UserPasswordResetParams(1001L, "new-password-123"),
                new UserBanParams(1001L),
                new UserUnbanParams(1001L),
                new UserDeleteParams(1001L),
                user,
                new PagingResult<>(new Paging(1, 20), List.of(listUser), 1L));

        for (Serializable contract : contracts) {
            assertThat(roundTrip(contract)).isEqualTo(contract);
        }
    }

    @Test
    void responseContractsDoNotExposeCredentialsOrSessionFacts() {
        Set<String> forbiddenFragments = Set.of(
                "password", "hash", "token", "cookie", "session", "credential", "fingerprint");

        for (Class<?> type : List.of(AccountUserDTO.class, AccountUserListDTO.class)) {
            Set<String> componentNames = java.util.Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            assertThat(componentNames)
                    .allSatisfy(name -> assertThat(forbiddenFragments)
                            .noneMatch(name::contains));
        }
    }

    @Test
    void identifierAndRequiredCommandValuesAreValidated() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserGetParams(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserGetParams(0L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserUpdateParams(null, "alice", "alice@example.com"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserUpdateParams(1L, " ", "alice@example.com"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserPasswordResetParams(1L, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserBanParams(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserUnbanParams(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new UserDeleteParams(null));
    }

    @Test
    void deletedOutputRejectsMissingValueInsteadOfUsingPrimitiveDefault() {
        RecordComponent[] components = AccountUserDTO.class.getRecordComponents();
        int deletedIndex = java.util.stream.IntStream.range(0, components.length)
                .filter(index -> components[index].getName().equals("deleted"))
                .findFirst()
                .orElseThrow();
        assertThat(components[deletedIndex].getType()).isEqualTo(Boolean.class);

        Class<?>[] parameterTypes = java.util.Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Object[] values = {
                1001L, "alice", "alice@example.com", AccountUserStatus.NORMAL,
                null, Instant.EPOCH, Instant.EPOCH
        };
        assertThatThrownBy(() -> AccountUserDTO.class.getDeclaredConstructor(parameterTypes)
                .newInstance(values))
                .isInstanceOfSatisfying(InvocationTargetException.class, exception ->
                        assertThat(exception.getCause())
                                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                                .hasMessageContaining("deleted"));
    }

    @Test
    void facadeDtoAndParamsDoNotExposePrimitiveComponents() {
        List<Class<?>> contractTypes = List.of(
                AccountUserDTO.class,
                AccountUserListDTO.class,
                UserPageParams.class,
                UserGetParams.class,
                UserUpdateParams.class,
                UserPasswordResetParams.class,
                UserBanParams.class,
                UserUnbanParams.class,
                UserDeleteParams.class);

        for (Class<?> contractType : contractTypes) {
            assertThat(contractType.getRecordComponents())
                    .allSatisfy(component -> assertThat(component.getType().isPrimitive())
                            .as("%s.%s", contractType.getSimpleName(), component.getName())
                            .isFalse());
        }
    }

    private static Object roundTrip(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}
