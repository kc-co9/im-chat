package com.co.kc.imchat.management.iam.support.lock;

/** IAM 分布式锁场景。 */
public final class IamLockScene {
    public static final String SUPER_ADMIN_WRITE = "im:iam:super-admin:write";
    public static final String APPLICATION_ADMINISTRATOR_ROLE_WRITE =
            "im:iam:application-administrator-role:write";

    private IamLockScene() {
    }
}
