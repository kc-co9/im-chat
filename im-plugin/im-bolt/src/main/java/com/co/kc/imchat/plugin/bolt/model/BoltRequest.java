package com.co.kc.imchat.plugin.bolt.model;

import java.io.Serializable;

public record BoltRequest(String service, String operation, String payload) implements Serializable {
}
