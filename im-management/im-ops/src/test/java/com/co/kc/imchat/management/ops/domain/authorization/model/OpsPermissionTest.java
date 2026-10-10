package com.co.kc.imchat.management.ops.domain.authorization.model;

import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.management.ops.interfaces.http.OpsController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpsPermissionTest {

    @Test
    void controllerUsesCatalogPermissionCodes() {
        Map<String, String> expectedPermissions = Map.of(
                "overview", OpsPermission.Code.OVERVIEW_READ,
                "brokers", OpsPermission.Code.BROKER_READ,
                "broker", OpsPermission.Code.BROKER_READ,
                "gateways", OpsPermission.Code.GATEWAY_READ,
                "connections", OpsPermission.Code.CONNECTION_READ,
                "gossipRecords", OpsPermission.Code.DIAGNOSTIC_READ,
                "migrations", OpsPermission.Code.DIAGNOSTIC_READ);

        assertThat(OpsController.class.getDeclaredMethods())
                .filteredOn(method -> expectedPermissions.containsKey(method.getName()))
                .allSatisfy(method -> assertThat(requiredPermission(method))
                        .isEqualTo(expectedPermissions.get(method.getName())));
    }

    private String requiredPermission(Method method) {
        return method.getAnnotation(RequiresPermission.class).value();
    }
}
