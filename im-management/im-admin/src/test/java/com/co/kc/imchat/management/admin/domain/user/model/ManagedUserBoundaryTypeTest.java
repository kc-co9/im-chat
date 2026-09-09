package com.co.kc.imchat.management.admin.domain.user.model;

import com.co.kc.imchat.management.admin.model.io.ManagedUserResponse;
import com.co.kc.imchat.management.admin.model.enums.ManagedUserStatusEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedUserBoundaryTypeTest {

    @Test
    void deletedOutputRejectsMissingValuesInsteadOfUsingPrimitiveDefault() {
        assertNullableBoundaryRejected(ManagedUser.class,
                new ManagedUserId(1001L), new ManagedUserName("alice"),
                new ManagedUserEmail("alice@example.com"),
                ManagedUserStatus.NORMAL, true, Instant.EPOCH, Instant.EPOCH);
        assertNullableBoundaryRejected(ManagedUserResponse.class,
                1001L, "alice", "alice@example.com", ManagedUserStatusEnum.NORMAL,
                true, 0L, 0L);
    }

    private static void assertNullableBoundaryRejected(Class<?> type, Object... values) {
        RecordComponent[] components = type.getRecordComponents();
        int deletedIndex = java.util.stream.IntStream.range(0, components.length)
                .filter(index -> components[index].getName().equals("deleted"))
                .findFirst()
                .orElseThrow();
        assertThat(components[deletedIndex].getType()).isEqualTo(Boolean.class);

        Class<?>[] parameterTypes = java.util.Arrays.stream(components)
                .map(RecordComponent::getType)
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
