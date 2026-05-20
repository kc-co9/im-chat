package com.co.kc.imchat.support.lock.aspect;

import com.co.kc.imchat.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.support.lock.LockKeys;
import com.co.kc.imchat.support.lock.template.DistributeLockTemplate;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributeLockAspect {
    private final DistributeLockTemplate distributeLockTemplate;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Pointcut("@annotation(com.co.kc.imchat.support.lock.annotation.DistributeLock)")
    public void pointCut() {
    }

    @Around("pointCut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethod(joinPoint);
        DistributeLock distributeLock = method.getAnnotation(DistributeLock.class);
        String key = parseKey(distributeLock.key(), method, joinPoint.getArgs());
        return distributeLockTemplate.execute(
                joinPoint::proceed, distributeLock.scene().getValue(), key, distributeLock.expireTime(), distributeLock.waitTime());
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (!method.getDeclaringClass().isInterface()) {
            return method;
        }
        return AopUtils.getTargetClass(joinPoint.getTarget()).getMethod(method.getName(), method.getParameterTypes());
    }

    private String parseKey(String keyExpression, Method method, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("LockKeys", LockKeys.class);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        Object value = expressionParser.parseExpression(keyExpression).getValue(context);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalArgumentException("分布式锁key不能为空");
        }
        return String.valueOf(value);
    }
}
