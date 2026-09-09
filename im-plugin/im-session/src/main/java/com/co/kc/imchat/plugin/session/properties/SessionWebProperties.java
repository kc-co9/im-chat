package com.co.kc.imchat.plugin.session.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Session Servlet 请求适配配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "im.session.web")
public class SessionWebProperties {
    /* 无需建立用户上下文的业务公开路径。 */
    private List<String> publicPaths = new ArrayList<>();
}
