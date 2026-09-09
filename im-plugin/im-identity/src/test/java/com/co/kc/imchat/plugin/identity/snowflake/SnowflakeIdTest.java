package com.co.kc.imchat.plugin.identity.snowflake;

import com.co.kc.imchat.plugin.identity.snowflake.impl.StaticSnowflakeMachineId;
import org.junit.jupiter.api.Test;

import static com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant.DATACENTER_BIT_SHIFT;
import static com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant.MACHINE_BIT_SHIFT;
import static com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant.MAX_DATACENTER;
import static com.co.kc.imchat.plugin.identity.constant.SnowflakeIdConstant.MAX_MACHINE;
import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdTest {

    @Test
    void encodesDataCenterAndMachineIntoTheirAssignedBits() {
        SnowflakeId snowflakeId = new SnowflakeId(new StaticSnowflakeMachineId(7L, 19L));

        Long value = snowflakeId.next();

        assertThat(value >> DATACENTER_BIT_SHIFT & MAX_DATACENTER).isEqualTo(7L);
        assertThat(value >> MACHINE_BIT_SHIFT & MAX_MACHINE).isEqualTo(19L);
    }

    @Test
    void generatesIncreasingValues() {
        SnowflakeId snowflakeId = new SnowflakeId(new StaticSnowflakeMachineId(1L, 1L));

        Long first = snowflakeId.next();
        Long second = snowflakeId.next();

        assertThat(second).isGreaterThan(first);
    }
}
