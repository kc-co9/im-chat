package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAppService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlApplicationRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbIamApp.class);
    }

    @Test
    void checksApplicationKeyIdentity() {
        DbIamAppService service = mock(DbIamAppService.class);
        when(service.count(any(Wrapper.class))).thenReturn(1L);
        MysqlApplicationRepository repository = new MysqlApplicationRepository(service);

        assertThat(repository.contains(new AppKey("imAdmin"))).isTrue();

        verify(service).count(any(Wrapper.class));
    }

    @Test
    void insertsAnApplication() {
        DbIamAppService service = mock(DbIamAppService.class);
        Application application = new Application(new AppId(1L), new AppKey("imAdmin"),
                new AppName("IM Admin"), AppStatus.ACTIVE);
        DbIamApp row = new DbIamApp();
        row.setId(100L);
        doAnswer(invocation -> {
            ((DbIamApp) invocation.getArgument(0)).setId(100L);
            return true;
        }).when(service).save(any(DbIamApp.class));
        MysqlApplicationRepository repository = new MysqlApplicationRepository(service);

        repository.save(application);

        verify(service).save(any(DbIamApp.class));
    }

    @Test
    void restoresApplicationTechnicalPrimaryKey() {
        DbIamAppService service = mock(DbIamAppService.class);
        DbIamApp row = new DbIamApp();
        row.setId(100L);
        row.setAppId(1L);
        row.setAppKey("imAdmin");
        row.setName("IM Admin");
        row.setStatus(com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamAppStatus.ACTIVE);
        when(service.getOne(any(Wrapper.class), any(Boolean.class))).thenReturn(row);
        MysqlApplicationRepository repository = new MysqlApplicationRepository(service);

        assertThat(repository.find(new AppId(1L)))
                .get()
                .extracting(Application::getPkId)
                .isEqualTo(100L);
    }

    @Test
    void findsRequestedApplicationsInOneBoundedLookup() {
        DbIamAppService service = mock(DbIamAppService.class);
        when(service.list(any(Wrapper.class))).thenReturn(List.of(
                dbApplication(100L, 1L, "imAdmin"),
                dbApplication(200L, 2L, "imAudit")));
        MysqlApplicationRepository repository = new MysqlApplicationRepository(service);

        List<Application> result = repository.find(Set.of(new AppId(1L), new AppId(2L)));

        assertThat(result)
                .extracting(application -> application.getAppId().value())
                .containsExactly(1L, 2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DbIamApp>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).list(wrapperCaptor.capture());
        wrapperCaptor.getValue().getSqlSegment();
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private static DbIamApp dbApplication(Long id, Long appId, String appKey) {
        DbIamApp application = new DbIamApp();
        application.setId(id);
        application.setAppId(appId);
        application.setAppKey(appKey);
        application.setName(appKey);
        application.setStatus(
                com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamAppStatus.ACTIVE);
        return application;
    }
}
