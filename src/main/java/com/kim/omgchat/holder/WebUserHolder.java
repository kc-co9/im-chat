package com.kim.omgchat.holder;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/13 15:46
 */
public class WebUserHolder {
    private static ThreadLocal<WebUser> local = new ThreadLocal<>();

    public static void set(WebUser webUser) {
        local.set(webUser);
    }

    public static WebUser get() {
        return local.get();
    }

    public static void remove() {
        local.remove();
    }
}
