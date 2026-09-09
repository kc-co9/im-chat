package com.co.kc.imchat.plugin.identity.snowflake.impl;

import com.co.kc.imchat.common.exception.ExhaustionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSnowflakeMachineIdTest {

    @Test
    void allocatesTheFirstAvailableMachineAndReleasesItOnClose() {
        RScript script = script();
        when(execute(script)).thenReturn(false, false, true, true);
        RedisSnowflakeMachineId machineId = machineId(script);

        assertThatThrownBy(machineId::getMachineId)
                .isInstanceOf(IllegalStateException.class);

        machineId.start();

        assertThat(machineId.getDataCenterId()).isEqualTo(7L);
        assertThat(machineId.getMachineId()).isEqualTo(2L);

        machineId.close();

        assertThatThrownBy(machineId::getMachineId)
                .isInstanceOf(IllegalStateException.class);
        verify(script, org.mockito.Mockito.atLeast(4)).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.BOOLEAN),
                anyList(),
                any(Object[].class));
    }

    @Test
    void failsWhenEveryMachineSlotIsOccupied() {
        RScript script = script();
        when(execute(script)).thenReturn(false);
        RedisSnowflakeMachineId machineId = machineId(script);

        assertThatThrownBy(machineId::start)
                .isInstanceOf(ExhaustionException.class);

        machineId.close();
    }

    @Test
    void reallocatesAfterOwnershipIsLost() {
        RScript script = script();
        when(execute(script)).thenReturn(true, false, false, true);
        RedisSnowflakeMachineId machineId = machineId(script);
        machineId.start();

        machineId.renewLease();

        assertThat(machineId.getMachineId()).isEqualTo(1L);
        machineId.close();
    }

    @Test
    void invalidatesMachineIdWhenRedisCannotRenewTheLease() {
        RScript script = script();
        when(execute(script)).thenReturn(true).thenThrow(new RedisException("unavailable"));
        RedisSnowflakeMachineId machineId = machineId(script);
        machineId.start();

        machineId.renewLease();

        assertThatThrownBy(machineId::getMachineId)
                .isInstanceOf(IllegalStateException.class);
        machineId.close();
    }

    @Test
    void rejectsMachineIdAfterTheLocalLeaseDeadline() {
        RScript script = script();
        when(execute(script)).thenReturn(true);
        RedisSnowflakeMachineId machineId = machineId(script);
        machineId.start();
        ReflectionTestUtils.setField(machineId, "leaseDeadlineNanos", System.nanoTime() - 1L);

        assertThatThrownBy(machineId::getMachineId)
                .isInstanceOf(IllegalStateException.class);

        machineId.close();
    }

    @Test
    void embedsLeaseSecondsInAllocationScriptInsteadOfUsingCodecEncodedArgument() {
        RScript script = script();
        when(execute(script)).thenReturn(false, true, true);
        RedisSnowflakeMachineId machineId = machineId(script);

        machineId.start();

        ArgumentCaptor<String> scripts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(script, org.mockito.Mockito.atLeastOnce()).eval(
                eq(RScript.Mode.READ_WRITE),
                scripts.capture(),
                eq(RScript.ReturnType.BOOLEAN),
                anyList(),
                arguments.capture());
        assertThat(scripts.getAllValues().getFirst())
                .contains(">= 7200")
                .doesNotContain("tonumber(ARGV[3])");
        assertThat(List.of(arguments.getAllValues().getFirst())).hasSize(2);

        machineId.close();
    }

    private RedisSnowflakeMachineId machineId(RScript script) {
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.getScript()).thenReturn(script);
        return new RedisSnowflakeMachineId(
                redissonClient,
                7L,
                "iam",
                Duration.ofHours(2),
                Duration.ofHours(1));
    }

    private RScript script() {
        return mock(RScript.class);
    }

    @SuppressWarnings("unchecked")
    private Boolean execute(RScript script) {
        return script.eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.BOOLEAN),
                anyList(),
                any(Object[].class));
    }
}
