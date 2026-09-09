package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPermissionPageQuery;
import com.co.kc.imchat.management.iam.model.io.ApplicationPermissionResponse;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.management.iam.transformer.interfaces.PermissionHttpTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.PERMISSION_READ;

/**
 * 应用权限管理 HTTP 接口。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class ApplicationPermissionController {
    private final ApplicationPermissionAppService applicationPermissionAppService;

    @GetMapping("/permissions/page")
    @RequiresPermission(PERMISSION_READ)
    public PagingResult<ApplicationPermissionResponse> permissions(@RequestParam Long appId,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(defaultValue = "1") Integer pageNo,
                                                                   @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        ApplicationPermissionPageQuery query = new ApplicationPermissionPageQuery(
                appId,
                keyword,
                new Paging(pageNo, pageSize));
        PagingResult<ApplicationPermissionDTO> page = applicationPermissionAppService.page(query);
        return page.map(PermissionHttpTransformer.INSTANCE::applicationPermissionResponseFrom);
    }
}
