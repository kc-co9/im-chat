package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.service.message.application.notification.ImMessageNotifier;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierFactory;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NotificationBeans {

    @Bean
    public ImMessageNotifierInvoker imMessageNotifierInvoker(ImMessageNotifierFactory imMessageNotifierFactory,
                                                             ImMessageConfirmableService imMessageConfirmableService) {
        return new ImMessageNotifierInvoker(imMessageNotifierFactory, imMessageConfirmableService);
    }

    @Bean(initMethod = "init", destroyMethod = "destroy")
    public ImMessageConfirmableService imMessageConfirmableService(ImMessageNotifierFactory imMessageNotifierFactory,
                                                                   ImMessageConfirmableStore imMessageConfirmableStore) {
        return new ImMessageConfirmableService(imMessageNotifierFactory, imMessageConfirmableStore);
    }

    @Bean
    public ImMessageNotifierFactory imMessageNotifierFactory(List<ImMessageNotifier<?>> notifiers) {
        return new ImMessageNotifierFactory(notifiers);
    }
}
