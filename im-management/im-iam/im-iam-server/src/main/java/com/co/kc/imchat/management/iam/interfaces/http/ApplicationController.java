package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.*;
import com.co.kc.imchat.management.iam.application.ApplicationAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationGetQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPageQuery;
import com.co.kc.imchat.management.iam.model.io.*;
import com.co.kc.imchat.management.iam.transformer.interfaces.ApplicationHttpTransformer;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.*;

/**
 * IAM 接入应用 HTTP 接口。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationAppService applicationAppService;

    @GetMapping("/applications/detail")
    @RequiresPermission(APPLICATION_READ)
    public ApplicationResponse applicationDetail(@RequestParam Long appId) {
        ApplicationGetQuery query = new ApplicationGetQuery(appId);
        ApplicationDTO application = applicationAppService.get(query);
        return ApplicationHttpTransformer.INSTANCE.applicationResponseFrom(application);
    }

    @GetMapping("/applications/page")
    @RequiresPermission(APPLICATION_READ)
    public PagingResult<ApplicationResponse> applications(@RequestParam(defaultValue = "1") Integer pageNo,
                                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        ApplicationPageQuery query = new ApplicationPageQuery(new Paging(pageNo, pageSize));
        PagingResult<ApplicationDTO> page = applicationAppService.page(query);
        return page.map(ApplicationHttpTransformer.INSTANCE::applicationResponseFrom);
    }

    @PostMapping("/applications/create")
    @RequiresPermission(APPLICATION_WRITE)
    @Audited(type = AuditType.SECURITY, action = "APPLICATION_REGISTER", targetType = "APPLICATION",
            targetId = "#request.appKey()", description = "注册 IAM 应用")
    public ApplicationResponse registerApplication(@RequestBody ApplicationRegisterRequest request) {
        ApplicationRegisterCmd command = ApplicationHttpTransformer.INSTANCE.applicationRegisterCmdFrom(request);
        ApplicationDTO application = applicationAppService.register(command);
        return ApplicationHttpTransformer.INSTANCE.applicationResponseFrom(application);
    }

    @PostMapping("/applications/update")
    @RequiresPermission(APPLICATION_WRITE)
    @Audited(type = AuditType.SECURITY, action = "APPLICATION_UPDATE",
            targetType = "APPLICATION", targetId = "#request.appId()",
            description = "更新 IAM 应用")
    public ApplicationResponse updateApplication(@RequestBody ApplicationUpdateRequest request) {
        ApplicationUpdateCmd command = ApplicationHttpTransformer.INSTANCE.applicationUpdateCmdFrom(request);
        ApplicationDTO application = applicationAppService.update(command);
        return ApplicationHttpTransformer.INSTANCE.applicationResponseFrom(application);
    }
}
