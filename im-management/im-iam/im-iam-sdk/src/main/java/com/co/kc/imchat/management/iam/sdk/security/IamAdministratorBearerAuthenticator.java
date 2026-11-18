package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionException;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Set;

/**
 * 管理员 Bearer 的实时信任边界，不读取 BFF 缓存或使用故障降级。
 */
public class IamAdministratorBearerAuthenticator {
    /** IAM Token introspection 客户端。 */
    private final IamIntrospectionClient client;
    /** 允许访问当前管理入口的 audience。 */
    private final String audience;
    /** 允许使用管理员 Bearer 的 Client ID 集合。 */
    private final Set<String> clientIds;
    /** 当前入口允许的 scope 集合。 */
    private final Set<String> scopes;

    /** 创建管理员 Bearer 实时校验器，并固定允许的 audience、Client 和 scope。 */
    public IamAdministratorBearerAuthenticator(
            IamIntrospectionClient client,
            String audience,
            Set<String> clientIds,
            Set<String> scopes
    ) {
        if (audience == null || audience.isBlank() || clientIds == null || scopes == null) {
            throw new IllegalArgumentException("Administrator Bearer trust configuration is required");
        }
        this.client = client;
        this.audience = audience;
        this.clientIds = Set.copyOf(clientIds);
        this.scopes = Set.copyOf(scopes);
    }

    /**
     * 每次调用实时验证 IAM 响应；认证 details 保留可信 Client 与 scope 供授权及审计使用。
     */
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        // 步骤一：向 IAM 实时查询 Token，不使用本地缓存或故障降级。
        IamIntrospectionResult result;
        try {
            result = client.introspect(token);
        } catch (IamIntrospectionException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Administrator Bearer verification failed");
        }
        // 步骤二：同时校验主体类型、受众、Client、权限和有效期。
        if (result == null || !result.active() || !"ADMINISTRATOR".equals(result.principalType())
                || !result.audiences().contains(audience) || !clientIds.contains(result.clientId())
                || result.administratorId() == null || result.administratorId() <= 0
                || result.expiresAt() == null || !result.expiresAt().isAfter(Instant.now())
                || result.scopes().isEmpty() || !scopes.containsAll(result.scopes())) {
            throw new BadCredentialsException("Administrator Bearer identity is invalid");
        }
        // 步骤三：把可信 IAM 结果放入认证主体和 details，供后续授权、审计复用。
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(result.principal(), "N/A",
                        result.authorities().stream().map(SimpleGrantedAuthority::new).toList());
        authentication.setDetails(result);
        return authentication;
    }
}
