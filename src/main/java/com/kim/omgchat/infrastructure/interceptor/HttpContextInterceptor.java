package com.kim.omgchat.infrastructure.interceptor;

import com.kim.omgchat.application.UserAppService;
import com.kim.omgchat.support.context.UserContext;
import com.kim.omgchat.support.context.UserContextUtils;
import com.kim.omgchat.model.cqrs.dto.user.TokenDTO;
import com.kim.omgchat.model.cqrs.dto.user.UserDetailDTO;
import com.kim.omgchat.model.cqrs.query.user.UserAuthQuery;
import com.kim.omgchat.model.cqrs.query.user.UserDetailQuery;
import com.kim.omgchat.support.user.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
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
public class HttpContextInterceptor extends HandlerInterceptorAdapter {

    private final TokenService tokenService;
    private final UserAppService userAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            return false;
        }

        TokenDTO tokenDTO = tokenService.parse(token);

        boolean isAuthenticated = userAppService.isAuthenticated(new UserAuthQuery(tokenDTO.getUserId()));
        if (!isAuthenticated) {
            return false;
        }

        UserDetailDTO userDetailDTO = userAppService.userDetail(new UserDetailQuery(tokenDTO.getUserId()));

        //存储线程变量
        UserContext webUser = new UserContext();
        webUser.setUserId(userDetailDTO.getUserId());
        webUser.setEmail(userDetailDTO.getEmail());
        webUser.setUsername(userDetailDTO.getUsername());

        UserContextUtils.set(webUser);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
        //删除线程变量
        UserContextUtils.remove();
    }
}
