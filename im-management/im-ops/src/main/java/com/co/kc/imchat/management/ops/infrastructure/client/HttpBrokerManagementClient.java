package com.co.kc.imchat.management.ops.infrastructure.client;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GossipRecordPayload;
import com.co.kc.imchat.common.constant.HttpResultCode;
import com.co.kc.imchat.common.model.io.HttpResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

/** 基于 Spring RestClient 的 Broker 管理接口客户端。 */
@Component
public class HttpBrokerManagementClient implements BrokerManagementClient {
    private static final int MAX_ERROR_LENGTH = 256;
    private final RestClient restClient;
    private final OpsBrokerProperties properties;

    public HttpBrokerManagementClient(
            @Qualifier("brokerRestClient") RestClient restClient,
            OpsBrokerProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public BrokerOverviewPayload overview(BrokerManagementEndpoint endpoint) {
        HttpResult<BrokerOverviewPayload> response = call(() -> restClient.get()
                .uri(uri(endpoint, "/management/broker/overview"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { }));
        return data(response);
    }

    @Override
    public List<BrokerNodePayload> brokers(BrokerManagementEndpoint endpoint) {
        return list(endpoint, "/management/brokers", new ParameterizedTypeReference<>() { });
    }

    @Override
    public List<GatewayNodePayload> gateways(BrokerManagementEndpoint endpoint) {
        return list(endpoint, "/management/gateways", new ParameterizedTypeReference<>() { });
    }

    @Override
    public List<ConnectionRoutePayload> connections(BrokerManagementEndpoint endpoint, Long userId) {
        return list(endpoint, "/management/connections?userId=" + userId, new ParameterizedTypeReference<>() { });
    }

    @Override
    public List<GossipRecordPayload> gossipRecords(BrokerManagementEndpoint endpoint, int limit) {
        return list(endpoint, "/management/gossip/records?limit=" + limit, new ParameterizedTypeReference<>() { });
    }

    @Override
    public BrokerOperationPayload operate(BrokerManagementEndpoint endpoint,
                                          BrokerClusterOperationCommand command,
                                          String scope) {
        HttpResult<BrokerOperationPayload> response = call(() -> restClient.post()
                .uri(uri(endpoint, "/management/cluster-state/brokers/"
                        + command.brokerId() + "/" + command.action()))
                .header("Authorization", "Bearer " + properties.getManagementToken())
                .header("X-Im-Audience", properties.getManagementAudience())
                .header("X-Im-Scopes", scope)
                .header("Idempotency-Key", command.operationId())
                .header("If-Match-Epoch", Long.toString(command.expectedEpoch()))
                .body(new OperationBody(command.membershipEndpoint(), command.reason()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { }));
        return data(response);
    }

    @Override
    public BrokerClusterStatePayload clusterState(BrokerManagementEndpoint endpoint) {
        HttpResult<BrokerClusterStatePayload> response = call(() -> restClient.get()
                .uri(uri(endpoint, "/management/cluster-state"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { }));
        return data(response);
    }

    private <T> List<T> list(
            BrokerManagementEndpoint endpoint,
            String path,
            ParameterizedTypeReference<HttpResult<List<T>>> type
    ) {
        HttpResult<List<T>> response = call(() -> restClient.get()
                .uri(uri(endpoint, path))
                .retrieve()
                .body(type));
        return data(response);
    }

    private <T> T call(Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new RestClientException("Broker returned an empty response");
            }
            return response;
        } catch (RestClientException exception) {
            String message = exception.getMessage() == null ? "Broker request failed" : exception.getMessage();
            throw new BrokerManagementClientException(
                    message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)), exception);
        }
    }

    private <T> T data(HttpResult<T> response) {
        if (response.getCode() == null
                || !response.getCode().equals(HttpResultCode.SUCCESS.getCode())
                || response.getData() == null) {
            String message = response.getMsg() == null
                    ? "Broker returned an unsuccessful response"
                    : response.getMsg();
            throw new BrokerManagementClientException(message, null);
        }
        return response.getData();
    }

    private URI uri(BrokerManagementEndpoint endpoint, String path) {
        if (!endpoint.isSupported()) {
            throw new BrokerManagementClientException(endpoint.errorSummary(), null);
        }
        return endpoint.baseUri().resolve(path);
    }

    private record OperationBody(String membershipEndpoint, String reason) {
    }
}
