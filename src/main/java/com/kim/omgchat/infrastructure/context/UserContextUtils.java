package com.kim.omgchat.infrastructure.context;

public class UserContextUtils {
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextUtils() {
    }

    public static void set(UserContext webUser) {
        CONTEXT.set(webUser);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static void remove() {
        CONTEXT.remove();
    }
}
