package com.co.kc.imchat.interfaces.support.web.interceptor;

import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.interfaces.model.enums.ParamsConstants;
import com.co.kc.imchat.interfaces.support.context.UserContext;
import com.co.kc.imchat.interfaces.support.context.UserContextUtils;
import com.co.kc.imchat.application.model.cqrs.dto.user.TokenDTO;
import com.co.kc.imchat.application.model.cqrs.dto.user.UserDetailDTO;
import com.co.kc.imchat.application.model.cqrs.query.user.UserAuthQuery;
import com.co.kc.imchat.application.model.cqrs.query.user.UserDetailQuery;
import com.co.kc.imchat.application.support.auth.TokenService;
import com.co.kc.imchat.common.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.thymeleaf.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * 登录拦截器
 * </p>
 *
 * @author kim
 * @since 2019/6/13 15:55
 */
@Component
@RequiredArgsConstructor
public class HttpContextInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final UserAppService userAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        String token = request.getHeader(ParamsConstants.TOKEN);
        if (StringUtils.isEmpty(token)) {
            throw new AuthException("用户未登录");
        }

        TokenDTO tokenDTO = tokenService.parse(token);
        if (tokenDTO == null) {
            throw new AuthException("用户未登录");
        }

        boolean isAuthenticated = userAppService.isAuthenticated(new UserAuthQuery(tokenDTO.getUserId()));
        if (!isAuthenticated) {
            throw new AuthException("用户未登录");
        }

        //查询用户信息
        UserDetailDTO userDetail = userAppService.userDetail(new UserDetailQuery(tokenDTO.getUserId()));

        //存储线程变量
        UserContext context = new UserContext(userDetail.getUserId(), userDetail.getEmail(), userDetail.getUsername());
        UserContextUtils.set(context);

        return true;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) throws Exception {
        //删除线程变量
        UserContextUtils.remove();
    }
}
