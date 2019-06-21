package com.kim.omgchat.interceptor;

import com.kim.omgchat.constant.RedisKeyConstant;
import com.kim.omgchat.domain.UserDO;
import com.kim.omgchat.holder.WebUser;
import com.kim.omgchat.holder.WebUserHolder;
import com.kim.omgchat.service.UserService;
import com.kim.omgchat.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public class LoginInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            return false;
        }

        String tokenKey = RedisKeyConstant.generateUserTokenKey(token);
        String json = redisTemplate.opsForValue().get(tokenKey);
        UserDO userDO = JsonUtil.json2Obj(json, UserDO.class);
        if (userDO == null) {
            return false;
        }

        //存储线程变量
        WebUser webUser = new WebUser();
        webUser.setUserId(userDO.getId());
        webUser.setEmail(userDO.getEmail());
        webUser.setNickname(userDO.getNickname());

        WebUserHolder.set(webUser);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
        //删除线程变量
        WebUserHolder.remove();
    }
}
