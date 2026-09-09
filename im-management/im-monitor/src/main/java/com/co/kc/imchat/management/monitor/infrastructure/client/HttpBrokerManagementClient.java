package com.co.kc.imchat.management.monitor.infrastructure.client;

import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.GossipRecordPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.MigrationRecordPayload;
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

    public HttpBrokerManagementClient(
            @Qualifier("brokerRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
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
    public List<MigrationRecordPayload> migrationRecords(BrokerManagementEndpoint endpoint, int limit) {
        return list(endpoint, "/management/migrations?limit=" + limit, new ParameterizedTypeReference<>() { });
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
}
