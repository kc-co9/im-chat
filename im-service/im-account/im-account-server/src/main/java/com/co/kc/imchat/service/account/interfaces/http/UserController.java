package com.co.kc.imchat.service.account.interfaces.http;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.RefreshTokenCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignOutCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignUpCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.UserDetailQuery;
import com.co.kc.imchat.service.account.model.io.UserDetailResponse;
import com.co.kc.imchat.service.account.model.io.TokenPairResponse;
import com.co.kc.imchat.service.account.model.io.TokenRefreshRequest;
import com.co.kc.imchat.service.account.model.io.UserSignInRequest;
import com.co.kc.imchat.service.account.model.io.UserSignUpRequest;
import com.co.kc.imchat.service.account.transformer.application.AccountAppTransformer;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import com.co.kc.imchat.plugin.session.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean({SessionAppService.class, UserAppService.class})
@RequestMapping(value = "/user")
public class UserController {
    private final SessionAppService sessionAppService;
    private final UserAppService userAppService;

    @PostMapping(value = "/signUp")
    public void signUp(@RequestBody @Validated UserSignUpRequest request) {
        UserSignUpCmd command = new UserSignUpCmd(
                request.getEmail(), request.getUsername(), request.getPassword());
        userAppService.signUp(command);
    }

    @PostMapping(value = "/signIn")
    public TokenPairResponse signIn(@RequestBody @Validated UserSignInRequest request) {
        UserSignInCmd command = new UserSignInCmd(request.getEmail(), request.getPassword());
        SignInDTO signInDTO = sessionAppService.signIn(command);
        return AccountAppTransformer.INSTANCE.tokenPairResponseFrom(signInDTO);
    }

    @PostMapping(value = "/refreshToken")
    public TokenPairResponse refreshToken(@RequestBody @Validated TokenRefreshRequest request) {
        RefreshTokenCmd command = new RefreshTokenCmd(request.refreshToken());
        SignInDTO signInDTO = sessionAppService.refreshToken(command);
        return AccountAppTransformer.INSTANCE.tokenPairResponseFrom(signInDTO);
    }

    @PostMapping(value = "/signOut")
    public void signOut() {
        UserContext userContext = UserContextUtils.get();
        sessionAppService.signOut(new UserSignOutCmd(userContext.getUserId(), userContext.getSessionVersion()));
    }

    @GetMapping(value = "/userDetail")
    public UserDetailResponse userDetail() {
        Long userId = UserContextUtils.get().getUserId();
        UserDetailQuery query = new UserDetailQuery(userId);
        UserDetailDTO userDetailDTO = userAppService.userDetail(query);
        return new UserDetailResponse(userDetailDTO.getUserId(), userDetailDTO.getEmail(), userDetailDTO.getUsername());
    }
}
