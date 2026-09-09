package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationGetQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPageQuery;
import com.co.kc.imchat.management.iam.transformer.application.ApplicationAppTransformer;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import lombok.RequiredArgsConstructor;

/**
 * IAM 注册应用管理服务。
 */
@RequiredArgsConstructor
public class ApplicationAppService {
    private final ApplicationRepository applicationRepository;
    private final SnowflakeId snowflakeId;

    public ApplicationDTO get(ApplicationGetQuery query) {
        AppId appId = new AppId(query.appId());
        Application application = applicationRepository.find(appId)
                .orElseThrow(() -> new NotFoundException("应用不存在"));

        return ApplicationAppTransformer.INSTANCE.applicationDtoFrom(application);
    }

    public PagingResult<ApplicationDTO> page(ApplicationPageQuery query) {
        return applicationRepository.page(query.paging())
                .map(ApplicationAppTransformer.INSTANCE::applicationDtoFrom);
    }

    public ApplicationDTO register(ApplicationRegisterCmd command) {
        AppKey appKey = new AppKey(command.appKey());
        AppName appName = new AppName(command.name());
        if (applicationRepository.contains(appKey)) {
            throw new RepeatException("appKey 已存在");
        }

        AppId appId = new AppId(snowflakeId.next());
        Application application = new Application(appId, appKey, appName, AppStatus.ACTIVE);
        applicationRepository.save(application);

        return ApplicationAppTransformer.INSTANCE.applicationDtoFrom(application);
    }

    public ApplicationDTO update(ApplicationUpdateCmd command) {
        AppId appId = new AppId(command.appId());

        Application application = applicationRepository.find(appId)
                .orElseThrow(() -> new NotFoundException("应用不存在"));

        application.rename(new AppName(command.name()));
        application.changeStatus(command.status());
        applicationRepository.save(application);

        return ApplicationAppTransformer.INSTANCE.applicationDtoFrom(application);
    }
}
