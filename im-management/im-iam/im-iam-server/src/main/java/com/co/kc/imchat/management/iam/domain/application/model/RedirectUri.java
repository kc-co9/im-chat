package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.net.URI;

/** OAuth2 登录回调或退出后跳转地址。 */
public record RedirectUri(URI value) {
    public RedirectUri {
        AssertUtils.domainPropNotNull("redirect URI must not be null", value);
        AssertUtils.domainPropTrue("redirect URI must be absolute", value.isAbsolute());
        AssertUtils.domainPropTrue(
                "redirect URI scheme must be HTTP or HTTPS",
                "https".equalsIgnoreCase(value.getScheme())
                        || "http".equalsIgnoreCase(value.getScheme()));
        AssertUtils.domainPropTrue("redirect URI must not contain a fragment", value.getFragment() == null);
    }

    public String stringValue() {
        return value.toString();
    }
}
