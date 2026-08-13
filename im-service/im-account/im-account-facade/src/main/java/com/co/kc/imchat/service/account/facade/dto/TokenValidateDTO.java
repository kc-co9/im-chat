package com.co.kc.imchat.service.account.facade.dto;

import java.io.Serializable;

public record TokenValidateDTO(boolean valid, Long userId) implements Serializable {
}
