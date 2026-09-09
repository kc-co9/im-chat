package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationGetQuery;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationAppServiceTest {

    @Test
    void getsApplicationByItsBusinessId() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        AppId appId = new AppId(9_007_199_254_740_993L);
        Application application = new Application(
                appId,
                new AppKey("imAdmin"),
                new AppName("IM 管理后台"),
                AppStatus.ACTIVE);
        when(apps.find(appId)).thenReturn(Optional.of(application));
        ApplicationAppService service = new ApplicationAppService(
                apps,
                mock(SnowflakeId.class));

        ApplicationDTO result = service.get(
                new ApplicationGetQuery(appId.value()));

        assertThat(result).isEqualTo(new ApplicationDTO(
                appId.value(),
                "imAdmin",
                "IM 管理后台",
                AppStatus.ACTIVE));
    }

    @Test
    void missingApplicationProducesStableNotFoundError() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        AppId appId = new AppId(9_007_199_254_740_993L);
        when(apps.find(appId)).thenReturn(Optional.empty());
        ApplicationAppService service = new ApplicationAppService(
                apps,
                mock(SnowflakeId.class));

        assertThatThrownBy(() -> service.get(
                new ApplicationGetQuery(appId.value())))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(HttpErrorCode.NOT_FOUND.getCode());
                    assertThat(exception.getReason()).isEqualTo("应用不存在");
                });
    }

    @Test
    void registersApplicationWithoutCreatingOAuthClient() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        SnowflakeId snowflakeId = mock(SnowflakeId.class);
        when(snowflakeId.next()).thenReturn(1001L);
        ApplicationAppService service = new ApplicationAppService(apps, snowflakeId);

        service.register(new ApplicationRegisterCmd(
                "imAdmin",
                "IM 管理后台"));

        ArgumentCaptor<Application> appCaptor = ArgumentCaptor.forClass(Application.class);
        verify(apps).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getAppId()).isEqualTo(new AppId(1001L));
        assertThat(appCaptor.getValue().getAppKey()).isEqualTo(new AppKey("imAdmin"));
    }

    @Test
    void updatesApplicationNameAndStatusWithoutChangingAppKey() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        Application application = new Application(
                new AppId(1001L),
                new AppKey("imAdmin"),
                new AppName("旧名称"),
                AppStatus.ACTIVE);
        when(apps.find(new AppId(1001L))).thenReturn(Optional.of(application));
        ApplicationAppService service = new ApplicationAppService(apps, mock(SnowflakeId.class));

        service.update(new ApplicationUpdateCmd(1001L, "新名称", AppStatus.DISABLED));

        verify(apps).save(application);
        assertThat(application.getAppKey()).isEqualTo(new AppKey("imAdmin"));
        assertThat(application.getName().value()).isEqualTo("新名称");
        assertThat(application.getStatus())
                .isEqualTo(AppStatus.DISABLED);
    }
}
