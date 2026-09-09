package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.ApplicationRoleAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleAssignmentChangeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleCreateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationRoleDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationRolePageQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationRoleAssignmentQuery;
import com.co.kc.imchat.management.iam.model.io.ApplicationRoleResponse;
import com.co.kc.imchat.management.iam.model.io.RoleCreateRequest;
import com.co.kc.imchat.management.iam.model.io.RoleUpdateRequest;
import com.co.kc.imchat.management.iam.model.io.ApplicationRoleAssignmentRequest;
import com.co.kc.imchat.management.iam.model.io.RoleAssignmentResponse;
import com.co.kc.imchat.management.iam.transformer.interfaces.RoleHttpTransformer;
import com.co.kc.imchat.management.iam.support.security.RequiresPermission;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ROLE_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ROLE_WRITE;

/**
 * 应用角色管理 HTTP 接口。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class ApplicationRoleController {
    private final ApplicationRoleAppService roleAppService;

    @GetMapping("/roles/page")
    @RequiresPermission(ROLE_READ)
    public PagingResult<ApplicationRoleResponse> roles(
            @RequestParam Long appId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        ApplicationRolePageQuery query = new ApplicationRolePageQuery(
                appId,
                new Paging(pageNo, pageSize));
        PagingResult<ApplicationRoleDTO> page = roleAppService.page(query);
        return page.map(RoleHttpTransformer.INSTANCE::applicationRoleResponseFrom);
    }

    @GetMapping("/roles/assignments")
    @RequiresPermission(ROLE_READ)
    public RoleAssignmentResponse assignments(
            @RequestParam Long appId,
            @RequestParam Long administratorId
    ) {
        return new RoleAssignmentResponse(roleAppService.getAdministratorRoles(
                new ApplicationRoleAssignmentQuery(appId, administratorId)).roleIds());
    }

    @PostMapping("/roles/assignments")
    @RequiresPermission(ROLE_WRITE)
    @Audited(type = AuditType.SECURITY, action = "APPLICATION_ROLE_ASSIGNMENT_CHANGE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "调整管理员应用角色")
    public void replaceAdministratorRoles(@RequestBody ApplicationRoleAssignmentRequest request) {
        roleAppService.changeAdministratorRoles(new ApplicationRoleAssignmentChangeCmd(
                request.appId(),
                request.administratorId(),
                request.roleIds()));
    }

    @PostMapping("/roles/create")
    @RequiresPermission(ROLE_WRITE)
    @Audited(
            type = AuditType.SECURITY,
            action = "ROLE_CREATE",
            targetType = "ROLE",
            targetId = "#request.code()",
            description = "创建 IAM 角色"
    )
    public void createRole(@RequestBody RoleCreateRequest request) {
        ApplicationRoleCreateCmd command =
                RoleHttpTransformer.INSTANCE.applicationRoleCreateCmdFrom(request);
        roleAppService.create(command);
    }

    @PostMapping("/roles/update")
    @RequiresPermission(ROLE_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ROLE_UPDATE",
            targetType = "ROLE", targetId = "#request.roleId()",
            description = "更新 IAM 应用角色")
    public void updateRole(@RequestBody RoleUpdateRequest request) {
        ApplicationRoleUpdateCmd command =
                RoleHttpTransformer.INSTANCE.applicationRoleUpdateCmdFrom(request);
        roleAppService.update(command);
    }
}
