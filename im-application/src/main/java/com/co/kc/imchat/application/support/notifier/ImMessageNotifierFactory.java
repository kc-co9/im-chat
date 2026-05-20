package com.co.kc.imchat.application.support.notifier;

import com.co.kc.imchat.application.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.common.utils.ReflectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Component
public class ImMessageNotifierFactory {

    private final Map<Class<?>, ImMessageNotifier<?>> commandNotifierMap = new HashMap<>();
    private final Map<NotifierTaskType, ImMessageNotifier<?>> typeNotifierMapping = new HashMap<>();

    public ImMessageNotifierFactory(List<ImMessageNotifier<?>> notifiers) {
        for (ImMessageNotifier<?> notifier : notifiers) {
            registerCommand(notifier);
            registerTask(notifier);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ImMessageNotifier<T> getNotifier(T command) {
        if (command == null) {
            throw new IllegalArgumentException("Unsupported IM notify command: null");
        }
        ImMessageNotifier<?> notifier = commandNotifierMap.get(command.getClass());
        if (notifier == null) {
            throw new IllegalArgumentException("Unsupported IM notify command: " + command.getClass().getName());
        }
        return (ImMessageNotifier<T>) notifier;
    }

    @SuppressWarnings("unchecked")
    public <T> ImMessageNotifier<T> getNotifier(NotifierTaskType taskType) {
        return (ImMessageNotifier<T>) findNotifier(taskType);
    }

    public Class<?> getCommandType(NotifierTaskType taskType) {
        return resolveCommandType(findNotifier(taskType));
    }

    private ImMessageNotifier<?> findNotifier(NotifierTaskType taskType) {
        if (taskType == null) {
            throw new IllegalArgumentException("Unsupported IM notify taskType: null");
        }
        ImMessageNotifier<?> notifier = typeNotifierMapping.get(taskType);
        if (notifier == null) {
            throw new IllegalArgumentException("Unsupported IM notify command: " + taskType);
        }
        return notifier;
    }

    private void registerCommand(ImMessageNotifier<?> notifier) {
        commandNotifierMap.put(resolveCommandType(notifier), notifier);
    }

    private void registerTask(ImMessageNotifier<?> notifier) {
        if (notifier instanceof ImMessageConfirmable) {
            NotifierTaskType taskType = ((ImMessageConfirmable) notifier).task();
            typeNotifierMapping.put(taskType, notifier);
        }
    }

    private Class<?> resolveCommandType(ImMessageNotifier<?> notifier) {
        Class<?> notifierClass = AopUtils.getTargetClass(notifier);
        Class<?> commandType = ReflectUtils.resolveFirstGenericTypeArgument(notifierClass, ImMessageNotifier.class);
        if (commandType == null) {
            throw new IllegalArgumentException("Cannot resolve ImMessageNotifier generic command type: "
                    + notifierClass.getName());
        }
        return commandType;
    }
}
