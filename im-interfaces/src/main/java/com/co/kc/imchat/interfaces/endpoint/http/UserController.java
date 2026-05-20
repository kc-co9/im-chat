package com.co.kc.imchat.interfaces.endpoint.http;

import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignInCmd;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignOutCmd;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignUpCmd;
import com.co.kc.imchat.application.model.cqrs.dto.user.SignInDTO;
import com.co.kc.imchat.application.model.cqrs.dto.user.UserDetailDTO;
import com.co.kc.imchat.application.model.cqrs.query.user.UserDetailQuery;
import com.co.kc.imchat.interfaces.model.io.user.UserDetailResponse;
import com.co.kc.imchat.interfaces.model.io.user.UserSignInResponse;
import com.co.kc.imchat.interfaces.model.io.user.UserSignInRequest;
import com.co.kc.imchat.interfaces.model.io.user.UserSignUpRequest;
import com.co.kc.imchat.interfaces.support.context.UserContextUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户路由")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/user")
public class UserController {
    private final UserAppService userAppService;

    @Operation(summary = "注册接口")
    @PostMapping(value = "/signUp")
    public void signUp(@RequestBody @Validated UserSignUpRequest request) {
        UserSignUpCmd command = new UserSignUpCmd(
                request.getEmail(), request.getUsername(), request.getPassword());
        userAppService.signUp(command);
    }

    @Operation(summary = "登录接口")
    @PostMapping(value = "/signIn")
    public UserSignInResponse signIn(@RequestBody @Validated UserSignInRequest request) {
        UserSignInCmd command = new UserSignInCmd(request.getEmail(), request.getPassword());
        SignInDTO signInDTO = userAppService.signIn(command);
        return new UserSignInResponse(signInDTO.getUserId(), signInDTO.getToken());
    }

    @Operation(summary = "退出登录接口")
    @PostMapping(value = "/signOut")
    public void signOut() {
        Long userId = UserContextUtils.get().getUserId();
        userAppService.signOut(new UserSignOutCmd(userId));
    }

    @Operation(summary = "用户详情接口")
    @GetMapping(value = "/userDetail")
    public UserDetailResponse userDetail() {
        Long userId = UserContextUtils.get().getUserId();
        UserDetailQuery query = new UserDetailQuery(userId);
        UserDetailDTO userDetailDTO = userAppService.userDetail(query);
        return new UserDetailResponse(userDetailDTO.getUserId(), userDetailDTO.getEmail(), userDetailDTO.getUsername());
    }
}
