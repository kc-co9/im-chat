package com.co.kc.imchat.plugin.web.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 跨域访问配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "im.web.cors")
public class WebCorsProperties {
    /* 是否启用全局 CORS 映射。 */
    private Boolean enabled = false;
    /* 允许的来源模式。 */
    private List<String> allowedOriginPatterns = new ArrayList<>();
    /* 允许的请求头。 */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    /* 允许的 HTTP 方法。 */
    private List<String> allowedMethods = new ArrayList<>(List.of("*"));
    /* 是否允许浏览器携带凭证。 */
    private Boolean allowCredentials = true;
    /* 预检结果缓存时间。 */
    private Duration maxAge = Duration.ofHours(1);
}
