package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;
import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.aop.support.AopUtils;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 对声明式审计用例记录最终成功或失败事实。 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class AuditedAspect {
    private static final String CONTEXT_STAGE = "context";
    private static final String EVENT_STAGE = "event";
    private static final String SUBMIT_STAGE = "submit";

    private final AuditClient auditClient;
    private final AuditContextCollector contextCollector;
    private final AuditEventFactory eventFactory;
    private final AuditFailureReporter failureReporter;

    /**
     * 执行原用例并隔离审计旁路故障。
     *
     * @param joinPoint 原业务调用
     * @param audited 审计声明
     * @return 原业务结果
     * @throws Throwable 原业务异常
     */
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditContext context = context();
        try {
            Object result = joinPoint.proceed();
            submitSuccess(joinPoint, audited, context, result);
            return result;
        } catch (Throwable failure) {
            submitFailure(joinPoint, audited, context, failure);
            throw failure;
        }
    }

    private AuditContext context() {
        try {
            return contextCollector.collect();
        } catch (RuntimeException failure) {
            failureReporter.report(CONTEXT_STAGE, failure);
            return null;
        }
    }

    private void submitSuccess(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            AuditContext context,
            Object result
    ) {
        AuditEvent event = event(
                joinPoint,
                audited,
                context,
                AuditOutcome.SUCCESS,
                null,
                result);
        if (event == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(event);
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submit(event);
                        }
                    });
        } catch (RuntimeException failure) {
            failureReporter.report(SUBMIT_STAGE, failure);
        }
    }

    private void submitFailure(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            AuditContext context,
            Throwable failure
    ) {
        AuditEvent event = event(
                joinPoint,
                audited,
                context,
                AuditOutcome.FAILURE,
                errorCode(failure),
                null);
        if (event != null) {
            submit(event);
        }
    }

    private AuditEvent event(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            AuditContext context,
            AuditOutcome outcome,
            String errorCode,
            Object result
    ) {
        if (context == null) {
            return null;
        }
        try {
            return eventFactory.create(
                    audited.type(),
                    audited.action(),
                    new AuditTarget(audited.targetType(), targetId(joinPoint, audited.targetId())),
                    outcome,
                    errorCode,
                    new AuditDescription(audited.description()),
                    attributes(joinPoint, result),
                    context);
        } catch (RuntimeException eventFailure) {
            failureReporter.report(EVENT_STAGE, eventFailure);
            return null;
        }
    }

    private String errorCode(Throwable failure) {
        if (failure instanceof com.co.kc.imchat.common.exception.BaseException baseException) {
            return String.valueOf(baseException.getCode());
        }
        return "INTERNAL_ERROR";
    }

    private AuditAttributes attributes(
            ProceedingJoinPoint joinPoint,
            Object result
    ) {
        try {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            collectArguments(joinPoint, values);
            collectResult(result, values);
            return new AuditAttributes(values);
        } catch (RuntimeException attributeFailure) {
            failureReporter.report("attributes", attributeFailure);
            return new AuditAttributes(Map.of());
        }
    }

    private void collectArguments(
            ProceedingJoinPoint joinPoint,
            Map<String, String> values
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                joinPoint.getTarget().getClass());
        Parameter[] parameters = method.getParameters();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < parameters.length; index++) {
            AuditAttribute attribute = parameters[index].getAnnotation(AuditAttribute.class);
            if (attribute == null || !attribute.include() || arguments[index] == null) {
                continue;
            }
            collect(parameters[index].getName(), arguments[index], values);
        }
    }

    private void collectResult(Object result, Map<String, String> values) {
        if (result != null) {
            collect("result", result, values);
        }
    }

    private void collect(
            String name,
            Object value,
            Map<String, String> values
    ) {
        if (simple(value.getClass())) {
            values.put(name, String.valueOf(value));
            return;
        }
        if (value.getClass().isRecord()) {
            collectRecord(value, values);
            return;
        }
        collectFields(value, values);
    }

    private void collectRecord(Object value, Map<String, String> values) {
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            AuditAttribute attribute = component.getAnnotation(AuditAttribute.class);
            if (attribute != null && !attribute.include()) {
                continue;
            }
            try {
                put(values, component.getName(), component.getAccessor().invoke(value));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot read audit record component: " + component.getName(),
                        failure);
            }
        }
    }

    private void collectFields(Object value, Map<String, String> values) {
        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                AuditAttribute attribute = field.getAnnotation(AuditAttribute.class);
                if (Modifier.isStatic(field.getModifiers())
                        || field.isSynthetic()
                        || attribute != null && !attribute.include()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible()) {
                        throw new IllegalStateException(
                                "Cannot access audit field: " + field.getName());
                    }
                    put(values, field.getName(), field.get(value));
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException(
                            "Cannot read audit field: " + field.getName(),
                            failure);
                }
            }
            type = type.getSuperclass();
        }
    }

    private void put(Map<String, String> values, String name, Object value) {
        if (value != null) {
            values.put(name, String.valueOf(value));
        }
    }

    private boolean simple(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type)
                || TemporalAccessor.class.isAssignableFrom(type)
                || TemporalAmount.class.isAssignableFrom(type)
                || UUID.class == type;
    }

    private String targetId(ProceedingJoinPoint joinPoint, String expression) {
        if (expression.isBlank()) {
            return null;
        }
        StandardEvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                joinPoint.getTarget().getClass());
        Parameter[] parameters = method.getParameters();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < parameters.length; index++) {
            context.setVariable(parameters[index].getName(), arguments[index]);
        }
        Object value = new SpelExpressionParser()
                .parseExpression(expression)
                .getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    private void submit(AuditEvent event) {
        try {
            auditClient.submit(event);
        } catch (RuntimeException failure) {
            failureReporter.report(SUBMIT_STAGE, failure);
        }
    }
}
