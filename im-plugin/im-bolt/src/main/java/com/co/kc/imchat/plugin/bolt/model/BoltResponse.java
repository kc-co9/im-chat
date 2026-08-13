package com.co.kc.imchat.plugin.bolt.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * SOFA Bolt uses Hessian by default here. Keep this DTO as a JavaBean instead of a record because
 * Hessian cannot reliably serialize Java record final fields in the current runtime.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoltResponse implements Serializable {
    private boolean success;
    private String data;
    private String code;
    private String message;

    public static BoltResponse ok(String data) {
        return new BoltResponse(true, data, "OK", "OK");
    }

    public static BoltResponse failed(String code, String message) {
        return new BoltResponse(false, null, code, message);
    }

}
