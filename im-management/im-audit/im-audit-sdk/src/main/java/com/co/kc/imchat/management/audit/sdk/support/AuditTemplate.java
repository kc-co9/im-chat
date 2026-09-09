package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditSubmission;
import lombok.RequiredArgsConstructor;
import java.util.function.UnaryOperator;

/** 统一收集上下文、构造并提交审计事件，同时隔离审计旁路故障。 */
@RequiredArgsConstructor
public class AuditTemplate {
    private static final String SUBMISSION_STAGE = "submission";

    private final AuditClient auditClient;
    private final AuditContextCollector contextCollector;
    private final AuditEventFactory eventFactory;
    private final AuditFailureReporter failureReporter;

    /** 使用当前调用上下文提交一次显式审计事件。 */
    public void submit(AuditSubmission submission) {
        AssertUtils.argNotNull("audit submission must not be null", submission);
        try {
            submit(submission, contextCollector.collect());
        } catch (RuntimeException failure) {
            failureReporter.report(SUBMISSION_STAGE, failure);
        }
    }

    /** 使用上下文变换提交事件，供需要补充可信操作者信息的适配器使用。 */
    public void submit(AuditSubmission submission, UnaryOperator<AuditContext> contextCustomizer) {
        AssertUtils.argNotNull("audit submission must not be null", submission);
        AssertUtils.argNotNull("audit context customizer must not be null", contextCustomizer);
        try {
            submit(submission, contextCustomizer.apply(contextCollector.collect()));
        } catch (RuntimeException failure) {
            failureReporter.report(SUBMISSION_STAGE, failure);
        }
    }

    /** 使用指定上下文提交事件，供需要补充可信操作者信息的适配器使用。 */
    public void submit(
            AuditSubmission submission,
            AuditContext context
    ) {
        AssertUtils.argNotNull("audit submission must not be null", submission);
        AssertUtils.argNotNull("audit context must not be null", context);
        try {
            AuditEvent event = eventFactory.create(
                    submission.type(),
                    submission.action(),
                    submission.target(),
                    submission.outcome(),
                    submission.errorCode(),
                    submission.description(),
                    submission.attributes(),
                    context);
            auditClient.submit(event);
        } catch (RuntimeException failure) {
            failureReporter.report(SUBMISSION_STAGE, failure);
        }
    }
}
