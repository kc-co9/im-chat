package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.AdministratorAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDeleteCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorEnableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorPasswordResetCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorRoleChangeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSessionsRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AdministratorDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.RoleAssignmentDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.IamRoleDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.AdministratorPageQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.AdministratorRoleAssignmentQuery;
import com.co.kc.imchat.management.iam.model.io.IamAdministratorResponse;
import com.co.kc.imchat.management.iam.model.io.AdministratorPasswordResetRequest;
import com.co.kc.imchat.management.iam.model.io.AdministratorRoleChangeRequest;
import com.co.kc.imchat.management.iam.model.io.AdministratorIdRequest;
import com.co.kc.imchat.management.iam.model.io.RoleAssignmentResponse;
import com.co.kc.imchat.management.iam.model.io.IamRoleResponse;
import com.co.kc.imchat.management.iam.transformer.interfaces.AdministratorHttpTransformer;
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

import java.util.List;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ADMINISTRATOR_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ADMINISTRATOR_WRITE;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ROLE_WRITE;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ROLE_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.SESSION_REVOKE;

/**
 * IAM 管理员及管理员会话 HTTP 接口。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class AdministratorController {
    private final AdministratorAppService administratorAppService;

    @GetMapping("/administrators/page")
    @RequiresPermission(ADMINISTRATOR_READ)
    public PagingResult<IamAdministratorResponse> administrators(@RequestParam(defaultValue = "1") Integer pageNo,
                                                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        AdministratorPageQuery query = new AdministratorPageQuery(new Paging(pageNo, pageSize));
        PagingResult<AdministratorDTO> page = administratorAppService.page(query);
        return page.map(AdministratorHttpTransformer.INSTANCE::responseFrom);
    }

    @GetMapping("/internal-roles/list")
    @RequiresPermission(ROLE_READ)
    public List<IamRoleResponse> iamRoles() {
        return administratorAppService.listIamRoles().stream()
                .map(role -> new IamRoleResponse(
                        role.id(),
                        role.code(),
                        role.name(),
                        role.type(),
                        role.status(),
                        role.permissions()))
                .toList();
    }

    @GetMapping("/administrators/internal-roles")
    @RequiresPermission(ROLE_READ)
    public RoleAssignmentResponse administratorRoles(@RequestParam Long administratorId) {
        RoleAssignmentDTO assignment = administratorAppService.getIamRoles(
                new AdministratorRoleAssignmentQuery(administratorId));
        return new RoleAssignmentResponse(assignment.roleIds());
    }

    @PostMapping("/administrators/disable")
    @RequiresPermission(ADMINISTRATOR_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_DISABLE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "停用 IAM 管理员")
    public void disableAdministrator(@RequestBody AdministratorIdRequest request) {
        AdministratorDisableCmd command = new AdministratorDisableCmd(request.administratorId());
        administratorAppService.disable(command);
    }

    @PostMapping("/administrators/enable")
    @RequiresPermission(ADMINISTRATOR_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_ENABLE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "启用 IAM 管理员")
    public void enableAdministrator(@RequestBody AdministratorIdRequest request) {
        AdministratorEnableCmd command = new AdministratorEnableCmd(request.administratorId());
        administratorAppService.enable(command);
    }

    @PostMapping("/administrators/delete")
    @RequiresPermission(ADMINISTRATOR_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_DELETE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "删除 IAM 管理员")
    public void deleteAdministrator(@RequestBody AdministratorIdRequest request) {
        AdministratorDeleteCmd command = new AdministratorDeleteCmd(request.administratorId());
        administratorAppService.delete(command);
    }

    @PostMapping("/administrators/reset-password")
    @RequiresPermission(ADMINISTRATOR_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_PASSWORD_RESET",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "重置 IAM 管理员密码")
    public void resetAdministratorPassword(@RequestBody AdministratorPasswordResetRequest request) {
        AdministratorPasswordResetCmd command = new AdministratorPasswordResetCmd(request.administratorId(), request.password());
        administratorAppService.resetPassword(command);
    }

    @PostMapping("/administrators/revoke-sessions")
    @RequiresPermission(SESSION_REVOKE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_SESSIONS_REVOKE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "撤销 IAM 管理员全部 OAuth 会话")
    public void revokeAdministratorSessions(@RequestBody AdministratorIdRequest request) {
        AdministratorSessionsRevokeCmd command =
                new AdministratorSessionsRevokeCmd(request.administratorId());
        administratorAppService.revokeSessions(command);
    }

    @PostMapping("/administrators/roles")
    @RequiresPermission(ROLE_WRITE)
    @Audited(type = AuditType.SECURITY, action = "ADMINISTRATOR_ROLE_CHANGE",
            targetType = "ADMINISTRATOR", targetId = "#request.administratorId()",
            description = "调整 IAM 管理员角色")
    public void changeAdministratorIamRoles(@RequestBody AdministratorRoleChangeRequest request) {
        AdministratorRoleChangeCmd command = new AdministratorRoleChangeCmd(request.administratorId(), request.roleIds());
        administratorAppService.changeIamRoles(command);
    }
}
