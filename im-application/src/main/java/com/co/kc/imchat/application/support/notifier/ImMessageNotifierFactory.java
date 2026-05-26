package com.co.kc.imchat.application.support.notifier;

import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.ReflectUtils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.support.AopUtils;

public class ImMessageNotifierFactory {

    private final Map<Class<?>, ImMessageNotifier<?>> notificationNotifiers = new HashMap<>();
    private final EnumMap<ReceiptType, ImMessageNotifier<?>> receiptNotifiers = new EnumMap<>(ReceiptType.class);

    public ImMessageNotifierFactory(List<ImMessageNotifier<?>> notifiers) {
        for (ImMessageNotifier<?> notifier : notifiers) {
            registerNotification(notifier);
            registerReceipt(notifier);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ImMessageNotifier<T> getNotifier(T notification) {
        if (notification == null) {
            throw new IllegalArgumentException("不支持空通知");
        }
        ImMessageNotifier<?> notifier = notificationNotifiers.get(notification.getClass());
        if (notifier == null) {
            throw new IllegalArgumentException("不支持通知：" + notification.getClass().getName());
        }
        return (ImMessageNotifier<T>) notifier;
    }

    @SuppressWarnings("unchecked")
    public <T> ImMessageNotifier<T> getNotifier(ReceiptType receiptType) {
        return (ImMessageNotifier<T>) findNotifier(receiptType);
    }

    public Class<?> getNotificationType(ReceiptType receiptType) {
        return resolveNotificationType(findNotifier(receiptType));
    }

    private ImMessageNotifier<?> findNotifier(ReceiptType receiptType) {
        if (receiptType == null) {
            throw new IllegalArgumentException("不支持空通知回执类型");
        }
        ImMessageNotifier<?> notifier = receiptNotifiers.get(receiptType);
        if (notifier == null) {
            throw new IllegalArgumentException("不支持通知回执类型：" + receiptType);
        }
        return notifier;
    }

    private void registerNotification(ImMessageNotifier<?> notifier) {
        notificationNotifiers.put(resolveNotificationType(notifier), notifier);
    }

    private void registerReceipt(ImMessageNotifier<?> notifier) {
        if (notifier instanceof ImMessageConfirmable<?>) {
            ReceiptType receiptType = ((ImMessageConfirmable<?>) notifier).receiptType();
            receiptNotifiers.put(receiptType, notifier);
        }
    }

    private Class<?> resolveNotificationType(ImMessageNotifier<?> notifier) {
        Class<?> notifierClass = AopUtils.getTargetClass(notifier);
        Class<?> notificationType = ReflectUtils.resolveFirstGenericTypeArgument(notifierClass, ImMessageNotifier.class);
        if (notificationType == null) {
            throw new IllegalArgumentException("无法解析 ImMessageNotifier 泛型通知类型：" + notifierClass.getName());
        }
        return notificationType;
    }
}
