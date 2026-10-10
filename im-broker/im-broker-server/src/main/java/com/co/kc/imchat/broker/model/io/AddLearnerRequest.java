package com.co.kc.imchat.broker.model.io;

/** 正式添加 learner 的 HTTP 输入，不包含预检查模式。 */
public record AddLearnerRequest(String membershipEndpoint) {
}
