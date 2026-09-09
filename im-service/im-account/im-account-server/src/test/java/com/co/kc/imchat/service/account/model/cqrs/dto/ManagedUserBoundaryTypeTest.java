package com.co.kc.imchat.service.account.model.cqrs.dto;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedUserBoundaryTypeTest {

    @Test
    void deletedOutputRejectsMissingValuesInsteadOfUsingPrimitiveDefault() throws NoSuchFieldException {
        assertThat(ManagedUser.class.getDeclaredField("deleted").getType())
                .isEqualTo(Boolean.class);
        assertThatThrownBy(() -> new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                null,
                Instant.EPOCH,
                Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("managed user properties");
        assertNullableBoundaryRejected(ManagedUserDTO.class,
                1001L, "alice", "alice@example.com", UserStatus.NORMAL,
                true, Instant.EPOCH, Instant.EPOCH);
        assertNullableBoundaryRejected(ManagedUserListDTO.class,
                1001L, "alice", "alice@example.com", UserStatus.NORMAL,
                true, Instant.EPOCH, Instant.EPOCH);
    }

    private static void assertNullableBoundaryRejected(Class<?> type, Object... values) {
        java.lang.reflect.RecordComponent[] components = type.getRecordComponents();
        int deletedIndex = java.util.stream.IntStream.range(0, components.length)
                .filter(index -> components[index].getName().equals("deleted"))
                .findFirst()
                .orElseThrow();
        assertThat(components[deletedIndex].getType()).isEqualTo(Boolean.class);

        Class<?>[] parameterTypes = java.util.Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getType)
                .toArray(Class<?>[]::new);
        values[deletedIndex] = null;
        assertThatThrownBy(() -> type.getDeclaredConstructor(parameterTypes).newInstance(values))
                .isInstanceOfSatisfying(InvocationTargetException.class, exception ->
                        assertThat(exception.getCause())
                                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class,
                                        NullPointerException.class)
                                .hasMessageContaining("deleted"));
    }
}
