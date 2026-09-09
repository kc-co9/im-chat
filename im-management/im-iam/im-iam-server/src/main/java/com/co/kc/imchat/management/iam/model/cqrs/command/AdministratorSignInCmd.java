package com.co.kc.imchat.management.iam.model.cqrs.command;

/**
 * 管理员登录命令。
 */
public record AdministratorSignInCmd(String email, String password) {
}
