package com.co.kc.imchat.management.admin.interfaces.http;

import com.co.kc.imchat.management.admin.application.ManagedUserAppService;
import com.co.kc.imchat.management.admin.support.security.AdminPermission;
import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.management.admin.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.management.admin.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.management.admin.model.cqrs.query.ManagedUserPageQuery;
import com.co.kc.imchat.management.admin.model.io.UserPasswordResetRequest;
import com.co.kc.imchat.management.admin.model.io.UserStatusRequest;
import com.co.kc.imchat.management.admin.model.io.UserUpdateRequest;
import com.co.kc.imchat.management.admin.model.io.ManagedUserResponse;
import com.co.kc.imchat.management.admin.transformer.interfaces.ManagedUserHttpTransformer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ManagedUserController {
    private final ManagedUserAppService managedUserAppService;

    @GetMapping("/page")
    @RequiresPermission(AdminPermission.Code.USER_READ)
    public PagingResult<ManagedUserResponse> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status
    ) {
        Paging paging = new Paging(pageNo, pageSize);
        ManagedUserPageQuery query = new ManagedUserPageQuery(paging, userId, username, email, status);
        PagingResult<ManagedUserDTO> page = managedUserAppService.page(query);
        return page.map(ManagedUserHttpTransformer.INSTANCE::responseFrom);
    }

    @GetMapping("/detail")
    @RequiresPermission(AdminPermission.Code.USER_READ)
    public ManagedUserResponse detail(@RequestParam Long userId) {
        ManagedUserGetQuery query = new ManagedUserGetQuery(userId);
        ManagedUserDTO managedUserDTO = managedUserAppService.get(query);
        return ManagedUserHttpTransformer.INSTANCE.responseFrom(managedUserDTO);
    }

    @PostMapping("/update")
    @RequiresPermission(AdminPermission.Code.USER_UPDATE)
    public void update(@Valid @RequestBody UserUpdateRequest request) {
        ManagedUserUpdateCmd command = new ManagedUserUpdateCmd(
                request.userId(), request.username(), request.email());
        managedUserAppService.update(command);
    }

    @PostMapping("/reset-password")
    @RequiresPermission(AdminPermission.Code.USER_PASSWORD_RESET)
    public void resetPassword(@Valid @RequestBody UserPasswordResetRequest request) {
        ManagedUserPasswordResetCmd command = new ManagedUserPasswordResetCmd(
                request.userId(), request.password());
        managedUserAppService.resetPassword(command);
    }

    @PostMapping("/ban")
    @RequiresPermission(AdminPermission.Code.USER_BAN)
    public void ban(@RequestBody UserStatusRequest request) {
        ManagedUserBanCmd command = new ManagedUserBanCmd(request.userId());
        managedUserAppService.ban(command);
    }

    @PostMapping("/unban")
    @RequiresPermission(AdminPermission.Code.USER_BAN)
    public void unban(@RequestBody UserStatusRequest request) {
        ManagedUserUnbanCmd command = new ManagedUserUnbanCmd(request.userId());
        managedUserAppService.unban(command);
    }

    @PostMapping("/delete")
    @RequiresPermission(AdminPermission.Code.USER_DELETE)
    public void delete(@RequestBody UserStatusRequest request) {
        ManagedUserDeleteCmd command = new ManagedUserDeleteCmd(request.userId());
        managedUserAppService.delete(command);
    }

}
