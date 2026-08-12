package com.co.kc.imchat.gateway.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpGatewayEndpointConfigTest {

    @Test
    void applicationYmlDefinesBusinessRoutes() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        Properties properties = Binder.get(environment)
                .bind("spring.cloud.gateway.server.webflux.routes", Properties.class)
                .orElseGet(Properties::new);

        List<String> routeIds = properties.stringPropertyNames().stream()
                .filter(name -> name.endsWith(".id"))
                .map(properties::getProperty)
                .sorted()
                .toList();

        assertEquals(18080, environment.getProperty("server.port", Integer.class));
        assertEquals(List.of("im-account", "im-message", "im-social"), routeIds);
        assertEquals("optional:nacos:im-http-gateway.yml?group=INFRA_GROUP",
                environment.getProperty("spring.config.import[0]"));

        List<String> routeUris = properties.stringPropertyNames().stream()
                .filter(name -> name.endsWith(".uri"))
                .map(properties::getProperty)
                .sorted()
                .toList();

        assertEquals(List.of("lb://im-account", "lb://im-message", "lb://im-social"), routeUris);

        assertEquals(
                "Path=/user/**",
                environment.getProperty("spring.cloud.gateway.server.webflux.routes[0].predicates[0]")
        );
        assertEquals(
                "Path=/im/chat/**,/im/private/**,/im/group/queryMessageDetail,/im/group/queryHistoryMessage",
                environment.getProperty("spring.cloud.gateway.server.webflux.routes[1].predicates[0]")
        );
        assertEquals(
                "Path=/friend/**,/im/group/createGroup,/im/group/inviteGroupMembers,"
                        + "/im/group/dismissGroup,/im/group/kickGroupMember,/im/group/leaveGroup,"
                        + "/im/group/transferGroupOwner,/im/group/changeGroupNotification,"
                        + "/im/group/changeGroupMemberAlias,/im/group/getGroupList,/im/group/getGroupDetail",
                environment.getProperty("spring.cloud.gateway.server.webflux.routes[2].predicates[0]")
        );

        boolean exposesInternalRoute = properties.stringPropertyNames().stream()
                .filter(name -> name.contains(".predicates"))
                .map(properties::getProperty)
                .anyMatch(predicate -> predicate.contains("/internal/**"));

        assertFalse(exposesInternalRoute);
    }
}
