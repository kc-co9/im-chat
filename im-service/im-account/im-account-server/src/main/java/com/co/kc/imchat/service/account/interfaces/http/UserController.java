package com.co.kc.imchat.service.account.interfaces.http;

import com.co.kc.imchat.service.account.application.AccountAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignOutCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignUpCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.UserDetailQuery;
import com.co.kc.imchat.service.account.model.io.UserDetailResponse;
import com.co.kc.imchat.service.account.model.io.UserSignInResponse;
import com.co.kc.imchat.service.account.model.io.UserSignInRequest;
import com.co.kc.imchat.service.account.model.io.UserSignUpRequest;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean({AccountAppService.class, UserAppService.class})
@RequestMapping(value = "/user")
public class UserController {
    private final AccountAppService accountAppService;
    private final UserAppService userAppService;

    @PostMapping(value = "/signUp")
    public void signUp(@RequestBody @Validated UserSignUpRequest request) {
        UserSignUpCmd command = new UserSignUpCmd(
                request.getEmail(), request.getUsername(), request.getPassword());
        userAppService.signUp(command);
    }

    @PostMapping(value = "/signIn")
    public UserSignInResponse signIn(@RequestBody @Validated UserSignInRequest request) {
        UserSignInCmd command = new UserSignInCmd(request.getEmail(), request.getPassword());
        SignInDTO signInDTO = accountAppService.signIn(command);
        return new UserSignInResponse(signInDTO.getUserId(), signInDTO.getToken());
    }

    @PostMapping(value = "/signOut")
    public void signOut() {
        Long userId = UserContextUtils.get().getUserId();
        accountAppService.signOut(new UserSignOutCmd(userId));
    }

    @GetMapping(value = "/userDetail")
    public UserDetailResponse userDetail() {
        Long userId = UserContextUtils.get().getUserId();
        UserDetailQuery query = new UserDetailQuery(userId);
        UserDetailDTO userDetailDTO = userAppService.userDetail(query);
        return new UserDetailResponse(userDetailDTO.getUserId(), userDetailDTO.getEmail(), userDetailDTO.getUsername());
    }
}
