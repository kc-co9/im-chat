package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.application.ApplicationAppService;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationGetQuery;
import com.co.kc.imchat.management.iam.model.enums.ApplicationStatusEnum;
import com.co.kc.imchat.management.iam.model.io.ApplicationResponse;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.plugin.web.ImWebAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.APPLICATION_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationControllerTest {
    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            ImWebAutoConfiguration.class));

    @Test
    void getsAuthoritativeApplicationDetailWithWireLongSafeResponse() {
        long appId = 9_007_199_254_740_993L;
        ApplicationAppService appService = mock(ApplicationAppService.class);
        when(appService.get(new ApplicationGetQuery(appId)))
                .thenReturn(new ApplicationDTO(
                        appId,
                        "imAdmin",
                        "IM 管理后台",
                        AppStatus.ACTIVE));
        ApplicationController controller = new ApplicationController(appService);

        ApplicationResponse response = controller.applicationDetail(appId);

        assertThat(response).isEqualTo(new ApplicationResponse(
                appId,
                "imAdmin",
                "IM 管理后台",
                ApplicationStatusEnum.ACTIVE));
        verify(appService).get(new ApplicationGetQuery(appId));

        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.writeValueAsString(response))
                    .contains("\"appId\":\"9007199254740993\"");
        });
    }

    @Test
    void applicationDetailRequiresApplicationReadPermission() throws Exception {
        Method method = ApplicationController.class.getDeclaredMethod(
                "applicationDetail",
                Long.class);

        GetMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                method,
                GetMapping.class);
        RequiresPermission permission = AnnotatedElementUtils.findMergedAnnotation(
                method,
                RequiresPermission.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/applications/detail");
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(APPLICATION_READ);
    }
}
