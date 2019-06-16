package com.kim.omgchat.holder;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/13 15:47
 */
public class WebTokenHolder {
    private static ThreadLocal<WebToken> local = new ThreadLocal<>();

    public static void set(WebToken webToken) {
        local.set(webToken);
    }

    public static void get() {
        local.get();
    }

    public static void remove() {
        local.remove();
    }
}
