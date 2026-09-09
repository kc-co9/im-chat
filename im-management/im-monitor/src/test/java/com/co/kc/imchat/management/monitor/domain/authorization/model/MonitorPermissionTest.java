package com.co.kc.imchat.management.monitor.domain.authorization.model;

import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.management.monitor.interfaces.http.MonitorController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorPermissionTest {

    @Test
    void controllerUsesCatalogPermissionCodes() {
        Map<String, String> expectedPermissions = Map.of(
                "overview", MonitorPermission.Code.OVERVIEW_READ,
                "brokers", MonitorPermission.Code.BROKER_READ,
                "broker", MonitorPermission.Code.BROKER_READ,
                "gateways", MonitorPermission.Code.GATEWAY_READ,
                "connections", MonitorPermission.Code.CONNECTION_READ,
                "gossipRecords", MonitorPermission.Code.DIAGNOSTIC_READ,
                "migrations", MonitorPermission.Code.DIAGNOSTIC_READ);

        assertThat(MonitorController.class.getDeclaredMethods())
                .filteredOn(method -> expectedPermissions.containsKey(method.getName()))
                .allSatisfy(method -> assertThat(requiredPermission(method))
                        .isEqualTo(expectedPermissions.get(method.getName())));
    }

    private String requiredPermission(Method method) {
        return method.getAnnotation(RequiresPermission.class).value();
    }
}
