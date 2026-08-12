package com.co.kc.imchat.plugin.nacos.listener;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class NonWebNacosRegistrationListenerTest {

    private final NacosAutoServiceRegistration registration = mock(NacosAutoServiceRegistration.class);
    private final NacosRegistration nacosRegistration = mock(NacosRegistration.class);
    private final NonWebNacosRegistrationListener listener =
            new NonWebNacosRegistrationListener(registration, nacosRegistration);

    NonWebNacosRegistrationListenerTest() {
        when(nacosRegistration.getPort()).thenReturn(12200);
    }

    @Test
    void startsRegistrationForNonWebApplication() {
        ApplicationStartedEvent event = event(mock(ConfigurableApplicationContext.class));

        listener.onApplicationEvent(event);

        verify(registration).start();
    }

    @Test
    void ignoresWebApplication() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class,
                withSettings().extraInterfaces(WebServerApplicationContext.class));
        ApplicationStartedEvent event = event(context);

        listener.onApplicationEvent(event);

        verify(registration, never()).start();
    }

    @Test
    void ignoresRegistrationThatIsAlreadyRunning() {
        when(registration.isRunning()).thenReturn(true);
        ApplicationStartedEvent event = event(mock(ConfigurableApplicationContext.class));

        listener.onApplicationEvent(event);

        verify(registration, never()).start();
    }

    @Test
    void rejectsNonWebApplicationWithoutDiscoveryPort() {
        when(nacosRegistration.getPort()).thenReturn(-1);
        ApplicationStartedEvent event = event(mock(ConfigurableApplicationContext.class));

        assertThatThrownBy(() -> listener.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.cloud.nacos.discovery.port");

        verify(registration, never()).start();
    }

    private ApplicationStartedEvent event(ConfigurableApplicationContext context) {
        ApplicationStartedEvent event = mock(ApplicationStartedEvent.class);
        when(event.getApplicationContext()).thenReturn(context);
        return event;
    }
}
