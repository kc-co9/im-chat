package com.co.kc.imchat.plugin.bolt.model;

import java.io.Serializable;

public record BoltResponse(boolean success, String data, String code, String message) implements Serializable {

    public static BoltResponse ok(String data) {
        return new BoltResponse(true, data, "OK", "OK");
    }

    public static BoltResponse failed(String code, String message) {
        return new BoltResponse(false, null, code, message);
    }
}
