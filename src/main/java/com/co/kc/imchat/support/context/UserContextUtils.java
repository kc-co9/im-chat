package com.co.kc.imchat.support.context;

public class UserContextUtils {
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextUtils() {
    }

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static void remove() {
        CONTEXT.remove();
    }
}
