package com.co.kc.imchat.management.ops.application;

import com.co.kc.imchat.management.ops.adapter.BrokerClusterOperationAdapter;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerClusterOperationAppServiceTest {
    private final BrokerClusterOperationAdapter adapter = mock(BrokerClusterOperationAdapter.class);
    private final BrokerClusterOperationAppService service =
            new BrokerClusterOperationAppService(adapter);

    @Test
    void retriesLeaderEndpointOnceWithSameOperationId() {
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "activate", "operation-1", 7L, null, null, "scope");
        when(adapter.operate(command)).thenReturn(
                new BrokerOperationDTO("operation-1", "NOT_LEADER", 7L,
                        null, "http://broker-2:19020", "not leader"));
        URI leader = URI.create("http://broker-2:19020");
        when(adapter.operateLeader(leader, command)).thenReturn(
                new BrokerOperationDTO("operation-1", "APPLIED", 7L,
                        8L, null, null));

        BrokerOperationDTO result = service.operate(command);

        assertThat(result.status()).isEqualTo("APPLIED");
        verify(adapter).operateLeader(leader, command);
    }
}
