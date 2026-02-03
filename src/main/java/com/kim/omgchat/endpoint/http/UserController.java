package com.kim.omgchat.endpoint.http;

import com.kim.omgchat.application.UserAppService;
import com.kim.omgchat.model.cqrs.command.user.SignInCommand;
import com.kim.omgchat.model.cqrs.command.user.SignOutCommand;
import com.kim.omgchat.model.cqrs.command.user.SignUpCommand;
import com.kim.omgchat.model.cqrs.dto.user.SignInDTO;
import com.kim.omgchat.model.cqrs.dto.user.UserDetailDTO;
import com.kim.omgchat.model.cqrs.query.user.UserDetailQuery;
import com.kim.omgchat.model.io.user.UserDetailResponse;
import com.kim.omgchat.model.io.user.UserSignInResponse;
import com.kim.omgchat.model.io.Result;
import com.kim.omgchat.model.io.user.UserSignInRequest;
import com.kim.omgchat.model.io.user.UserSignUpRequest;
import com.kim.omgchat.support.context.UserContextUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Api("用户路由")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/user")
public class UserController {
    private final UserAppService userAppService;

    @ApiOperation("注册接口")
    @PostMapping("/signUp")
    public Result<?> signUp(@RequestBody @Validated UserSignUpRequest request) {
        SignUpCommand command = new SignUpCommand(
                request.getEmail(), request.getUsername(), request.getPassword());
        userAppService.signUp(command);
        return Result.success();
    }

    @ApiOperation("登录接口")
    @PostMapping("/signIn")
    public Result<UserSignInResponse> signIn(@RequestBody @Validated UserSignInRequest request) {
        SignInCommand command = new SignInCommand(request.getEmail(), request.getPassword());
        SignInDTO signInDTO = userAppService.signIn(command);
        return Result.success(new UserSignInResponse(signInDTO.getToken()));
    }

    @ApiOperation("退出登录接口")
    @PostMapping("/signOut")
    public Result<?> signOut() {
        Long userId = UserContextUtils.get().getUserId();
        userAppService.signOut(new SignOutCommand(userId));
        return Result.success();
    }

    @ApiOperation("用户详情接口")
    @GetMapping("/userDetail")
    public Result<UserDetailResponse> userDetail() {
        Long userId = UserContextUtils.get().getUserId();
        UserDetailQuery query = new UserDetailQuery(userId);
        UserDetailDTO userDetailDTO = userAppService.userDetail(query);
        return Result.success(new UserDetailResponse(
                userDetailDTO.getUserId(), userDetailDTO.getEmail(), userDetailDTO.getUsername()));
    }
}
